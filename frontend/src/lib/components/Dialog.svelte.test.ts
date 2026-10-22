import { describe, it, expect, vi } from 'vitest';
import { createRawSnippet } from 'svelte';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import Dialog from './Dialog.svelte';

const body = createRawSnippet(() => ({ render: () => '<p>Delete this movie?</p>' }));
const actions = createRawSnippet(() => ({
	render: () =>
		'<span><button type="button">Cancel</button><button type="button">Delete</button></span>'
}));

describe('Dialog', () => {
	it('is a labelled modal dialog', () => {
		render(Dialog, { title: 'Confirm delete', onclose: () => {}, children: body, actions });
		const dialog = screen.getByRole('dialog', { name: 'Confirm delete' });
		expect(dialog).toHaveAttribute('aria-modal', 'true');
		expect(screen.getByText('Delete this movie?')).toBeInTheDocument();
	});

	it('moves focus to the first control inside the dialog', () => {
		render(Dialog, { title: 'Confirm', onclose: () => {}, children: body, actions });
		expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
	});

	it('closes on Escape', async () => {
		const onclose = vi.fn();
		render(Dialog, { title: 'Confirm', onclose, children: body, actions });
		await userEvent.keyboard('{Escape}');
		expect(onclose).toHaveBeenCalledTimes(1);
	});

	it('keeps Tab and Shift+Tab inside the dialog', async () => {
		render(Dialog, { title: 'Confirm', onclose: () => {}, children: body, actions });
		const cancel = screen.getByRole('button', { name: 'Cancel' });
		const del = screen.getByRole('button', { name: 'Delete' });
		await userEvent.tab();
		expect(del).toHaveFocus();
		await userEvent.tab();
		expect(cancel).toHaveFocus();
		await userEvent.tab({ shift: true });
		expect(del).toHaveFocus();
	});

	it('gives focus back to the opener when it closes', () => {
		const opener = document.createElement('button');
		document.body.append(opener);
		opener.focus();
		const { unmount } = render(Dialog, {
			title: 'Confirm',
			onclose: () => {},
			children: body,
			actions
		});
		expect(opener).not.toHaveFocus();
		unmount();
		expect(opener).toHaveFocus();
		opener.remove();
	});

	it('works without actions and focuses the dialog itself', () => {
		render(Dialog, { title: 'Info', onclose: () => {}, children: body });
		expect(screen.getByRole('dialog', { name: 'Info' })).toHaveFocus();
	});
});
