import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { toasts } from './toast.svelte';

describe('toast store', () => {
	beforeEach(() => vi.useFakeTimers());
	afterEach(() => {
		toasts.clear();
		vi.useRealTimers();
	});

	it('adds a success toast that disappears after the timeout', () => {
		toasts.success('Saved');
		expect(toasts.items).toHaveLength(1);
		expect(toasts.items[0]).toMatchObject({ kind: 'success', message: 'Saved' });
		vi.advanceTimersByTime(4999);
		expect(toasts.items).toHaveLength(1);
		vi.advanceTimersByTime(2);
		expect(toasts.items).toHaveLength(0);
	});

	it('keeps error toasts until dismissed', () => {
		const id = toasts.error('Failed');
		vi.advanceTimersByTime(60_000);
		expect(toasts.items).toHaveLength(1);
		toasts.dismiss(id);
		expect(toasts.items).toHaveLength(0);
	});

	it('gives every toast a unique id and dismisses only the requested one', () => {
		const a = toasts.info('a');
		const b = toasts.info('b');
		expect(a).not.toBe(b);
		toasts.dismiss(a);
		expect(toasts.items.map((t) => t.message)).toEqual(['b']);
	});

	it('ignores dismissing an unknown id', () => {
		toasts.info('a');
		toasts.dismiss(9999);
		expect(toasts.items).toHaveLength(1);
	});

	it('honours a custom timeout', () => {
		toasts.push('info', 'quick', 100);
		vi.advanceTimersByTime(101);
		expect(toasts.items).toHaveLength(0);
	});
});
