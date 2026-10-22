import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { ApiError } from '#lib/api/client.js';
import * as api from '#lib/api/catalog.js';
import { toasts } from '#lib/stores/toast.svelte.js';
import MovieListView from './MovieListView.svelte';
import MovieDetailView from './MovieDetailView.svelte';
import MovieCreateView from './MovieCreateView.svelte';
import MovieEditView from './MovieEditView.svelte';
import { movie, movieDetail, paged } from './testing.js';

vi.mock('#lib/api/catalog.js', async (original) => ({
	...(await original<typeof import('#lib/api/catalog.js')>()),
	fetchMovies: vi.fn(),
	fetchMovie: vi.fn(),
	createMovie: vi.fn(),
	updateMovie: vi.fn(),
	deleteMovie: vi.fn(),
	removeArtwork: vi.fn(),
	fetchPeople: vi.fn(),
	addCredit: vi.fn(),
	updateCredit: vi.fn(),
	removeCredit: vi.fn()
}));
const goto = vi.hoisted(() => vi.fn());
vi.mock('$app/navigation', () => ({ goto }));

const fetchMovies = vi.mocked(api.fetchMovies);
const fetchMovie = vi.mocked(api.fetchMovie);

beforeEach(() => {
	vi.resetAllMocks();
	toasts.items.splice(0);
});

