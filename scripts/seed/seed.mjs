#!/usr/bin/env node
// Loads demo data through the public GraphQL API only (S-601). Safe to run again: nothing is duplicated.
//   node scripts/seed/seed.mjs            real films, their cast and crew, and their posters (D24, D25);
//                                         local-only posters come from data/local-posters/ when present
//   GATEWAY_URL=http://host:8080 node ...  another gateway
// Needs Node 22 (built-in fetch and FormData). No packages.
import { buildCatalog } from './data.mjs';
import { posterFor } from './posters.mjs';

const UPLOAD = `mutation($movieId: ID!, $file: Upload!) { uploadArtwork(movieId: $movieId, file: $file) { id } }`;

export class GraphQlError extends Error {
	constructor(error) {
		super(error?.message ?? 'GraphQL error');
		this.code = error?.extensions?.code;
		this.messageKey = error?.extensions?.messageKey;
		this.fieldErrors = error?.extensions?.fieldErrors ?? [];
	}
}

export function createClient(baseUrl, fetchImpl = fetch) {
	const url = new URL('/graphql', baseUrl).toString();
	async function send(init) {
		const response = await fetchImpl(url, init);
		const body = await response.json();
		if (body.errors?.length) throw new GraphQlError(body.errors[0]);
		return body.data;
	}
	return {
		query: (query, variables = {}) =>
			send({ method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ query, variables }) }),
		upload(movieId, bytes, type, filename) {
			const form = new FormData();
			form.append('operations', JSON.stringify({ query: UPLOAD, variables: { movieId, file: null } }));
			form.append('map', JSON.stringify({ 0: ['variables.file'] }));
			form.append('0', new Blob([bytes], { type }), filename);
			return send({ method: 'POST', headers: { 'apollo-require-preflight': 'true' }, body: form });
		}
	};
}

const isConflict = (e, key) => e instanceof GraphQlError && e.code === 'CONFLICT' && (key === undefined || e.messageKey === key);

/** Runs the whole seed and returns counts, so a second run can be compared with the first. */
export async function seed(client, options = {}, log = () => {}) {
	const catalog = buildCatalog(options);
	const counts = { peopleCreated: 0, peopleSkipped: 0, moviesCreated: 0, moviesSkipped: 0, creditsCreated: 0, creditsSkipped: 0, postersUploaded: 0, postersMissing: 0 };
	const personIds = new Map();

	for (const person of catalog.people) {
		// An existing person is found by exact name through the name filter (S-601 AC4).
		const found = await client.query(
			`query($q: String) { people(query: $q, page: { page: 0, size: 100 }) { items { id name } } }`,
			{ q: person.name }
		);
		const existing = found.people.items.find((p) => p.name.toLowerCase() === person.name.toLowerCase());
		if (existing) {
			personIds.set(person.name, existing.id);
			counts.peopleSkipped++;
			continue;
		}
		const created = await client.query(
			`mutation($input: PersonInput!) { createPerson(input: $input) { id } }`,
			{ input: person }
		);
		personIds.set(person.name, created.createPerson.id);
		counts.peopleCreated++;
	}
	log(`people: ${counts.peopleCreated} created, ${counts.peopleSkipped} already there`);

	for (const movie of catalog.movies) {
		const input = {
			title: movie.title,
			releaseYear: movie.releaseYear,
			runtimeMinutes: movie.runtimeMinutes,
			synopsis: movie.synopsis,
			genres: movie.genres
		};
		let id;
		let hasArtwork = false;
		try {
			const created = await client.query(
				`mutation($input: MovieInput!) { createMovie(input: $input) { id } }`,
				{ input }
			);
			id = created.createMovie.id;
			counts.moviesCreated++;
		} catch (e) {
			if (!isConflict(e, 'movie.duplicate')) throw e;
			// Already there: look it up by year, then by title.
			const list = await client.query(
				`query($y: Int) { movies(filter: { yearFrom: $y, yearTo: $y }, page: { page: 0, size: 100 }) { items { id title artwork { url } } } }`,
				{ y: movie.releaseYear }
			);
			const existing = list.movies.items.find((m) => m.title.toLowerCase() === movie.title.toLowerCase());
			if (!existing) throw e;
			id = existing.id;
			hasArtwork = existing.artwork !== null;
			counts.moviesSkipped++;
		}

		if (!hasArtwork) {
			const poster = options.localPosterDir ? posterFor(movie, options.localPosterDir) : posterFor(movie);
			if (poster) {
				await client.upload(id, poster.bytes, poster.type, poster.filename);
				counts.postersUploaded++;
			} else {
				counts.postersMissing++; // a local-only poster that is not on this machine (D25)
				log(`no local poster for "${movie.title}": add data/local-posters/${movie.poster} and run again`);
			}
		}

		for (const credit of movie.credits) {
			try {
				await client.query(
					`mutation($movieId: ID!, $input: CreditInput!) { addCredit(movieId: $movieId, input: $input) { id } }`,
					{ movieId: id, input: { personId: personIds.get(credit.person), role: credit.role, characterName: credit.characterName } }
				);
				counts.creditsCreated++;
			} catch (e) {
				if (!isConflict(e, 'credit.duplicate')) throw e;
				counts.creditsSkipped++;
			}
		}
	}
	log(`movies: ${counts.moviesCreated} created, ${counts.moviesSkipped} already there; posters uploaded: ${counts.postersUploaded}, missing local posters: ${counts.postersMissing}`);
	log(`credits: ${counts.creditsCreated} created, ${counts.creditsSkipped} already there`);
	return counts;
}

if (import.meta.url === `file://${process.argv[1]}`) {
	const base = process.env.GATEWAY_URL ?? 'http://localhost:8080';
	console.log(`Seeding ${base} ...`);
	try {
		const counts = await seed(createClient(base), {}, console.log);
		console.log('Done.', JSON.stringify(counts));
	} catch (e) {
		console.error(`Seed failed: ${e instanceof GraphQlError ? `${e.code} ${e.messageKey ?? ''} ${e.message}` : e.message}`);
		console.error('Is the stack running? See the README section "Run".');
		process.exit(1);
	}
}
