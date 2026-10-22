// Run: node --test scripts/seed/seed.test.mjs
// Checks the seed against an in-memory stand-in for the gateway: counts, idempotency, poster files and credits.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { buildCatalog } from './data.mjs';
import { posterFor } from './posters.mjs';

/** A local-poster folder for a test: empty, or holding a stand-in file for every local-only poster. */
function localDir(withFiles) {
	const dir = mkdtempSync(join(tmpdir(), 'seed-local-'));
	if (withFiles) for (const m of buildCatalog().movies.filter((x) => x.localPoster)) writeFileSync(join(dir, m.poster), 'local poster');
	return pathToFileURL(dir + '/');
}
const FILMS = buildCatalog().movies.length;
const COMMITTED = buildCatalog().movies.filter((m) => !m.localPoster).length;
import { createClient, seed } from './seed.mjs';

function fakeGateway() {
	const state = { people: [], movies: [], credits: [], uploads: [], nextId: 1 };
	const id = () => `00000000-0000-4000-8000-${String(state.nextId++).padStart(12, '0')}`;
	const fail = (code, messageKey) => ({ errors: [{ message: 'x', extensions: { code, messageKey } }] });

	function handle(query, variables) {
		if (query.includes('createPerson')) {
			const person = { id: id(), ...variables.input };
			state.people.push(person);
			return { data: { createPerson: { id: person.id } } };
		}
		if (query.includes('people(')) {
			const q = variables.q.toLowerCase();
			return { data: { people: { items: state.people.filter((p) => p.name.toLowerCase().includes(q)).slice(0, 100) } } };
		}
		if (query.includes('createMovie')) {
			const { title, releaseYear } = variables.input;
			if (state.movies.some((m) => m.title.toLowerCase() === title.toLowerCase() && m.releaseYear === releaseYear)) {
				return fail('CONFLICT', 'movie.duplicate');
			}
			const movie = { id: id(), artwork: null, ...variables.input };
			state.movies.push(movie);
			return { data: { createMovie: { id: movie.id } } };
		}
		if (query.includes('movies(')) {
			const items = state.movies.filter((m) => m.releaseYear === variables.y);
			return { data: { movies: { items } } };
		}
		if (query.includes('addCredit')) {
			const { personId, role } = variables.input;
			if (state.credits.some((c) => c.movieId === variables.movieId && c.personId === personId && c.role === role)) {
				return fail('CONFLICT', 'credit.duplicate');
			}
			state.credits.push({ movieId: variables.movieId, ...variables.input });
			return { data: { addCredit: { id: id() } } };
		}
		throw new Error(`unexpected operation: ${query.slice(0, 40)}`);
	}

	const server = http.createServer(async (req, res) => {
		const chunks = [];
		for await (const c of req) chunks.push(c);
		const body = Buffer.concat(chunks);
		let result;
		if ((req.headers['content-type'] ?? '').startsWith('multipart/form-data')) {
			if (req.headers['apollo-require-preflight'] !== 'true') {
				res.writeHead(403).end();
				return;
			}
			const form = await new Response(body, { headers: { 'content-type': req.headers['content-type'] } }).formData();
			const operations = JSON.parse(form.get('operations'));
			assert.deepEqual(JSON.parse(form.get('map')), { 0: ['variables.file'] });
			const file = form.get('0');
			const bytes = Buffer.from(await file.arrayBuffer());
			const movie = state.movies.find((m) => m.id === operations.variables.movieId);
			movie.artwork = { url: '/artwork/x' };
			state.uploads.push({ movieId: movie.id, bytes, type: file.type });
			result = { data: { uploadArtwork: { id: id() } } };
		} else {
			const { query, variables } = JSON.parse(body.toString());
			result = handle(query, variables);
		}
		res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(result));
	});
	return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({ state, server, url: `http://127.0.0.1:${server.address().port}` })));
}