describe('MovieListView', () => {
	it('shows a loading state, then the movies with a placeholder for missing artwork', async () => {
		fetchMovies.mockResolvedValue(
			paged([movie(), movie({ id: 'm2', title: 'Ran', releaseYear: 1985, genres: [] })])
		);
		render(MovieListView);
		expect(screen.getByRole('status')).toHaveTextContent('Loading movies');
		expect(await screen.findByRole('link', { name: 'Heat' })).toHaveAttribute('href', '/movies/m1');
		expect(screen.getByText('1995 · Crime, Thriller')).toBeInTheDocument();
		expect(screen.getByText('1985')).toBeInTheDocument();
		expect(screen.getByRole('img', { name: 'No artwork for Ran' })).toBeInTheDocument();
	});

	it('shows posters in a list where each card has one link named by the title (S-701 AC2, AC4)', async () => {
		fetchMovies.mockResolvedValue(paged([movie(), movie({ id: 'm2', title: 'Ran' })], 128));
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		const items = screen.getAllByRole('listitem');
		expect(items).toHaveLength(2);
		for (const item of items) expect(within(item).getAllByRole('link')).toHaveLength(1);
		expect(screen.getByText('128 titles')).toBeInTheDocument();
		expect(screen.getAllByRole('link', { name: 'Add movie' })).toHaveLength(1);
	});

	it('shows an image with alt text when artwork exists', async () => {
		const artwork = {
			thumbnailUrl: '/artwork/m1/thumbnail?v=1',
			url: '/x',
			width: 10,
			height: 15,
			contentType: 'image/jpeg',
			sizeBytes: 1
		};
		fetchMovies.mockResolvedValue(paged([movie({ artwork })]));
		render(MovieListView);
		expect(await screen.findByRole('img', { name: 'Poster of Heat' })).toHaveAttribute(
			'src',
			artwork.thumbnailUrl
		);
	});

	it('shows a friendly empty state with an add action (EC-01)', async () => {
		fetchMovies.mockResolvedValue(paged([]));
		render(MovieListView);
		expect(await screen.findByText('There are no movies yet.')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Add the first movie' })).toHaveAttribute(
			'href',
			'/movies/new'
		);
	});

	it('shows an error with retry', async () => {
		fetchMovies
			.mockRejectedValueOnce(new ApiError('UNAVAILABLE', 'x'))
			.mockResolvedValue(paged([movie()]));
		render(MovieListView);
		expect(await screen.findByRole('alert')).toHaveTextContent('not available');
		await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
		expect(await screen.findByRole('link', { name: 'Heat' })).toBeInTheDocument();
	});

	it('applies genre, year and sort filters and can clear them', async () => {
		fetchMovies.mockResolvedValue(paged([movie()]));
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		await userEvent.click(screen.getByRole('checkbox', { name: 'Crime' }));
		await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'RELEASE_YEAR:DESC');
		await userEvent.type(screen.getByLabelText('From year'), '1990');
		await userEvent.type(screen.getByLabelText('To year'), '2000');
		await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({
					page: 0,
					genres: ['CRIME'],
					yearFrom: 1990,
					yearTo: 2000,
					sortField: 'RELEASE_YEAR',
					direction: 'DESC'
				}),
				expect.anything()
			)
		);
		await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ genres: [], yearFrom: null, sortField: 'TITLE' }),
				expect.anything()
			)
		);
		expect(screen.getByRole('checkbox', { name: 'Crime' })).not.toBeChecked();
	});

	it('applies a genre chip at once, keeps other applied filters and returns to page 1 (S-701 AC3)', async () => {
		fetchMovies.mockImplementation(async (p) => paged([movie()], 41, p.page));
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'RELEASE_YEAR:DESC');
		await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
		await userEvent.click(screen.getByRole('button', { name: /Next/ }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ page: 1 }),
				expect.anything()
			)
		);
		await userEvent.click(screen.getByRole('checkbox', { name: 'War' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ page: 0, genres: ['WAR'], sortField: 'RELEASE_YEAR' }),
				expect.anything()
			)
		);
		expect(screen.getByRole('checkbox', { name: 'War' })).toBeChecked();
	});

	it('selects several genres, shows ✕ on each, and Clear filters removes them all (S-702 AC7)', async () => {
		fetchMovies.mockResolvedValue(paged([movie()]));
		const user = userEvent.setup();
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		const group = screen.getByRole('group', { name: 'Genre' });
		expect(within(group).getAllByRole('checkbox')).toHaveLength(15);
		expect(within(group).queryAllByRole('checkbox', { checked: true })).toHaveLength(0);

		await user.click(screen.getByRole('checkbox', { name: 'Crime' }));
		await user.click(screen.getByRole('checkbox', { name: 'Comedy' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ page: 0, genres: ['COMEDY', 'CRIME'] }), // fixed order
				expect.anything()
			)
		);
		expect(screen.getByRole('checkbox', { name: 'Crime' }).closest('label')).toHaveTextContent('✕');

		await user.click(screen.getByRole('checkbox', { name: 'Crime' })); // clicking again removes it
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ genres: ['COMEDY'] }),
				expect.anything()
			)
		);

		screen.getByRole('checkbox', { name: 'Drama' }).focus();
		await user.keyboard(' ');
		expect(screen.getByRole('checkbox', { name: 'Drama' })).toBeChecked();

		await user.click(screen.getByRole('button', { name: 'Clear filters' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ genres: [] }),
				expect.anything()
			)
		);
		expect(within(group).queryAllByRole('checkbox', { checked: true })).toHaveLength(0);
		expect(screen.queryByText(/selected/)).not.toBeInTheDocument(); // no summary line (owner, 2026-10-22)
	});

	it('fills the year range from the year pickers, then Apply filters (S-702 AC6)', async () => {
		fetchMovies.mockResolvedValue(paged([movie()]));
		const user = userEvent.setup();
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		await user.type(screen.getByLabelText('From year'), '1995');
		await user.click(screen.getByRole('button', { name: 'Choose to year' }));
		await user.click(screen.getByRole('button', { name: 'Earlier years' }));
		await user.click(screen.getByRole('button', { name: '2005' }));
		expect(screen.getByLabelText('To year')).toHaveValue('2005');
		await user.click(screen.getByRole('button', { name: 'Apply' }));
		await waitFor(() =>
			expect(fetchMovies).toHaveBeenLastCalledWith(
				expect.objectContaining({ yearFrom: 1995, yearTo: 2005 }),
				expect.anything()
			)
		);
	});

	it('does not call the server for an invalid year range', async () => {
		fetchMovies.mockResolvedValue(paged([movie()]));
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		fetchMovies.mockClear();
		await userEvent.type(screen.getByLabelText('From year'), '2000');
		await userEvent.type(screen.getByLabelText('To year'), '1990');
		await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
		expect(await screen.findByText(/valid year range/)).toBeInTheDocument();
		expect(fetchMovies).not.toHaveBeenCalled();
	});

	it('says so when filters match nothing', async () => {
		fetchMovies.mockResolvedValueOnce(paged([movie()])).mockResolvedValue(paged([]));
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		await userEvent.click(screen.getByRole('checkbox', { name: 'War' }));
		expect(await screen.findByText('No movies match these filters.')).toBeInTheDocument();
	});

	it('pages forward and returns to the last valid page when a page is empty (EC-11)', async () => {
		fetchMovies.mockImplementation(async (p) =>
			p.page === 0
				? paged([movie()], 41, 0)
				: p.page === 5
					? paged([], 41, 5)
					: paged([movie({ id: 'm9', title: `Page ${p.page}` })], 41, p.page)
		);
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		await userEvent.click(screen.getByRole('button', { name: /Next/ }));
		expect(await screen.findByRole('link', { name: 'Page 1' })).toBeInTheDocument();
		expect(fetchMovies).toHaveBeenLastCalledWith(
			expect.objectContaining({ page: 1 }),
			expect.anything()
		);
	});

	it('moves back to the last page when the current one is empty', async () => {
		fetchMovies.mockImplementation(async (p) =>
			p.page === 0 ? paged([movie()], 41, 0) : paged([], 41, p.page)
		);
		render(MovieListView);
		await screen.findByRole('link', { name: 'Heat' });
		// jump to a page that no longer exists
		fetchMovies.mockImplementation(async (p) =>
			p.page === 1 ? paged([movie({ title: 'Back' })], 41, 1) : paged([], 41, p.page)
		);
		await userEvent.click(screen.getByRole('button', { name: /Next/ }));
		expect(await screen.findByRole('link', { name: 'Back' })).toBeInTheDocument();
	});
});

