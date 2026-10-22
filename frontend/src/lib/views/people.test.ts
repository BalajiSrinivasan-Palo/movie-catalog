import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { ApiError } from '#lib/api/client.js';
import * as api from '#lib/api/catalog.js';
import { toasts } from '#lib/stores/toast.svelte.js';
import PeopleListView from './PeopleListView.svelte';
import PersonDetailView from './PersonDetailView.svelte';
import PersonCreateView from './PersonCreateView.svelte';
import PersonEditView from './PersonEditView.svelte';
import { paged, person, personDetail } from './testing.js';

vi.mock('#lib/api/catalog.js', async (original) => ({
	...(await original<typeof import('#lib/api/catalog.js')>()),
	fetchPeople: vi.fn(),
	fetchPerson: vi.fn(),
	createPerson: vi.fn(),
	updatePerson: vi.fn(),
	deletePerson: vi.fn()
}));
const goto = vi.hoisted(() => vi.fn());
vi.mock('$app/navigation', () => ({ goto }));

const fetchPeople = vi.mocked(api.fetchPeople);
const fetchPerson = vi.mocked(api.fetchPerson);

beforeEach(() => {
	vi.resetAllMocks();
	toasts.items.splice(0);
});

describe('PeopleListView', () => {
	it('lists people with links and birth dates', async () => {
		fetchPeople.mockResolvedValue(
			paged([person(), person({ id: 'p2', name: 'Robert De Niro', birthDate: null })])
		);
		render(PeopleListView);
		expect(screen.getByRole('status')).toHaveTextContent('Loading people');
		expect(await screen.findByRole('link', { name: 'Al Pacino' })).toHaveAttribute(
			'href',
			'/people/p1'
		);
		expect(screen.getByText('born 1940-04-25')).toBeInTheDocument();
		expect(screen.getByText('2 people')).toBeInTheDocument();
	});

	it('shows each person as a card with initials and a single link (S-701 AC5)', async () => {
		fetchPeople.mockResolvedValue(paged([person(), person({ id: 'p2', name: 'Cher' })]));
		render(PeopleListView);
		await screen.findByRole('link', { name: 'Al Pacino' });
		const items = screen.getAllByRole('listitem');
		expect(items).toHaveLength(2);
		expect(within(items[0]).getByText('AP')).toHaveAttribute('aria-hidden', 'true');
		expect(within(items[1]).getByText('C')).toBeInTheDocument();
		for (const item of items) expect(within(item).getAllByRole('link')).toHaveLength(1);
	});

	it('shows the empty state and the filtered empty state', async () => {
		fetchPeople.mockResolvedValue(paged([]));
		render(PeopleListView);
		expect(await screen.findByText('There are no people yet.')).toBeInTheDocument();
		await userEvent.type(screen.getByLabelText('Name contains'), 'zzz');
		await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
		expect(await screen.findByText('No people match “zzz”.')).toBeInTheDocument();
		await userEvent.click(screen.getAllByRole('button', { name: 'Clear filter' })[0]);
		expect(await screen.findByText('There are no people yet.')).toBeInTheDocument();
	});

	it('filters by name and sorts Z to A', async () => {
		fetchPeople.mockResolvedValue(paged([person()]));
		render(PeopleListView);
		await screen.findByRole('link', { name: 'Al Pacino' });
		await userEvent.type(screen.getByLabelText('Name contains'), '  al ');
		await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'DESC');
		await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
		await waitFor(() =>
			expect(fetchPeople).toHaveBeenLastCalledWith(
				expect.objectContaining({ query: 'al', direction: 'DESC', page: 0 }),
				expect.anything()
			)
		);
	});

	it('shows an error with retry and can page forward', async () => {
		fetchPeople
			.mockRejectedValueOnce(new ApiError('NETWORK', 'x'))
			.mockResolvedValue(paged([person()], 45, 0));
		render(PeopleListView);
		await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));
		await screen.findByRole('link', { name: 'Al Pacino' });
		fetchPeople.mockResolvedValue(paged([person({ name: 'Second Page' })], 45, 1));
		await userEvent.click(screen.getByRole('button', { name: /Next/ }));
		expect(await screen.findByRole('link', { name: 'Second Page' })).toBeInTheDocument();
	});
});

