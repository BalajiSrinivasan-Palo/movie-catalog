import { describe, it, expect, vi } from 'vitest';
import { createRawSnippet } from 'svelte';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import Button from './Button.svelte';

const label = (text: string) => createRawSnippet(() => ({ render: () => `<span>${text}</span>` }));

describe('Button', () => {
	it('renders a button of type button by default and fires onclick', async () => {
		const onclick = vi.fn();
		render(Button, { children: label('Save'), onclick });
		const button = screen.getByRole('button', { name: 'Save' });
		expect(button).toHaveAttribute('type', 'button');
		await userEvent.click(button);
		expect(onclick).toHaveBeenCalledTimes(1);
	});

	it('is disabled and announces work while pending, and ignores clicks', async () => {
		const onclick = vi.fn();
		render(Button, { children: label('Save'), pending: true, onclick });
		const button = screen.getByRole('button', { name: /Save/ });
		expect(button).toHaveAttribute('aria-disabled', 'true');
		expect(button).not.toBeDisabled();
		expect(button).toHaveAttribute('aria-busy', 'true');
		expect(screen.getByText('Working…')).toBeInTheDocument();
		await userEvent.click(button);
		expect(onclick).not.toHaveBeenCalled();
	});

	it('is operable with the keyboard', async () => {
		const onclick = vi.fn();
		render(Button, { children: label('Go'), onclick });
		await userEvent.tab();
		expect(screen.getByRole('button', { name: 'Go' })).toHaveFocus();
		await userEvent.keyboard('{Enter}');
		await userEvent.keyboard(' ');
		expect(onclick).toHaveBeenCalledTimes(2);
	});

	it('supports submit type and the disabled flag', () => {
		render(Button, { children: label('Send'), type: 'submit', disabled: true });
		const button = screen.getByRole('button', { name: 'Send' });
		expect(button).toHaveAttribute('type', 'submit');
		expect(button).toBeDisabled();
	});
});
