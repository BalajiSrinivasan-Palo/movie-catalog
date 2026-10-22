/**
 * An in-memory stand-in for the gateway, used by the browser tests that check layout and accessibility.
 * It answers the same GraphQL operations the app sends, so pages render exactly as they do against the real
 * backend, without needing it. The journey tests in e2e/stack use the real stack instead.
 */
import type { Page, Route } from '@playwright/test';

type Json = Record<string, unknown>;

export interface MockMovie {
	id: string;
	title: string;
	releaseYear: number;
	runtimeMinutes: number | null;
	synopsis: string;
	genres: string[];
	artwork: Json | null;
}
export interface MockPerson {
	id: string;
	name: string;
	birthDate: string | null;
	bio: string;
}
export interface MockCredit {
	id: string;
	movieId: string;
	personId: string;
	role: string;
	characterName: string | null;
}

export interface MockOptions {
	movies?: MockMovie[];
	people?: MockPerson[];
	credits?: MockCredit[];
	/** Operation names that fail with INTERNAL, for example ['Movies']. '*' fails everything except Health. */
	fail?: string[];
	/** Milliseconds to wait before answering every operation except Health. */
	delayMs?: number;
}

export const ARTWORK = (movieId: string) => ({
	thumbnailUrl: `/artwork/${movieId}/thumbnail?v=1`,
	url: `/artwork/${movieId}/original?v=1`,
	width: 240,
	height: 360,
	contentType: 'image/png',
	sizeBytes: 1500
});

export const sampleMovies = (): MockMovie[] => [
	{
		id: 'm1',
		title: 'The Silent Harbor',
		releaseYear: 1999,
		runtimeMinutes: 112,
		synopsis: 'A demo story.',
		genres: ['DRAMA', 'MYSTERY'],
		artwork: ARTWORK('m1')
	},
	{
		id: 'm2',
		title: 'The Golden Signal',
		releaseYear: 2004,
		runtimeMinutes: null,
		synopsis: '',
		genres: [],
		artwork: null
	},
	{
		id: 'm3',
		title:
			'A very long title that keeps going and going to check that wrapping works on a narrow screen without overflow',
		releaseYear: 2011,
		runtimeMinutes: 95,
		synopsis: 'Another demo story '.repeat(20),
		genres: ['SCI_FI'],
		artwork: null
	}
];
export const samplePeople = (): MockPerson[] => [
	{ id: 'p1', name: 'Ada Alder', birthDate: '1970-04-02', bio: 'A fictional demo person.' },
	{ id: 'p2', name: 'Bruno Bellamy', birthDate: null, bio: '' }
];
export const sampleCredits = (): MockCredit[] => [
	{ id: 'c1', movieId: 'm1', personId: 'p1', role: 'DIRECTOR', characterName: null },
	{ id: 'c2', movieId: 'm1', personId: 'p2', role: 'ACTOR', characterName: 'The Captain' }
];

const png1x1 = Buffer.from(
	'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
	'base64'
);

const pageInfo = (items: unknown[], total: number, page: number, size: number) => ({
	items,
	pageInfo: { page, size, totalElements: total, totalPages: Math.ceil(total / size) }
});
const paged = <T>(all: T[], p?: { page?: number; size?: number }) => {
	const size = p?.size ?? 20;
	const page = p?.page ?? 0;
	return pageInfo(all.slice(page * size, page * size + size), all.length, page, size);
};
const err = (code: string, messageKey?: string, extra: Json = {}) => ({
	data: null,
	errors: [{ message: code, extensions: { code, ...(messageKey ? { messageKey } : {}), ...extra } }]
});

export interface MockApi {
	state: { movies: MockMovie[]; people: MockPerson[]; credits: MockCredit[] };
	/** Every URL path the page requested from the network, for the "only /graphql and /artwork" check. */
	requests: string[];
}

