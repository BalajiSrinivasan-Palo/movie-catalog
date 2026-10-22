import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { ApiError } from '#lib/api/client.js';
import * as api from '#lib/api/catalog.js';
import SearchView from './SearchView.svelte';
import { movie, paged, person } from './testing.js';

vi.mock('#lib/api/catalog.js', async (original) => ({
	...(await original<typeof import('#lib/api/catalog.js')>()),
	searchAll: vi.fn(),
	searchMovies: vi.fn(),
	searchPeople: vi.fn()
}));
const searchAll = vi.mocked(api.searchAll);
const searchMovies = vi.mocked(api.searchMovies);
const searchPeople = vi.mocked(api.searchPeople);

beforeEach(() => vi.resetAllMocks());

const result = (
	movies = [movie()],
	people = [person()],
	moviesTotal = movies.length,
	peopleTotal = people.length
) => ({
	movies: paged(movies, moviesTotal, 0, 10),
	people: paged(people, peopleTotal, 0, 10)
});

/** A promise the test settles by hand, to control the order of answers. */
function deferred<T>() {
	let resolve!: (v: T) => void;
	let reject!: (e: unknown) => void;
	const promise = new Promise<T>((res, rej) => {
		resolve = res;
		reject = rej;
	});
	return { promise, resolve, reject };
}

