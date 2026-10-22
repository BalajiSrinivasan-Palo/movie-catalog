import { describe, expect, it, vi } from 'vitest';
import { createAction } from './action.svelte';

describe('createAction', () => {
	it('moves idle, pending, success and returns the result', async () => {
		let resolveFn!: (v: number) => void;
		const action = createAction(() => new Promise<number>((r) => (resolveFn = r)));
		expect(action.state).toBe('idle');
		const promise = action.run();
		expect(action.state).toBe('pending');
		expect(action.pending).toBe(true);
		resolveFn(42);
		expect(await promise).toBe(42);
		expect(action.state).toBe('success');
		expect(action.error).toBeNull();
	});

	it('moves to error and keeps the error', async () => {
		const failure = new Error('boom');
		const action = createAction(async () => {
			throw failure;
		});
		expect(await action.run()).toBeUndefined();
		expect(action.state).toBe('error');
		expect(action.error).toBe(failure);
	});

	it('ignores a second call while pending so a double click sends one request', async () => {
		let resolveFn!: () => void;
		const fn = vi.fn(() => new Promise<void>((r) => (resolveFn = r)));
		const action = createAction(fn);
		const first = action.run();
		const second = await action.run();
		expect(second).toBeUndefined();
		expect(fn).toHaveBeenCalledTimes(1);
		resolveFn();
		await first;
	});

	it('allows retry after an error and clears the previous error', async () => {
		const fn = vi.fn().mockRejectedValueOnce(new Error('first')).mockResolvedValueOnce('ok');
		const action = createAction(fn);
		await action.run();
		expect(action.state).toBe('error');
		expect(await action.run()).toBe('ok');
		expect(action.state).toBe('success');
		expect(action.error).toBeNull();
	});

	it('passes arguments through and can be reset', async () => {
		const fn = vi.fn(async (a: number, b: number) => a + b);
		const action = createAction(fn);
		expect(await action.run(2, 3)).toBe(5);
		expect(fn).toHaveBeenCalledWith(2, 3);
		action.reset();
		expect(action.state).toBe('idle');
	});
});
