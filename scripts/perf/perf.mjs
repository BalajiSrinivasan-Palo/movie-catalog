#!/usr/bin/env node
// Performance check on a larger dataset (S-604). Uses the public GraphQL API only.
//   node scripts/perf/perf.mjs                 load 1,000 movies and 1,000 people if missing, measure, write reports/perf.md
//   node scripts/perf/perf.mjs --no-load       measure only
//   GATEWAY_URL=http://host:8080 node ...      another gateway
//   --strict                                   exit 1 when a target is missed (the report is written either way)
// Names are "Perf Movie 0001" and "Perf Person 0001", so the data is easy to tell from the demo seed and safe to load twice.
import os from 'node:os';
import { writeFileSync, mkdirSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { createClient, GraphQlError } from '../seed/seed.mjs';
import { posterPng } from '../seed/posters.mjs';

const base = process.env.GATEWAY_URL ?? 'http://localhost:8080';
const args = new Set(process.argv.slice(2));
const COUNT = 1000, SAMPLES = 50, CONCURRENCY = 8;
const client = createClient(base);
const pad = (n) => String(n).padStart(4, '0');

const TARGETS = { list: 500, detail: 500, search: 800, upload: 5000 }; // ms, NFR-PERF-01..03

async function pool(items, worker) {
	let next = 0;
	await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
		while (next < items.length) { const i = next++; await worker(items[i], i); }
	}));
}

async function total(query, field, variables = {}) {
	const d = await client.query(query, variables);
	return d[field].pageInfo.totalElements;
}

async function load() {
	const havePeople = await total(`query($q: String) { people(query: $q, page: {page: 0, size: 1}) { pageInfo { totalElements } } }`, 'people', { q: 'Perf Person' });
	const haveMovies = await total(`query { movies(page: {page: 0, size: 1}) { pageInfo { totalElements } } }`, 'movies');
	if (havePeople >= COUNT && haveMovies >= COUNT) { console.log('dataset already loaded'); return; }
	console.log(`loading ${COUNT} people and ${COUNT} movies ...`);
	const ids = [];
	await pool([...Array(COUNT).keys()], async (i) => {
		try {
			const r = await client.query(`mutation($i: PersonInput!) { createPerson(input: $i) { id } }`,
				{ i: { name: `Perf Person ${pad(i)}`, birthDate: null, bio: `Generated for the performance check, number ${i}.` } });
			ids[i] = r.createPerson.id;
		} catch (e) { if (!(e instanceof GraphQlError && e.code === 'CONFLICT')) throw e; }
	});
	const genres = ['ACTION', 'COMEDY', 'DRAMA', 'THRILLER', 'SCI_FI', 'HORROR', 'WESTERN', 'WAR'];
	const movieIds = [];
	await pool([...Array(COUNT).keys()], async (i) => {
		try {
			const r = await client.query(`mutation($i: MovieInput!) { createMovie(input: $i) { id } }`,
				{ i: { title: `Perf Movie ${pad(i)}`, releaseYear: 1950 + (i % 75), runtimeMinutes: 80 + (i % 90),
					synopsis: `Generated for the performance check, number ${i}.`, genres: [genres[i % genres.length]] } });
			movieIds[i] = r.createMovie.id;
		} catch (e) { if (!(e instanceof GraphQlError && e.code === 'CONFLICT')) throw e; }
	});
	// Movie 0 gets 50 credits for the NFR-PERF-04 style detail page.
	const people = await client.query(`query { people(query: "Perf Person", page: {page: 0, size: 100}) { items { id } } }`);
	const first = movieIds[0] ?? (await client.query(`query { movies(filter: {}, page: {page:0,size:100}) { items { id title } } }`)).movies.items.find((m) => m.title === 'Perf Movie 0000')?.id;
	for (const [n, p] of people.people.items.slice(0, 50).entries()) {
		try { await client.query(`mutation($m: ID!, $i: CreditInput!) { addCredit(movieId: $m, input: $i) { id } }`, { m: first, i: { personId: p.id, role: n % 5 === 0 ? 'DIRECTOR' : 'ACTOR', characterName: null } }); }
		catch (e) { if (!(e instanceof GraphQlError && e.code === 'CONFLICT')) throw e; }
	}
}

/** A valid PNG of about `bytes` bytes (grey pixels plus an ancillary text chunk), for the upload timing. */
function pngOfSize(bytes) {
	const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
	const crc = (b) => { let c = ~0; for (const x of b) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8); return ~c >>> 0; };
	const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const body = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(body)); return Buffer.concat([len, body, c]); };
	const w = 600, h = 900, ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8;
	const raw = Buffer.concat(Array.from({ length: h }, () => Buffer.concat([Buffer.from([0]), Buffer.alloc(w, 128)])));
	const idat = deflateSync(raw);
	const fixed = 8 + 25 + (12 + idat.length) + 12;
	const text = Buffer.concat([Buffer.from('pad\0'), Buffer.alloc(Math.max(0, bytes - fixed - 12 - 4), 0x61)]);
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('tEXt', text), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

const p95 = (values) => [...values].sort((a, b) => a - b)[Math.ceil(values.length * 0.95) - 1];
const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];

async function time(fn, samples = SAMPLES) {
	await fn(0); // warm-up, not counted
	const ms = [];
	for (let i = 0; i < samples; i++) { const t = performance.now(); await fn(i); ms.push(performance.now() - t); }
	return ms;
}

