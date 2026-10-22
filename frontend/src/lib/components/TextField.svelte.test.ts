import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import TextField from './TextField.svelte';
import TextArea from './TextArea.svelte';
import Select from './Select.svelte';

describe('TextField', () => {
	it('connects the label to the input', () => {
		render(TextField, { label: 'Title' });
		expect(screen.getByLabelText('Title')).toBeInTheDocument();
	});

	it('shows the error, marks the input invalid and links them for screen readers', () => {
		render(TextField, { label: 'Title', error: 'Enter a title.' });
		const input = screen.getByLabelText('Title');
		expect(input).toHaveAttribute('aria-invalid', 'true');
		const message = screen.getByText('Enter a title.');
		expect(input.getAttribute('aria-describedby')).toContain(message.id);
	});

	it('links both hint and error and has no aria-describedby when neither exists', () => {
		const { unmount } = render(TextField, { label: 'A' });
		expect(screen.getByLabelText('A')).not.toHaveAttribute('aria-describedby');
		unmount();
		render(TextField, { label: 'B', hint: 'Max 200', error: 'Too long' });
		const described = screen.getByLabelText('B').getAttribute('aria-describedby')!.split(' ');
		expect(described).toHaveLength(2);
	});

	it('marks required fields visibly and for the browser', () => {
		render(TextField, { label: 'Name', required: true });
		expect(screen.getByLabelText(/Name/)).toBeRequired();
	});

	it('accepts typed input', async () => {
		render(TextField, { label: 'Title' });
		const input = screen.getByLabelText('Title');
		await userEvent.type(input, 'Heat');
		expect(input).toHaveValue('Heat');
	});
});

describe('TextArea', () => {
	it('is labelled, shows errors and keeps typed text', async () => {
		render(TextArea, { label: 'Synopsis', error: 'Too long' });
		const area = screen.getByLabelText('Synopsis');
		expect(area).toHaveAttribute('aria-invalid', 'true');
		await userEvent.type(area, 'Hello');
		expect(area).toHaveValue('Hello');
	});
});

describe('Select', () => {
	const options = [
		{ value: 'A', label: 'Action' },
		{ value: 'D', label: 'Drama' }
	];

	it('lists options with an optional empty placeholder and lets the user choose', async () => {
		render(Select, { label: 'Genre', options, placeholder: 'Any genre' });
		const select = screen.getByLabelText('Genre');
		expect(screen.getAllByRole('option')).toHaveLength(3);
		await userEvent.selectOptions(select, 'Drama');
		expect(select).toHaveValue('D');
	});

	it('omits the placeholder when none is given and shows errors', () => {
		render(Select, { label: 'Role', options, error: 'Choose a role.' });
		expect(screen.getAllByRole('option')).toHaveLength(2);
		expect(screen.getByLabelText('Role')).toHaveAttribute('aria-invalid', 'true');
		expect(screen.getByText('Choose a role.')).toBeInTheDocument();
	});
});
