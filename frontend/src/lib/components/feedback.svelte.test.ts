import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import Skeleton from './Skeleton.svelte';
import ProgressBar from './ProgressBar.svelte';
import Pagination from './Pagination.svelte';
import ToastRegion from './ToastRegion.svelte';
import { toasts } from '#lib/stores/toast.svelte.js';

describe('Skeleton', () => {
	it('is a status region that announces loading', () => {
		render(Skeleton, { label: 'Loading movies' });
		expect(screen.getByRole('status')).toHaveTextContent('Loading movies');
	});

	it('renders the requested number of placeholder lines', () => {
		const { container } = render(Skeleton, { lines: 5 });
		expect(container.querySelectorAll('.line')).toHaveLength(5);
	});
});

describe('ProgressBar', () => {
	it('exposes value, range and label as a progressbar', () => {
		render(ProgressBar, { value: 42, label: 'Uploading poster' });
		const bar = screen.getByRole('progressbar', { name: 'Uploading poster' });
		expect(bar).toHaveAttribute('aria-valuenow', '42');
		expect(bar).toHaveAttribute('aria-valuemin', '0');
		expect(bar).toHaveAttribute('aria-valuemax', '100');
	});

	it.each([
		[-5, '0'],
		[150, '100'],
		[33.6, '34']
	])('clamps and rounds %s to %s', (input, expected) => {
		render(ProgressBar, { value: input, label: 'p' });
		expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', expected);
	});

	it('has no value when indeterminate', () => {
		render(ProgressBar, { label: 'Processing' });
		expect(screen.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow');
	});
});

describe('Pagination', () => {
	it('renders nothing for a single page', () => {
		const { container } = render(Pagination, { page: 0, totalPages: 1, onchange: () => {} });
		expect(container.querySelector('nav')).toBeNull();
	});

	it('shows position and disables Previous on the first page', () => {
		render(Pagination, { page: 0, totalPages: 3, onchange: () => {} });
		expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
		expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
	});

	it('disables Next on the last page', () => {
		render(Pagination, { page: 2, totalPages: 3, onchange: () => {} });
		expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
	});

	it('reports the requested page', async () => {
		const onchange = vi.fn();
		render(Pagination, { page: 1, totalPages: 3, onchange });
		await userEvent.click(screen.getByRole('button', { name: 'Next' }));
		await userEvent.click(screen.getByRole('button', { name: 'Previous' }));
		expect(onchange).toHaveBeenNthCalledWith(1, 2);
		expect(onchange).toHaveBeenNthCalledWith(2, 0);
	});

	it('keeps both controls focusable but inert while busy', async () => {
		const onchange = vi.fn();
		render(Pagination, { page: 1, totalPages: 3, onchange, busy: true });
		const prev = screen.getByRole('button', { name: /Previous/ });
		const next = screen.getByRole('button', { name: /Next/ });
		expect(prev).toHaveAttribute('aria-disabled', 'true');
		expect(next).toHaveAttribute('aria-disabled', 'true');
		await userEvent.click(next);
		expect(onchange).not.toHaveBeenCalled();
	});
});

describe('ToastRegion', () => {
	afterEach(() => toasts.clear());

	it('announces status messages politely and errors as alerts', async () => {
		render(ToastRegion);
		toasts.success('Movie saved');
		toasts.error('Could not save');
		expect(await screen.findByRole('status')).toHaveTextContent('Movie saved');
		expect(await screen.findByRole('alert')).toHaveTextContent('Could not save');
	});

	it('lets the user dismiss a message', async () => {
		render(ToastRegion);
		toasts.error('Could not save');
		await userEvent.click(await screen.findByRole('button', { name: 'Dismiss message' }));
		expect(screen.queryByText('Could not save')).not.toBeInTheDocument();
	});
});