describe('MovieDetailView', () => {
	it('shows the movie and its credits', async () => {
		fetchMovie.mockResolvedValue(movieDetail());
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByRole('heading', { level: 1, name: /Heat/ })).toBeInTheDocument();
		expect(screen.getByText('Cops and robbers.')).toBeInTheDocument();
		expect(screen.getByText('170 min', { exact: false })).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Al Pacino' })).toHaveAttribute('href', '/people/p1');
		expect(screen.getByText('Actor as Vincent')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/movies/m1/edit');
	});

	it('opens with a hero band: back link, title, facts, genre pills and actions (S-701 AC6)', async () => {
		fetchMovie.mockResolvedValue(movieDetail({ creditCount: 12 }));
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByRole('heading', { level: 1, name: 'Heat' })).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Back to movies' })).toHaveAttribute('href', '/movies');
		expect(screen.getByText('1995 · 170 min · 12 credits')).toBeInTheDocument();
		const genres = within(screen.getByRole('list', { name: 'Genres' })).getAllByRole('listitem');
		expect(genres.map((g) => g.textContent)).toEqual(['Crime', 'Thriller']);
		expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
	});

	it('leaves out a missing runtime and says 1 credit (S-701 AC6)', async () => {
		fetchMovie.mockResolvedValue(movieDetail({ runtimeMinutes: null, genres: [] }));
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByText('1995 · 1 credit')).toBeInTheDocument();
		expect(screen.queryByRole('list', { name: 'Genres' })).not.toBeInTheDocument();
	});

	const withArtwork = movieDetail({
		artwork: {
			thumbnailUrl: '/artwork/m1/thumbnail?v=1',
			url: '/artwork/m1/original?v=1',
			width: 400,
			height: 600,
			contentType: 'image/png',
			sizeBytes: 10
		}
	});

	it('shows the original image, an uploader labelled Replace, and a Remove button when artwork exists', async () => {
		fetchMovie.mockResolvedValue(withArtwork);
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByRole('img', { name: 'Poster of Heat' })).toHaveAttribute(
			'src',
			'/artwork/m1/original?v=1'
		);
		expect(screen.getByRole('heading', { name: 'Replace artwork' })).toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Remove artwork' })).toBeInTheDocument();
	});

	it('offers Add artwork and no Remove button when there is none', async () => {
		fetchMovie.mockResolvedValue(movieDetail());
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByRole('heading', { name: 'Add artwork' })).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: 'Remove artwork' })).not.toBeInTheDocument();
	});

	it('removes artwork only after confirmation, then reloads the movie', async () => {
		fetchMovie.mockResolvedValueOnce(withArtwork).mockResolvedValue(movieDetail());
		vi.mocked(api.removeArtwork).mockResolvedValue(undefined);
		const user = userEvent.setup();
		render(MovieDetailView, { id: 'm1' });
		await user.click(await screen.findByRole('button', { name: 'Remove artwork' }));
		expect(api.removeArtwork).not.toHaveBeenCalled();
		const dialog = screen.getByRole('dialog');
		await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
		await waitFor(() => expect(api.removeArtwork).toHaveBeenCalledWith('m1'));
		expect(await screen.findByRole('img', { name: 'No artwork for Heat' })).toBeInTheDocument();
		expect(toasts.items.at(-1)?.message).toBe('Artwork removed.');
	});

	it('cancelling the confirmation keeps the artwork', async () => {
		fetchMovie.mockResolvedValue(withArtwork);
		const user = userEvent.setup();
		render(MovieDetailView, { id: 'm1' });
		await user.click(await screen.findByRole('button', { name: 'Remove artwork' }));
		await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
		expect(api.removeArtwork).not.toHaveBeenCalled();
		expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
	});

	it('a failed removal shows an error and still reloads to show the real state', async () => {
		fetchMovie.mockResolvedValue(withArtwork);
		vi.mocked(api.removeArtwork).mockRejectedValue(new ApiError('UNAVAILABLE', 'x'));
		const user = userEvent.setup();
		render(MovieDetailView, { id: 'm1' });
		await user.click(await screen.findByRole('button', { name: 'Remove artwork' }));
		await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }));
		await waitFor(() => expect(toasts.items.at(-1)?.message).toContain('not available'));
		expect(fetchMovie).toHaveBeenCalledTimes(2);
	});

	it('shows a not-found message when the movie was deleted', async () => {
		fetchMovie.mockRejectedValue(new ApiError('NOT_FOUND', 'x'));
		render(MovieDetailView, { id: 'gone' });
		expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
		expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Back to movies' })).toBeInTheDocument();
	});

	it('degrades credit names when people-service is down and unknown people (EC-15, BR-07)', async () => {
		fetchMovie.mockResolvedValue(
			movieDetail({
				credits: [
					{
						id: 'c1',
						role: 'ACTOR',
						characterName: null,
						personStatus: 'UNAVAILABLE',
						person: null,
						personId: 'p1'
					},
					{
						id: 'c2',
						role: 'DIRECTOR',
						characterName: null,
						personStatus: 'NOT_FOUND',
						person: null,
						personId: 'p2'
					}
				],
				creditCount: 2
			})
		);
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByText('Name unavailable')).toBeInTheDocument();
		expect(screen.getByText('Unknown person')).toBeInTheDocument();
		// S-701 AC7: both show as dashed cards that keep the role and the edit and remove actions.
		for (const name of ['Name unavailable', 'Unknown person']) {
			const card = screen.getByText(name).closest('li')!;
			expect(card).toHaveClass('missing');
			expect(
				within(card).getByRole('button', { name: `Remove credit of ${name}` })
			).toBeInTheDocument();
		}
		expect(screen.getByText('Director')).toBeInTheDocument();
		expect(screen.getByRole('status')).toHaveTextContent('Some names cannot be shown');
	});

	it('says when credits are capped (EC-14)', async () => {
		fetchMovie.mockResolvedValue(movieDetail({ creditsTruncated: true, creditCount: 350 }));
		render(MovieDetailView, { id: 'm1' });
		expect(await screen.findByText('Showing 1 of 350 credits.')).toBeInTheDocument();
	});

	it('asks for confirmation, deletes and goes back to the list', async () => {
		fetchMovie.mockResolvedValue(movieDetail());
		vi.mocked(api.deleteMovie).mockResolvedValue(undefined);
		render(MovieDetailView, { id: 'm1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		const dialog = screen.getByRole('dialog', { name: 'Delete movie' });
		expect(dialog).toHaveTextContent('credits and artwork are deleted too');
		expect(api.deleteMovie).not.toHaveBeenCalled();
		await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));
		await waitFor(() => expect(api.deleteMovie).toHaveBeenCalledWith('m1'));
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/movies'));
	});

	it('cancelling the confirmation deletes nothing', async () => {
		fetchMovie.mockResolvedValue(movieDetail());
		render(MovieDetailView, { id: 'm1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
		expect(api.deleteMovie).not.toHaveBeenCalled();
	});

	it('shows an error and keeps the page when deleting fails', async () => {
		fetchMovie.mockResolvedValue(movieDetail());
		vi.mocked(api.deleteMovie).mockRejectedValue(new ApiError('NOT_FOUND', 'x'));
		render(MovieDetailView, { id: 'm1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' })
		);
		await waitFor(() => expect(toasts.items[0]?.kind).toBe('error'));
		expect(goto).not.toHaveBeenCalled();
	});
});