async function measure() {
	const results = {};
	results.list = await time((i) => client.query(
		`query($p: Int) { movies(sort: {field: TITLE, direction: ASC}, page: {page: $p, size: 20}) { items { id title releaseYear genres creditCount artwork { thumbnailUrl } } pageInfo { totalElements } } }`, { p: i % 50 }));
	const big = (await client.query(`query { movies(filter: {}, page: {page: 0, size: 5}) { items { id title creditCount } } }`)).movies.items.sort((a, b) => b.creditCount - a.creditCount)[0];
	const detail = (await client.query(`query { search(query: "Perf Movie 0000") { movies { items { id creditCount } } } }`)).search.movies.items[0] ?? big;
	results.detail = await time(() => client.query(
		`query($id: ID!) { movie(id: $id) { id title synopsis creditCount creditsTruncated credits { id role personStatus person { id name } } } }`, { id: detail.id }));
	results.detailCredits = detail.creditCount;
	const words = ['Perf', 'Movie', 'Person', 'Generated', 'number', 'zzz-no-match'];
	results.search = await time((i) => client.query(
		`query($q: String!) { search(query: $q) { movies { items { id title } pageInfo { totalElements } } people { items { id name } pageInfo { totalElements } } } }`, { q: words[i % words.length] }));

	// Upload: 5 MiB minus a little, to stay under the limit; a different movie each time so replacement is not what is measured.
	const png = pngOfSize(5 * 1024 * 1024 - 2048);
	const targets = (await client.query(`query { movies(page: {page: 5, size: 10}) { items { id } } }`)).movies.items;
	results.upload = await time((i) => client.upload(targets[i % targets.length].id, png, 'big.png'), 5);
	results.uploadBytes = png.length;

	// NFR-PERF-05: images transferred for a list page of 20 movies.
	const page = (await client.query(`query { movies(page: {page: 0, size: 20}) { items { id artwork { thumbnailUrl } } } }`)).movies.items;
	for (const m of page) if (!m.artwork) await client.upload(m.id, posterPng(1), 'p.png');
	const refreshed = (await client.query(`query { movies(page: {page: 0, size: 20}) { items { artwork { thumbnailUrl } } } }`)).movies.items;
	let bytes = 0;
	for (const m of refreshed) { const r = await fetch(new URL(m.artwork.thumbnailUrl, base)); bytes += (await r.arrayBuffer()).byteLength; }
	results.thumbBytes = bytes;
	return results;
}

function report(r, counts) {
	const row = (name, ms, target) => `| ${name} | ${median(ms).toFixed(0)} | ${p95(ms).toFixed(0)} | ${Math.max(...ms).toFixed(0)} | ${target} | ${p95(ms) <= target ? 'met' : '**NOT MET**'} |`;
	const cpu = os.cpus();
	const md = [
		'# Performance report (S-604)', '',
		`Generated: ${new Date().toISOString()}`,
		`Machine: ${cpu[0]?.model ?? 'unknown'} x${cpu.length}, ${(os.totalmem() / 2 ** 30).toFixed(1)} GiB RAM, ${os.type()} ${os.release()}, Node ${process.version}`,
		`Gateway: ${base} (all three services on one machine; H2 file databases)`,
		`Dataset: ${counts.movies} movies, ${counts.people} people; the movie used for detail has ${r.detailCredits} credits`, '',
		'Times are milliseconds measured at the client over localhost, including JSON parsing. Each row is 50 requests after one warm-up (upload: 5).', '',
		'| Operation | median | p95 | max | target p95 | result |', '|---|---|---|---|---|---|',
		row('Movie list, page of 20 (NFR-PERF-01)', r.list, TARGETS.list),
		row('Movie detail with credits (NFR-PERF-01)', r.detail, TARGETS.detail),
		row('Search, movies and people (NFR-PERF-02)', r.search, TARGETS.search),
		row(`Upload ${(r.uploadBytes / 2 ** 20).toFixed(2)} MiB (NFR-PERF-03)`, r.upload, TARGETS.upload), '',
		`NFR-PERF-05: thumbnails for a page of 20 movies total ${(r.thumbBytes / 1024).toFixed(0)} KiB (target under 1024 KiB): ${r.thumbBytes < 1024 * 1024 ? 'met' : '**NOT MET**'}.`, '',
		'NFR-PERF-04 (one batched people lookup per movie page) is checked by the gRPC call-count test in the gateway tests, not by timing.', '',
		'Targets that are not met are reported as they are. The p95 of 5 upload samples is simply the slowest one.', ''
	].join('\n');
	mkdirSync(new URL('../../reports/', import.meta.url), { recursive: true });
	writeFileSync(new URL('../../reports/perf.md', import.meta.url), md);
	return md;
}

try {
	if (!args.has('--no-load')) await load();
	const counts = {
		movies: await total(`query { movies(page: {page: 0, size: 1}) { pageInfo { totalElements } } }`, 'movies'),
		people: await total(`query { people(page: {page: 0, size: 1}) { pageInfo { totalElements } } }`, 'people')
	};
	const r = await measure();
	const md = report(r, counts);
	console.log(md);
	const missed = md.includes('NOT MET');
	if (missed && args.has('--strict')) process.exit(1);
} catch (e) {
	console.error(`Perf run failed: ${e.message}`);
	process.exit(1);
}