describe('PersonDetailView', () => {
	it('shows details and filmography', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		render(PersonDetailView, { id: 'p1' });
		expect(await screen.findByRole('heading', { level: 1, name: 'Al Pacino' })).toBeInTheDocument();
		expect(screen.getByText('Born 1940-04-25')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Heat' })).toHaveAttribute('href', '/movies/m1');
		expect(screen.getByText(/Actor as Vincent/)).toBeInTheDocument();
	});

	it('shows a hero band and the filmography as poster cards (S-701 AC8)', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		render(PersonDetailView, { id: 'p1' });
		await screen.findByRole('heading', { level: 1, name: 'Al Pacino' });
		expect(screen.getByRole('link', { name: 'Back to people' })).toHaveAttribute('href', '/people');
		expect(screen.getByText('AP')).toHaveAttribute('aria-hidden', 'true');
		expect(screen.getByRole('heading', { level: 3, name: 'Heat' })).toBeInTheDocument();
		expect(screen.getByText('1995 · Actor as Vincent')).toBeInTheDocument();
		expect(screen.getByRole('img', { name: 'No artwork for Heat' })).toBeInTheDocument();
	});

	it('shows a placeholder when there are no credits and no bio', async () => {
		fetchPerson.mockResolvedValue(
			personDetail({ bio: '', birthDate: null, filmography: paged([]) })
		);
		render(PersonDetailView, { id: 'p1' });
		expect(await screen.findByText('No credits yet.')).toBeInTheDocument();
		expect(screen.getByText('No biography.')).toBeInTheDocument();
	});

	it('shows not found for a deleted person', async () => {
		fetchPerson.mockRejectedValue(new ApiError('NOT_FOUND', 'x'));
		render(PersonDetailView, { id: 'gone' });
		expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
	});

	it('deletes after confirmation and returns to the list', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		vi.mocked(api.deletePerson).mockResolvedValue(undefined);
		render(PersonDetailView, { id: 'p1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' })
		);
		await waitFor(() => expect(api.deletePerson).toHaveBeenCalledWith('p1'));
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/people'));
	});

	it('explains a blocked delete and lists the blocking movies (D8)', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		vi.mocked(api.deletePerson).mockRejectedValue(
			new ApiError('CONFLICT', 'x', {
				details: {
					messageKey: 'person.has_credits',
					blockingMovies: [{ id: 'm1', title: 'Heat', releaseYear: 1995 }]
				}
			})
		);
		render(PersonDetailView, { id: 'p1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' })
		);
		const alert = await screen.findByText(/still has credits/, { selector: 'p' });
		expect(alert).toBeInTheDocument();
		expect(screen.getAllByRole('link', { name: 'Heat (1995)' })[0]).toHaveAttribute(
			'href',
			'/movies/m1'
		);
		expect(goto).not.toHaveBeenCalled();
	});

	it('shows a generic error when deletion fails for another reason', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		vi.mocked(api.deletePerson).mockRejectedValue(new ApiError('UNAVAILABLE', 'x'));
		render(PersonDetailView, { id: 'p1' });
		await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' })
		);
		await waitFor(() => expect(toasts.items[0]?.message).toContain('not available'));
	});
});

describe('PersonCreateView and PersonEditView', () => {
	it('validates before sending', async () => {
		render(PersonCreateView);
		await userEvent.type(screen.getByLabelText('Birth date'), '2999-01-01');
		await userEvent.click(screen.getByRole('button', { name: 'Create person' }));
		expect(
			await screen.findByText('Enter a name between 1 and 200 characters.')
		).toBeInTheDocument();
		expect(screen.getByText(/valid birth date/)).toBeInTheDocument();
		expect(api.createPerson).not.toHaveBeenCalled();
	});

	it('creates a person and opens the page', async () => {
		vi.mocked(api.createPerson).mockResolvedValue(person({ id: 'new' }));
		render(PersonCreateView);
		await userEvent.type(screen.getByLabelText(/Name/), '  Ada ');
		await userEvent.type(screen.getByLabelText('Birth date'), '1906-12-09');
		await userEvent.click(screen.getByRole('button', { name: 'Create person' }));
		await waitFor(() =>
			expect(api.createPerson).toHaveBeenCalledWith({
				name: 'Ada',
				birthDate: '1906-12-09',
				bio: ''
			})
		);
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/people/new'));
	});

	it('keeps input and shows the server error when saving fails', async () => {
		vi.mocked(api.createPerson).mockRejectedValue(
			new ApiError('VALIDATION_FAILED', 'x', { fieldErrors: { name: 'person.name.invalid' } })
		);
		render(PersonCreateView);
		await userEvent.type(screen.getByLabelText(/Name/), 'Ada');
		await userEvent.click(screen.getByRole('button', { name: 'Create person' }));
		expect(
			await screen.findByText('Enter a name between 1 and 200 characters.')
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Name/)).toHaveValue('Ada');
	});

	it('edit prefills and saves', async () => {
		fetchPerson.mockResolvedValue(personDetail());
		vi.mocked(api.updatePerson).mockResolvedValue(person());
		render(PersonEditView, { id: 'p1' });
		const name = await screen.findByLabelText(/Name/);
		expect(name).toHaveValue('Al Pacino');
		expect(screen.getByLabelText('Birth date')).toHaveValue('1940-04-25');
		await userEvent.clear(name);
		await userEvent.type(name, 'Alfredo');
		await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
		await waitFor(() =>
			expect(api.updatePerson).toHaveBeenCalledWith(
				'p1',
				expect.objectContaining({ name: 'Alfredo' })
			)
		);
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/people/p1'));
	});
});