describe('MovieCreateView and MovieEditView', () => {
	const fill = async (title: string, year: string) => {
		await userEvent.type(screen.getByLabelText(/Title/), title);
		await userEvent.type(screen.getByLabelText(/Release year/), year);
	};

	it('shows validation messages and does not submit invalid data', async () => {
		render(MovieCreateView);
		await userEvent.click(screen.getByRole('button', { name: 'Create movie' }));
		expect(
			await screen.findByText('Enter a title between 1 and 200 characters.')
		).toBeInTheDocument();
		expect(screen.getByText(/release year between 1888/)).toBeInTheDocument();
		expect(api.createMovie).not.toHaveBeenCalled();
	});

	it('creates a trimmed movie, with genres, and opens it', async () => {
		vi.mocked(api.createMovie).mockResolvedValue(movie({ id: 'new1' }));
		render(MovieCreateView);
		await fill('  Heat  ', '1995');
		await userEvent.click(screen.getByLabelText('Crime'));
		await userEvent.click(screen.getByRole('button', { name: 'Create movie' }));
		await waitFor(() =>
			expect(api.createMovie).toHaveBeenCalledWith({
				title: 'Heat',
				releaseYear: 1995,
				runtimeMinutes: null,
				synopsis: '',
				genres: ['CRIME']
			})
		);
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/movies/new1'));
	});

	it('keeps what the user typed and shows the server message on a duplicate', async () => {
		vi.mocked(api.createMovie).mockRejectedValue(
			new ApiError('CONFLICT', 'x', { fieldErrors: {}, details: { messageKey: 'movie.duplicate' } })
		);
		render(MovieCreateView);
		await fill('Heat', '1995');
		await userEvent.click(screen.getByRole('button', { name: 'Create movie' }));
		await waitFor(() => expect(toasts.items[0]?.kind).toBe('error'));
		expect(screen.getByLabelText(/Title/)).toHaveValue('Heat');
		expect(screen.getByLabelText(/Release year/)).toHaveValue('1995');
		expect(goto).not.toHaveBeenCalled();
	});

	it('shows server field errors next to the fields', async () => {
		vi.mocked(api.createMovie).mockRejectedValue(
			new ApiError('VALIDATION_FAILED', 'x', { fieldErrors: { title: 'movie.title.invalid' } })
		);
		render(MovieCreateView);
		await fill('x', '1995');
		await userEvent.click(screen.getByRole('button', { name: 'Create movie' }));
		expect(
			await screen.findByText('Enter a title between 1 and 200 characters.')
		).toBeInTheDocument();
	});

	it('ignores a second click while saving (EC-20)', async () => {
		let release: (m: ReturnType<typeof movie>) => void = () => {};
		vi.mocked(api.createMovie).mockReturnValue(new Promise((resolve) => (release = resolve)));
		render(MovieCreateView);
		await fill('Heat', '1995');
		const button = screen.getByRole('button', { name: 'Create movie' });
		await userEvent.click(button);
		await userEvent.click(screen.getByRole('button', { name: /Create movie/ }));
		expect(api.createMovie).toHaveBeenCalledTimes(1);
		release(movie());
	});

	it('edit loads the movie into the form and saves changes', async () => {
		fetchMovie.mockResolvedValue(movieDetail({ runtimeMinutes: null, genres: ['WAR'] }));
		vi.mocked(api.updateMovie).mockResolvedValue(movie());
		render(MovieEditView, { id: 'm1' });
		const title = await screen.findByLabelText(/Title/);
		expect(title).toHaveValue('Heat');
		expect(screen.getByLabelText('War')).toBeChecked();
		await userEvent.clear(title);
		await userEvent.type(title, 'Heat 2');
		await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
		await waitFor(() =>
			expect(api.updateMovie).toHaveBeenCalledWith(
				'm1',
				expect.objectContaining({ title: 'Heat 2', genres: ['WAR'] })
			)
		);
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/movies/m1'));
	});

	it('edit of a deleted movie shows not found', async () => {
		fetchMovie.mockRejectedValue(new ApiError('NOT_FOUND', 'x'));
		render(MovieEditView, { id: 'gone' });
		expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
	});
});