test('the catalog has the real films; every credit points at a listed person and no person is unused', () => {
	const c = buildCatalog();
	assert.equal(c.movies.length, 12);
	assert.equal(c.movies.filter((m) => m.localPoster).length, 2);
	const names = new Set(c.people.map((p) => p.name));
	assert.equal(names.size, c.people.length, 'no duplicate people');
	assert.equal(new Set(c.movies.map((m) => `${m.title.toLowerCase()}|${m.releaseYear}`)).size, 12);
	const credited = new Set();
	for (const m of c.movies) {
		assert.ok(m.credits.length >= 3, `${m.title} has credits`);
		for (const credit of m.credits) {
			assert.ok(names.has(credit.person), `${credit.person} is listed`);
			credited.add(credit.person);
			assert.equal(credit.role === 'ACTOR', credit.characterName !== null);
		}
		assert.equal(new Set(m.credits.map((x) => `${x.person}|${x.role}`)).size, m.credits.length);
		// The same rules movie-service applies (BRD section 7).
		assert.ok(m.releaseYear >= 1888 && m.runtimeMinutes >= 1 && m.runtimeMinutes <= 1000);
		assert.ok(m.genres.length >= 1 && m.genres.length <= 5 && new Set(m.genres).size === m.genres.length);
		assert.ok(m.synopsis.length > 0 && m.synopsis.length <= 2000);
	}
	assert.deepEqual([...credited].sort(), [...names].sort());
	for (const p of c.people) assert.ok(p.birthDate === null || (p.birthDate >= '1850-01-01' && p.birthDate < '2026-01-01'));
});

test('a smaller catalog keeps only the people its films credit', () => {
	const c = buildCatalog({ movies: 2 });
	assert.deepEqual(c.movies.map((m) => m.title), ['The General', 'The Cabinet of Dr. Caligari']);
	assert.ok(c.people.every((p) => c.movies.some((m) => m.credits.some((x) => x.person === p.name))));
});

test('every committed poster is a credited JPEG under the upload limit; local-only posters are never committed', () => {
	const credits = readFileSync(new URL('./posters/CREDITS.md', import.meta.url), 'utf8');
	for (const movie of buildCatalog().movies) {
		if (movie.localPoster) {
			assert.ok(!existsSync(new URL(`./posters/${movie.poster}`, import.meta.url)), `${movie.poster} must not be in the repository (D25)`);
			continue;
		}
		const poster = posterFor(movie);
		assert.equal(poster.type, 'image/jpeg');
		assert.deepEqual([...poster.bytes.subarray(0, 3)], [0xff, 0xd8, 0xff], `${movie.poster} is a JPEG`);
		assert.ok(poster.bytes.length > 10_000 && poster.bytes.length < 5 * 1024 * 1024, `${movie.poster} size`);
		assert.ok(credits.includes(`\`${movie.poster}\``), `${movie.poster} is credited`);
	}
});

test('a first run creates everything and a second run creates nothing', async () => {
	const { state, server, url } = await fakeGateway();
	try {
		const client = createClient(url);
		const first = await seed(client, { localPosterDir: localDir(false) });
		const people = buildCatalog().people.length;
		assert.equal(first.peopleCreated, people);
		assert.equal(first.moviesCreated, FILMS);
		assert.equal(first.postersUploaded, COMMITTED);
		assert.equal(first.postersMissing, FILMS - COMMITTED);
		assert.equal(state.people.length, people);
		assert.equal(state.movies.length, FILMS);
		assert.equal(state.uploads.length, COMMITTED);
		const creditsAfterFirst = state.credits.length;
		assert.equal(first.creditsCreated, creditsAfterFirst);
		assert.equal(state.uploads[0].type, 'image/jpeg');
		assert.deepEqual(state.uploads[0].bytes, posterFor(buildCatalog().movies[0]).bytes);

		const second = await seed(client, { localPosterDir: localDir(false) });
		assert.deepEqual(second, { peopleCreated: 0, peopleSkipped: people, moviesCreated: 0, moviesSkipped: FILMS, creditsCreated: 0, creditsSkipped: creditsAfterFirst, postersUploaded: 0, postersMissing: FILMS - COMMITTED });
		assert.equal(state.people.length, people);
		assert.equal(state.movies.length, FILMS);
		assert.equal(state.credits.length, creditsAfterFirst);
		assert.equal(state.uploads.length, COMMITTED);

		// Adding the local posters later fills in just those two on the next run.
		const third = await seed(client, { localPosterDir: localDir(true) });
		assert.equal(third.postersUploaded, FILMS - COMMITTED);
		assert.equal(third.postersMissing, 0);
		assert.deepEqual(state.uploads.slice(-2).map((u) => u.type).sort(), ['image/jpeg', 'image/webp']);
	} finally {
		server.close();
	}
});

test('a movie that exists without artwork gets its poster on the next run', async () => {
	const { state, server, url } = await fakeGateway();
	try {
		const client = createClient(url);
		await seed(client, { movies: 3 });
		state.movies[1].artwork = null;
		const again = await seed(client, { movies: 3 });
		assert.equal(again.postersUploaded, 1);
		assert.equal(state.uploads.length, 4);
	} finally {
		server.close();
	}
});
