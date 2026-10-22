/** Helpers for the journeys that run against the real gateway and services (S-603). */
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { deflateSync } from 'node:zlib';

/** Names carry a run id so repeated runs against one data directory never collide. */
export const RUN = Date.now().toString(36);
export const unique = (label: string) => `${label} ${RUN}`;

function crc32(buf: Buffer): number {
	let c = ~0;
	for (const b of buf) {
		c ^= b;
		for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
	}
	return ~c >>> 0;
}
function chunk(type: string, data: Buffer): Buffer {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body));
	return Buffer.concat([len, body, crc]);
}

/**
 * A valid grey PNG of the given size. `padBytes` adds an ancillary tEXt chunk so the file is big enough
 * for the upload progress bar to be visible without needing a huge image.
 */
export function makePng(width = 200, height = 300, padBytes = 0): Buffer {
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(width, 0);
	ihdr.writeUInt32BE(height, 4);
	ihdr[8] = 8; // bit depth
	ihdr[9] = 0; // grayscale
	const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(width, 128)]);
	const raw = Buffer.concat(Array.from({ length: height }, () => row));
	const parts = [
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', ihdr)
	];
	if (padBytes > 0)
		parts.push(chunk('tEXt', Buffer.concat([Buffer.from('pad\0'), Buffer.alloc(padBytes, 0x61)])));
	parts.push(chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)));
	return Buffer.concat(parts);
}

/** Sends a GraphQL operation straight to the gateway, for setting up data a journey needs. */
export async function gql<T>(
	request: APIRequestContext,
	query: string,
	variables: Record<string, unknown> = {}
): Promise<T> {
	const res = await request.post('/graphql', { data: { query, variables } });
	const body = await res.json();
	expect(body.errors, JSON.stringify(body.errors)).toBeUndefined();
	return body.data as T;
}

export const createMovieApi = (request: APIRequestContext, title: string, releaseYear = 2001) =>
	gql<{ createMovie: { id: string } }>(
		request,
		'mutation($i: MovieInput!) { createMovie(input: $i) { id } }',
		{
			i: { title, releaseYear, runtimeMinutes: 100, synopsis: `About ${title}`, genres: ['DRAMA'] }
		}
	).then((d) => d.createMovie.id);

export const createPersonApi = (request: APIRequestContext, name: string) =>
	gql<{ createPerson: { id: string } }>(
		request,
		'mutation($i: PersonInput!) { createPerson(input: $i) { id } }',
		{ i: { name, birthDate: null, bio: `Bio of ${name}` } }
	).then((d) => d.createPerson.id);

export const addCreditApi = (
	request: APIRequestContext,
	movieId: string,
	personId: string,
	role = 'ACTOR'
) =>
	gql(request, 'mutation($m: ID!, $i: CreditInput!) { addCredit(movieId: $m, input: $i) { id } }', {
		m: movieId,
		i: { personId, role, characterName: null }
	});

/**
 * Records every request the page makes. NFR-ARCH-03: the browser talks to the gateway only, so besides
 * static assets from this origin, only /graphql and /artwork/* may appear.
 */
export function recordRequests(page: Page) {
	const urls: URL[] = [];
	page.on('request', (r) => urls.push(new URL(r.url())));
	return {
		urls,
		offenders(origin: string) {
			return urls
				.filter((u) => u.protocol.startsWith('http'))
				.filter((u) => {
					if (u.origin !== origin) return true;
					const ok =
						u.pathname === '/graphql' ||
						u.pathname.startsWith('/artwork/') ||
						u.pathname.startsWith('/_app/') ||
						['/', '/favicon.png', '/favicon.svg', '/favicon.ico', '/robots.txt'].includes(
							u.pathname
						) ||
						/^\/(movies|people|search)(\/|$)/.test(u.pathname);
					return !ok;
				})
				.map((u) => u.href);
		}
	};
}
