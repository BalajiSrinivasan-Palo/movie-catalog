import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { ApiError } from '#lib/api/client.js';
import * as api from '#lib/api/catalog.js';
import { toasts } from '#lib/stores/toast.svelte.js';
import CreditsSection from './CreditsSection.svelte';
import { credit, paged, person } from './testing.js';

vi.mock('#lib/api/catalog.js', async (original) => ({
	...(await original<typeof import('#lib/api/catalog.js')>()),
	fetchPeople: vi.fn(),
	addCredit: vi.fn(),
	updateCredit: vi.fn(),
	removeCredit: vi.fn()
}));

beforeEach(() => {
	vi.resetAllMocks();
	toasts.items.splice(0);
});

const setup = (over: Partial<Parameters<typeof render<typeof CreditsSection>>[1]> = {}) => {
	const onchanged = vi.fn();
	render(CreditsSection, {
		movieId: 'm1',
		credits: [credit()],
		truncated: false,
		creditCount: 1,
		onchanged,
		...over
	});
	return { onchanged };
};

describe('CreditsSection', () => {
	it('searches for a person, adds an actor credit with a character and reloads', async () => {
		vi.mocked(api.fetchPeople).mockResolvedValue(paged([person({ id: 'p9', name: 'Ada' })]));
		vi.mocked(api.addCredit).mockResolvedValue(credit());
		const { onchanged } = setup({ credits: [], creditCount: 0 });
		await userEvent.click(screen.getByRole('button', { name: 'Add credit' }));
		await userEvent.type(screen.getByLabelText('Find a person by name'), 'ada');
		await userEvent.click(screen.getByRole('button', { name: 'Search people' }));
		await userEvent.type(await screen.findByLabelText('Character (optional)'), ' Lead ');
		await userEvent.click(screen.getByRole('button', { name: 'Add to movie' }));
		await waitFor(() =>
			expect(api.addCredit).toHaveBeenCalledWith('m1', {
				personId: 'p9',
				role: 'ACTOR',
				characterName: 'Lead'
			})
		);
		await waitFor(() => expect(onchanged).toHaveBeenCalled());
	});

	it('hides the character field for non-actors', async () => {
		vi.mocked(api.fetchPeople).mockResolvedValue(paged([person()]));
		setup();
		await userEvent.click(screen.getByRole('button', { name: 'Add credit' }));
		await userEvent.click(screen.getByRole('button', { name: 'Search people' }));
		await userEvent.selectOptions(await screen.findByLabelText('Role'), 'DIRECTOR');
		expect(screen.queryByLabelText('Character (optional)')).not.toBeInTheDocument();
	});

	it('offers to create a person when the search finds nobody', async () => {
		vi.mocked(api.fetchPeople).mockResolvedValue(paged([]));
		setup();
		await userEvent.click(screen.getByRole('button', { name: 'Add credit' }));
		await userEvent.click(screen.getByRole('button', { name: 'Search people' }));
		expect(await screen.findByRole('link', { name: 'Create a person first.' })).toHaveAttribute(
			'href',
			'/people/new'
		);
	});

	it('shows the server message for a duplicate credit and keeps the form open', async () => {
		vi.mocked(api.fetchPeople).mockResolvedValue(paged([person()]));
		vi.mocked(api.addCredit).mockRejectedValue(
			new ApiError('CONFLICT', 'x', { details: { messageKey: 'credit.duplicate' } })
		);
		const { onchanged } = setup();
		await userEvent.click(screen.getByRole('button', { name: 'Add credit' }));
		await userEvent.click(screen.getByRole('button', { name: 'Search people' }));
		await userEvent.click(await screen.findByRole('button', { name: 'Add to movie' }));
		await waitFor(() => expect(toasts.items[0]?.kind).toBe('error'));
		expect(screen.getByRole('button', { name: 'Add to movie' })).toBeInTheDocument();
		expect(onchanged).not.toHaveBeenCalled();
	});

	it('shows a field error when the person was deleted meanwhile', async () => {
		vi.mocked(api.fetchPeople).mockResolvedValue(paged([person()]));
		vi.mocked(api.addCredit).mockRejectedValue(
			new ApiError('VALIDATION_FAILED', 'x', {
				fieldErrors: { personId: 'credit.person.not_found' }
			})
		);
		setup();
		await userEvent.click(screen.getByRole('button', { name: 'Add credit' }));
		await userEvent.click(screen.getByRole('button', { name: 'Search people' }));
		await userEvent.click(await screen.findByRole('button', { name: 'Add to movie' }));
		expect(await screen.findByText('This person no longer exists.')).toBeInTheDocument();
	});

	it('edits a credit with validation', async () => {
		vi.mocked(api.updateCredit).mockResolvedValue(credit());
		const { onchanged } = setup();
		await userEvent.click(screen.getByRole('button', { name: 'Edit credit of Al Pacino' }));
		const dialog = screen.getByRole('dialog', { name: 'Edit credit' });
		await userEvent.selectOptions(within(dialog).getByLabelText('Role'), 'DIRECTOR');
		expect(within(dialog).queryByLabelText('Character (optional)')).not.toBeInTheDocument();
		await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
		await waitFor(() =>
			expect(api.updateCredit).toHaveBeenCalledWith('c1', { role: 'DIRECTOR', characterName: null })
		);
		await waitFor(() => expect(onchanged).toHaveBeenCalled());
	});

	it('removes a credit after confirmation', async () => {
		vi.mocked(api.removeCredit).mockResolvedValue(undefined);
		const { onchanged } = setup();
		await userEvent.click(screen.getByRole('button', { name: 'Remove credit of Al Pacino' }));
		expect(api.removeCredit).not.toHaveBeenCalled();
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' })
		);
		await waitFor(() => expect(api.removeCredit).toHaveBeenCalledWith('c1'));
		await waitFor(() => expect(onchanged).toHaveBeenCalled());
	});

	it('refreshes the list when removing a credit that is already gone', async () => {
		vi.mocked(api.removeCredit).mockRejectedValue(new ApiError('NOT_FOUND', 'x'));
		const { onchanged } = setup();
		await userEvent.click(screen.getByRole('button', { name: 'Remove credit of Al Pacino' }));
		await userEvent.click(
			within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' })
		);
		await waitFor(() => expect(onchanged).toHaveBeenCalled());
		expect(toasts.items[0]?.kind).toBe('error');
	});
});