describe('SearchView', () => {
	it('asks for at least 2 characters and sends no request for a short or whitespace query', async () => {
		const view = render(SearchView, { q: ' a ' });
		expect(screen.getByRole('status')).toHaveTextContent('Enter between 2 and 100 characters');
		await view.rerender({ q: '   ' });
		await view.rerender({ q: 'x'.repeat(101) });
		expect(screen.getByRole('status')).toHaveTextContent('Enter between 2 and 100 characters');
		expect(searchAll).not.toHaveBeenCalled();
	});

	it('shows a searching state, then movies and people as separate groups with links', async () => {
		const pending = deferred<ReturnType<typeof result>>();
		searchAll.mockReturnValue(pending.promise);
		render(SearchView, { q: 'nol' });
		expect(screen.getByRole('status')).toHaveTextContent('Searching…');
		expect(screen.getByRole('progressbar', { name: 'Searching' })).toBeInTheDocument();
		pending.resolve(
			result(
				[movie({ id: 'm1', title: 'Inception' })],
				[person({ id: 'p1', name: 'Christopher Nolan' })]
			)
		);
		expect(await screen.findByRole('link', { name: 'Inception' })).toHaveAttribute(
			'href',
			'/movies/m1'
		);
		expect(screen.getByRole('link', { name: 'Christopher Nolan' })).toHaveAttribute(
			'href',
			'/people/p1'
		);
		expect(screen.getByRole('heading', { name: 'Movies (1)' })).toBeInTheDocument();
		expect(screen.getByRole('heading', { name: 'People (1)' })).toBeInTheDocument();
		expect(screen.getByRole('status')).toHaveTextContent('1 movies and 1 people found for “nol”.');
		expect(searchAll).toHaveBeenCalledWith('nol', expect.anything());
	});

	it('trims the query before sending it', async () => {
		searchAll.mockResolvedValue(result());
		render(SearchView, { q: '  heat  ' });
		await screen.findByRole('link', { name: 'Heat' });
		expect(searchAll.mock.calls[0][0]).toBe('heat');
	});

	it('shows an empty-result message that includes the query', async () => {
		searchAll.mockResolvedValue(result([], []));
		render(SearchView, { q: 'zzzz' });
		expect(await screen.findByText('No results for “zzzz”.')).toBeInTheDocument();
		expect(screen.queryByRole('heading', { name: /Movies/ })).not.toBeInTheDocument();
	});

	it('says so when only one group has matches', async () => {
		searchAll.mockResolvedValue(result([movie()], []));
		render(SearchView, { q: 'heat' });
		expect(await screen.findByText('No people match.')).toBeInTheDocument();
	});

	it('shows an error with a retry that searches again', async () => {
		searchAll.mockRejectedValueOnce(new ApiError('UNAVAILABLE', 'x')).mockResolvedValue(result());
		const user = userEvent.setup();
		render(SearchView, { q: 'heat' });
		expect(await screen.findByRole('alert')).toHaveTextContent('not available');
		await user.click(screen.getByRole('button', { name: 'Try again' }));
		expect(await screen.findByRole('link', { name: 'Heat' })).toBeInTheDocument();
		expect(searchAll).toHaveBeenCalledTimes(2);
	});

	it('ignores an older answer that arrives after a newer one (EC-13)', async () => {
		const older = deferred<ReturnType<typeof result>>();
		const newer = deferred<ReturnType<typeof result>>();
		searchAll.mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise);
		const view = render(SearchView, { q: 'ab' });
		await view.rerender({ q: 'abc' });
		newer.resolve(result([movie({ id: 'n', title: 'Newer result' })], []));
		expect(await screen.findByRole('link', { name: 'Newer result' })).toBeInTheDocument();
		older.resolve(result([movie({ id: 'o', title: 'Older result' })], []));
		await new Promise((r) => setTimeout(r, 20));
		expect(screen.queryByRole('link', { name: 'Older result' })).not.toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Newer result' })).toBeInTheDocument();
	});

	it('does not show results of the previous query while the next one loads', async () => {
		const second = deferred<ReturnType<typeof result>>();
		searchAll
			.mockResolvedValueOnce(result([movie({ title: 'First' })], []))
			.mockReturnValueOnce(second.promise);
		const view = render(SearchView, { q: 'aa' });
		await screen.findByRole('link', { name: 'First' });
		await view.rerender({ q: 'bb' });
		await waitFor(() =>
			expect(screen.queryByRole('link', { name: 'First' })).not.toBeInTheDocument()
		);
		expect(screen.getByRole('status')).toHaveTextContent('Searching…');
	});

	describe('Show more', () => {
		const first = result(
			[movie({ id: 'm1', title: 'One' })],
			[person({ id: 'p1', name: 'Ann' })],
			3,
			1
		);

		it('appends the next page of one group only and hides the button when exhausted', async () => {
			searchAll.mockResolvedValue(first);
			searchMovies.mockResolvedValue({
				items: [movie({ id: 'm2', title: 'Two' }), movie({ id: 'm3', title: 'Three' })],
				pageInfo: { page: 1, size: 10, totalElements: 3, totalPages: 2 }
			});
			const user = userEvent.setup();
			render(SearchView, { q: 'heat' });
			// only the movies group has more
			first.movies.pageInfo.totalPages = 2;
			const movies = await screen.findByRole('region', { name: /Movies/ });
			await user.click(within(movies).getByRole('button', { name: 'Show more' }));
			expect(await screen.findByRole('link', { name: 'Three' })).toBeInTheDocument();
			expect(searchMovies).toHaveBeenCalledWith('heat', 1);
			expect(searchPeople).not.toHaveBeenCalled();
			expect(within(movies).queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
			expect(
				screen.getAllByRole('link').filter((a) => a.getAttribute('href')?.startsWith('/movies/'))
			).toHaveLength(3);
		});

		it('skips results that are already shown when pages overlap', async () => {
			const overlapping = result([movie({ id: 'm1', title: 'One' })], [], 2, 0);
			overlapping.movies.pageInfo.totalPages = 2;
			searchAll.mockResolvedValue(overlapping);
			searchMovies.mockResolvedValue({
				items: [movie({ id: 'm1', title: 'One' }), movie({ id: 'm2', title: 'Two' })],
				pageInfo: { page: 1, size: 10, totalElements: 2, totalPages: 2 }
			});
			const user = userEvent.setup();
			render(SearchView, { q: 'heat' });
			await user.click(await screen.findByRole('button', { name: 'Show more' }));
			await screen.findByRole('link', { name: 'Two' });
			expect(screen.getAllByRole('link', { name: 'One' })).toHaveLength(1);
		});

		it('keeps the results and offers Retry when a page fails to load', async () => {
			const withMore = result([movie({ id: 'm1', title: 'One' })], [], 2, 0);
			withMore.movies.pageInfo.totalPages = 2;
			searchAll.mockResolvedValue(withMore);
			searchMovies.mockRejectedValueOnce(new ApiError('NETWORK', 'x')).mockResolvedValue({
				items: [movie({ id: 'm2', title: 'Two' })],
				pageInfo: { page: 1, size: 10, totalElements: 2, totalPages: 2 }
			});
			const user = userEvent.setup();
			render(SearchView, { q: 'heat' });
			await user.click(await screen.findByRole('button', { name: 'Show more' }));
			expect(await screen.findByRole('alert')).toHaveTextContent('Could not load more results');
			expect(screen.getByRole('link', { name: 'One' })).toBeInTheDocument();
			await user.click(screen.getByRole('button', { name: 'Retry' }));
			expect(await screen.findByRole('link', { name: 'Two' })).toBeInTheDocument();
			expect(screen.queryByRole('alert')).not.toBeInTheDocument();
		});
	});

	// ---- scope (S-702 AC2) ----

	it('Titles searches movies only and shows only the movie group', async () => {
		searchMovies.mockResolvedValue(paged([movie({ title: 'Heat' })], 12, 0, 10));
		render(SearchView, { q: 'heat', scope: 'titles' });
		expect(await screen.findByRole('link', { name: 'Heat' })).toBeInTheDocument();
		expect(searchMovies).toHaveBeenCalledWith('heat', 0, expect.anything());
		expect(searchAll).not.toHaveBeenCalled();
		expect(searchPeople).not.toHaveBeenCalled();
		expect(screen.getByRole('heading', { name: 'Movies (12)' })).toBeInTheDocument();
		expect(screen.queryByRole('heading', { name: /People/ })).not.toBeInTheDocument();
		expect(screen.getByRole('status')).toHaveTextContent('12 movies found for “heat”.');
	});

	it('People searches people only and shows only the people group', async () => {
		searchPeople.mockResolvedValue(paged([person()], 1, 0, 10));
		render(SearchView, { q: 'pacino', scope: 'people' });
		expect(await screen.findByRole('link', { name: 'Al Pacino' })).toBeInTheDocument();
		expect(searchPeople).toHaveBeenCalledWith('pacino', 0, expect.anything());
		expect(searchAll).not.toHaveBeenCalled();
		expect(screen.queryByRole('heading', { name: /Movies/ })).not.toBeInTheDocument();
		expect(screen.getByRole('status')).toHaveTextContent('1 people found for “pacino”.');
	});

	it('an empty scoped search shows the empty message', async () => {
		searchPeople.mockResolvedValue(paged([], 0, 0, 10));
		render(SearchView, { q: 'zzzz', scope: 'people' });
		expect(await screen.findByText('No results for “zzzz”.')).toBeInTheDocument();
	});

	it('changing the scope searches again and never shows the old scope’s answer', async () => {
		const late = deferred<ReturnType<typeof result>>();
		searchAll.mockReturnValueOnce(late.promise);
		searchMovies.mockResolvedValue(paged([movie({ title: 'Only movies' })], 1, 0, 10));
		const view = render(SearchView, { q: 'heat', scope: 'all' });
		await view.rerender({ q: 'heat', scope: 'titles' });
		expect(await screen.findByRole('link', { name: 'Only movies' })).toBeInTheDocument();
		late.resolve(result([movie({ title: 'From all' })], [person()]));
		await new Promise((r) => setTimeout(r, 20));
		expect(screen.queryByRole('link', { name: 'From all' })).not.toBeInTheDocument();
		expect(screen.queryByRole('heading', { name: /People/ })).not.toBeInTheDocument();
	});

	it('a too-short query sends no request in any scope', async () => {
		render(SearchView, { q: 'a', scope: 'titles' });
		expect(screen.getByRole('status')).toHaveTextContent('Enter between 2 and 100 characters');
		expect(searchMovies).not.toHaveBeenCalled();
	});
});
