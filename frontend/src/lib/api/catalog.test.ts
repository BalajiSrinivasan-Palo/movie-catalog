import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as api from './catalog.js';

/** Every operation is checked at the wire: which query text and variables are sent, and what comes back. */
let sent: { query: string; variables: Record<string, unknown> };

function answer(data: unknown) {
	vi.stubGlobal(
		'fetch',
		vi.fn(async (_url: string, init: RequestInit) => {
			sent = JSON.parse(init.body as string);
			return new Response(JSON.stringify({ data }), { status: 200 });
		})
	);
}

beforeEach(() => vi.unstubAllGlobals());
afterEach(() => vi.unstubAllGlobals());

const pageInfo = { page: 0, size: 20, totalElements: 0, totalPages: 0 };
const movieInput = {
	title: 'Heat',
	releaseYear: 1995,
	runtimeMinutes: null,
	synopsis: '',
	genres: []
};
const personInput = { name: 'Ada', birthDate: null, bio: '' };

describe('catalog operations', () => {
	it('fetchMovies sends filter, sort and page with defaults', async () => {
		answer({ movies: { items: [], pageInfo } });
		const result = await api.fetchMovies({ page: 2, genres: ['DRAMA', 'CRIME'], yearFrom: 1990 });
		expect(result.items).toEqual([]);
		expect(sent.variables).toEqual({
			filter: { genres: ['DRAMA', 'CRIME'], yearFrom: 1990, yearTo: null },
			sort: { field: 'TITLE', direction: 'ASC' },
			page: { page: 2, size: 20 }
		});
	});

	it('fetchMovie asks for credits and the artwork fields', async () => {
		answer({ movie: { id: 'm1' } });
		expect(await api.fetchMovie('m1')).toEqual({ id: 'm1' });
		expect(sent.variables).toEqual({ id: 'm1' });
		expect(sent.query).toContain('credits');
		expect(sent.query).toContain('artwork { thumbnailUrl url');
	});

	it('movie mutations send their input and return the result', async () => {
		answer({ createMovie: { id: 'm1' } });
		expect(await api.createMovie(movieInput)).toEqual({ id: 'm1' });
		expect(sent.variables).toEqual({ input: movieInput });

		answer({ updateMovie: { id: 'm1' } });
		expect(await api.updateMovie('m1', movieInput)).toEqual({ id: 'm1' });
		expect(sent.variables).toEqual({ id: 'm1', input: movieInput });

		answer({ deleteMovie: 'm1' });
		await api.deleteMovie('m1');
		expect(sent.variables).toEqual({ id: 'm1' });

		answer({ removeArtwork: { id: 'm1' } });
		await api.removeArtwork('m1');
		expect(sent.variables).toEqual({ movieId: 'm1' });
		expect(sent.query).toContain('removeArtwork');
	});

	it('people queries and mutations', async () => {
		answer({ people: { items: [], pageInfo } });
		await api.fetchPeople({ page: 0, query: 'ad' });
		expect(sent.variables).toMatchObject({
			query: 'ad',
			sort: { field: 'NAME', direction: 'ASC' }
		});
		await api.fetchPeople({ page: 0, query: '' });
		expect(sent.variables.query).toBeNull();

		answer({ person: { id: 'p1' } });
		expect(await api.fetchPerson('p1', 3)).toEqual({ id: 'p1' });
		expect(sent.variables).toEqual({ id: 'p1', page: { page: 3, size: 20 } });

		answer({ createPerson: { id: 'p1' } });
		await api.createPerson(personInput);
		expect(sent.variables).toEqual({ input: personInput });

		answer({ updatePerson: { id: 'p1' } });
		await api.updatePerson('p1', personInput);
		expect(sent.variables).toEqual({ id: 'p1', input: personInput });

		answer({ deletePerson: 'p1' });
		await api.deletePerson('p1');
		expect(sent.variables).toEqual({ id: 'p1' });
	});

	it('credit mutations', async () => {
		answer({ addCredit: { id: 'c1' } });
		await api.addCredit('m1', { personId: 'p1', role: 'ACTOR', characterName: 'Vincent' });
		expect(sent.variables).toEqual({
			movieId: 'm1',
			input: { personId: 'p1', role: 'ACTOR', characterName: 'Vincent' }
		});

		answer({ updateCredit: { id: 'c1' } });
		await api.updateCredit('c1', { role: 'DIRECTOR', characterName: null });
		expect(sent.variables).toEqual({ id: 'c1', input: { role: 'DIRECTOR', characterName: null } });

		answer({ removeCredit: 'c1' });
		await api.removeCredit('c1');
		expect(sent.variables).toEqual({ id: 'c1' });
	});

	it('search operations send the query and page', async () => {
		answer({ search: { movies: { items: [], pageInfo }, people: { items: [], pageInfo } } });
		await api.searchAll('nol');
		expect(sent.variables).toEqual({
			query: 'nol',
			movies: { page: 0, size: 10 },
			people: { page: 0, size: 10 }
		});

		answer({ searchMovies: { items: [], pageInfo } });
		await api.searchMovies('nol', 2);
		expect(sent.variables).toEqual({ query: 'nol', page: { page: 2, size: 10 } });

		answer({ searchPeople: { items: [], pageInfo } });
		await api.searchPeople('nol', 1);
		expect(sent.variables).toEqual({ query: 'nol', page: { page: 1, size: 10 } });
	});

	it('label helpers fall back to the raw value', () => {
		expect(api.genreLabel('SCI_FI')).toBe('Sci-Fi');
		expect(api.genreLabel('OTHER')).toBe('OTHER');
		expect(api.roleLabel('ACTOR')).toBe('Actor');
	});
});