export async function mockApi(page: Page, options: MockOptions = {}): Promise<MockApi> {
	const state = {
		movies: options.movies ?? sampleMovies(),
		people: options.people ?? samplePeople(),
		credits: options.credits ?? sampleCredits()
	};
	const api: MockApi = { state, requests: [] };
	let nextId = 100;

	const personView = (p: MockPerson) => ({ ...p });
	const movieView = (m: MockMovie) => {
		const credits = state.credits
			.filter((c) => c.movieId === m.id)
			.map((c) => {
				const person = state.people.find((p) => p.id === c.personId);
				return {
					id: c.id,
					role: c.role,
					characterName: c.characterName,
					personStatus: person ? 'RESOLVED' : 'NOT_FOUND',
					personId: c.personId,
					person: person ? { id: person.id, name: person.name } : null
				};
			});
		return { ...m, creditCount: credits.length, credits, creditsTruncated: false };
	};

	const answer = (operation: string, v: Json, query: string): Json => {
		switch (operation) {
			case 'Health':
				return {
					data: {
						health: [
							{ service: 'movie-service', status: 'UP', detail: null },
							{ service: 'people-service', status: 'UP', detail: null }
						]
					}
				};
			case 'Movies': {
				const f = (v.filter ?? {}) as Json;
				let list = state.movies.filter(
					(m) =>
						(!f.genre || m.genres.includes(f.genre as string)) &&
						(!Array.isArray(f.genres) ||
							f.genres.length === 0 ||
							f.genres.some((g) => m.genres.includes(g as string))) &&
						(!f.yearFrom || m.releaseYear >= (f.yearFrom as number)) &&
						(!f.yearTo || m.releaseYear <= (f.yearTo as number))
				);
				const sort = (v.sort ?? {}) as Json;
				list = [...list].sort((a, b) =>
					sort.field === 'RELEASE_YEAR'
						? a.releaseYear - b.releaseYear
						: a.title.localeCompare(b.title)
				);
				if (sort.direction === 'DESC') list.reverse();
				return { data: { movies: paged(list.map(movieView), v.page as Json) } };
			}
			case 'Movie': {
				const m = state.movies.find((x) => x.id === v.id);
				return m ? { data: { movie: movieView(m) } } : err('NOT_FOUND', 'movie.not_found');
			}
			case 'CreateMovie': {
				const input = v.input as MockMovie;
				if (
					state.movies.some(
						(m) =>
							m.title.toLowerCase() === input.title.toLowerCase() &&
							m.releaseYear === input.releaseYear
					)
				)
					return err('CONFLICT', 'movie.duplicate');
				const movie = { ...input, id: `m${nextId++}`, artwork: null };
				state.movies.push(movie);
				return { data: { createMovie: movieView(movie) } };
			}
			case 'UpdateMovie': {
				const m = state.movies.find((x) => x.id === v.id);
				if (!m) return err('NOT_FOUND', 'movie.not_found');
				Object.assign(m, v.input);
				return { data: { updateMovie: movieView(m) } };
			}
			case 'DeleteMovie':
				state.movies = state.movies.filter((m) => m.id !== v.id);
				state.credits = state.credits.filter((c) => c.movieId !== v.id);
				return { data: { deleteMovie: v.id } };
			case 'RemoveArtwork': {
				const m = state.movies.find((x) => x.id === v.movieId);
				if (m) m.artwork = null;
				return { data: { removeArtwork: { id: v.movieId } } };
			}
			case 'People': {
				const q = ((v.query as string) ?? '').toLowerCase();
				const list = state.people
					.filter((p) => p.name.toLowerCase().includes(q))
					.sort((a, b) => a.name.localeCompare(b.name));
				return { data: { people: paged(list.map(personView), v.page as Json) } };
			}
			case 'Person': {
				const p = state.people.find((x) => x.id === v.id);
				if (!p) return err('NOT_FOUND', 'person.not_found');
				const entries = state.credits
					.filter((c) => c.personId === p.id)
					.map((c) => {
						const m = state.movies.find((x) => x.id === c.movieId)!;
						return {
							creditId: c.id,
							role: c.role,
							characterName: c.characterName,
							movie: {
								id: m.id,
								title: m.title,
								releaseYear: m.releaseYear,
								thumbnailUrl: m.artwork ? (m.artwork.thumbnailUrl as string) : null
							}
						};
					});
				return {
					data: { person: { ...personView(p), filmography: paged(entries, v.page as Json) } }
				};
			}
			case 'CreatePerson': {
				const person = { ...(v.input as MockPerson), id: `p${nextId++}` };
				state.people.push(person);
				return { data: { createPerson: personView(person) } };
			}
			case 'UpdatePerson': {
				const p = state.people.find((x) => x.id === v.id);
				if (!p) return err('NOT_FOUND', 'person.not_found');
				Object.assign(p, v.input);
				return { data: { updatePerson: personView(p) } };
			}
			case 'DeletePerson': {
				const blocking = state.credits
					.filter((c) => c.personId === v.id)
					.map((c) => state.movies.find((m) => m.id === c.movieId)!);
				if (blocking.length > 0) {
					return err('CONFLICT', 'person.has_credits', {
						blockingMovies: blocking.map((m) => ({
							id: m.id,
							title: m.title,
							releaseYear: m.releaseYear
						})),
						creditCount: blocking.length
					});
				}
				state.people = state.people.filter((p) => p.id !== v.id);
				return { data: { deletePerson: v.id } };
			}
			case 'AddCredit': {
				const input = v.input as Json;
				if (
					state.credits.some(
						(c) => c.movieId === v.movieId && c.personId === input.personId && c.role === input.role
					)
				)
					return err('CONFLICT', 'credit.duplicate');
				const credit = {
					id: `c${nextId++}`,
					movieId: v.movieId as string,
					personId: input.personId as string,
					role: input.role as string,
					characterName: (input.characterName as string | null) ?? null
				};
				state.credits.push(credit);
				return {
					data: {
						addCredit: movieView(state.movies.find((m) => m.id === v.movieId)!).credits.find(
							(c) => c.id === credit.id
						)
					}
				};
			}
			case 'UpdateCredit': {
				const c = state.credits.find((x) => x.id === v.id);
				if (!c) return err('NOT_FOUND', 'credit.not_found');
				Object.assign(c, v.input);
				return {
					data: {
						updateCredit: movieView(state.movies.find((m) => m.id === c.movieId)!).credits.find(
							(x) => x.id === c.id
						)
					}
				};
			}
			case 'RemoveCredit':
				state.credits = state.credits.filter((c) => c.id !== v.id);
				return { data: { removeCredit: v.id } };
			case 'Search':
			case 'SearchMovies':
			case 'SearchPeople': {
				const q = (v.query as string).trim().toLowerCase();
				if (q.length < 2)
					return err('VALIDATION_FAILED', undefined, {
						fieldErrors: [{ field: 'query', messageKey: 'search.query.invalid' }]
					});
				const movies = state.movies.filter(
					(m) => m.title.toLowerCase().includes(q) || m.synopsis.toLowerCase().includes(q)
				);
				const people = state.people.filter(
					(p) => p.name.toLowerCase().includes(q) || p.bio.toLowerCase().includes(q)
				);
				if (operation === 'SearchMovies')
					return { data: { searchMovies: paged(movies, v.page as Json) } };
				if (operation === 'SearchPeople')
					return { data: { searchPeople: paged(people, v.page as Json) } };
				return {
					data: {
						search: {
							movies: paged(movies, v.movies as Json),
							people: paged(people, v.people as Json)
						}
					}
				};
			}
			default:
				throw new Error(`mock-api: unhandled operation ${operation} in ${query.slice(0, 60)}`);
		}
	};

	await page.route('**/graphql', async (route: Route) => {
		const request = route.request();
		api.requests.push(new URL(request.url()).pathname);
		const contentType = request.headers()['content-type'] ?? '';
		let operation: string;
		let variables: Json = {};
		let query = '';
		if (contentType.startsWith('multipart/form-data')) {
			operation = 'UploadArtwork';
			const movieId = /"movieId":"([^"]+)"/.exec(request.postData() ?? '')?.[1] ?? 'm1';
			variables = { movieId };
		} else {
			const body = request.postDataJSON() as { query: string; variables?: Json };
			query = body.query;
			variables = body.variables ?? {};
			operation = /(?:query|mutation)\s+(\w+)/.exec(query)?.[1] ?? 'Unknown';
		}
		if (operation !== 'Health' && options.delayMs)
			await new Promise((r) => setTimeout(r, options.delayMs));
		const failing =
			operation !== 'Health' && (options.fail?.includes(operation) || options.fail?.includes('*'));
		let payload: Json;
		if (failing) payload = err('INTERNAL');
		else if (operation === 'UploadArtwork') {
			const m = state.movies.find((x) => x.id === variables.movieId);
			if (m) m.artwork = ARTWORK(m.id);
			payload = { data: { uploadArtwork: { id: variables.movieId, artwork: m?.artwork ?? null } } };
		} else payload = answer(operation, variables, query);
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(payload)
		});
	});

	await page.route('**/artwork/**', async (route) => {
		api.requests.push(new URL(route.request().url()).pathname);
		await route.fulfill({ status: 200, contentType: 'image/png', body: png1x1 });
	});
	return api;
}
