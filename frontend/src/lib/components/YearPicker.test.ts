import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import YearPicker from './YearPicker.svelte';

const setup = (value = '', extra: Record<string, unknown> = {}) => {
	render(YearPicker, { label: 'From year', value, min: 1888, max: 2031, ...extra });
	return {
		user: userEvent.setup(),
		input: screen.getByLabelText('From year') as HTMLInputElement,
		toggle: screen.getByRole('button', { name: 'Choose from year' })
	};
};

describe('YearPicker (S-702 AC4 to AC6)', () => {
	it('keeps a labelled text field that can be typed in', async () => {
		const { user, input } = setup();
		await user.type(input, '1995');
		expect(input).toHaveValue('1995');
		expect(input).toHaveAttribute('inputmode', 'numeric');
	});

	it('opens a year grid on the selected year, with that year highlighted and focused', async () => {
		const { user, toggle } = setup('1995');
		expect(toggle).toHaveAttribute('aria-expanded', 'false');
		await user.click(toggle);
		expect(toggle).toHaveAttribute('aria-expanded', 'true');
		const dialog = screen.getByRole('dialog', { name: 'Choose from year' });
		expect(within(dialog).getByText('1980–1999')).toBeInTheDocument();
		const group = within(dialog).getByRole('group', { name: 'Years 1980 to 1999' });
		expect(within(group).getAllByRole('button')).toHaveLength(20);
		const year = within(group).getByRole('button', { name: '1995' });
		expect(year).toHaveAttribute('aria-pressed', 'true');
		expect(year).toHaveFocus();
	});

	it('opens on this year when empty; picking a year fills the field, closes and returns focus', async () => {
		const { user, input, toggle } = setup('', { max: 2100 });
		await user.click(toggle);
		const thisYear = new Date().getFullYear();
		expect(screen.getByRole('button', { name: String(thisYear) })).toHaveFocus();
		await user.click(screen.getByRole('button', { name: 'Earlier years' }));
		await user.click(screen.getByRole('button', { name: String(thisYear - 20) }));
		expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
		expect(input).toHaveValue(String(thisYear - 20));
		expect(toggle).toHaveFocus();
	});

	it('is keyboard-operable: arrows, rows, pages, Enter and Esc', async () => {
		const { user, input, toggle } = setup('1995');
		await user.click(toggle);
		await user.keyboard('{ArrowRight}');
		expect(screen.getByRole('button', { name: '1996' })).toHaveFocus();
		await user.keyboard('{ArrowDown}');
		expect(screen.getByRole('button', { name: '2000' })).toHaveFocus(); // moved to the next page
		expect(screen.getByText('2000–2019')).toBeInTheDocument();
		await user.keyboard('{PageUp}');
		expect(screen.getByRole('button', { name: '1980' })).toHaveFocus();
		await user.keyboard('{Enter}');
		expect(input).toHaveValue('1980');
		expect(toggle).toHaveFocus();

		await user.click(toggle);
		await user.keyboard('{Escape}');
		expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
		expect(toggle).toHaveFocus();
		expect(input).toHaveValue('1980');
	});

	it('does not offer years outside 1888 to the maximum, and stops paging there', async () => {
		const { user, toggle } = setup('1890');
		await user.click(toggle);
		const group = screen.getByRole('group', { name: 'Years 1888 to 1899' });
		expect(within(group).queryByRole('button', { name: '1887' })).not.toBeInTheDocument();
		expect(within(group).getAllByRole('button')).toHaveLength(12);
		const earlier = screen.getByRole('button', { name: 'Earlier years' });
		expect(earlier).toHaveAttribute('aria-disabled', 'true');
		await user.click(earlier);
		expect(screen.getByText('1888–1899')).toBeInTheDocument();
		await user.keyboard('{PageUp}');
		expect(screen.getByRole('button', { name: '1888' })).toHaveFocus(); // clamped
	});

	it('stops at the maximum year on the last page', async () => {
		const { user, toggle } = setup('2030');
		await user.click(toggle);
		expect(screen.getByText('2020–2031')).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: '2032' })).not.toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Later years' })).toHaveAttribute(
			'aria-disabled',
			'true'
		);
	});

	it('Clear empties the field and This year fills the current year', async () => {
		const { user, input, toggle } = setup('1995');
		await user.click(toggle);
		await user.click(screen.getByRole('button', { name: 'Clear' }));
		expect(input).toHaveValue('');
		await user.click(toggle);
		await user.click(screen.getByRole('button', { name: 'This year' }));
		expect(input).toHaveValue(String(new Date().getFullYear()));
	});

	it('closes on a click outside without changing the value', async () => {
		const { user, input, toggle } = setup('1995');
		await user.click(toggle);
		await user.click(document.body);
		expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
		expect(input).toHaveValue('1995');
	});

	it('shows an error tied to the field', () => {
		const { input } = setup('', { error: 'Enter a valid year range.' });
		expect(input).toHaveAttribute('aria-invalid', 'true');
		expect(input).toHaveAccessibleDescription('Enter a valid year range.');
	});
});
