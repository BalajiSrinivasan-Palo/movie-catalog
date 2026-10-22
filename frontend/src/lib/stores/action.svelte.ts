export type ActionState = 'idle' | 'pending' | 'success' | 'error';

/**
 * Wraps an async operation in the state machine every action uses (idle | pending | success | error).
 * A second call while pending is ignored, so a double click sends one request (EC-20).
 */
export function createAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
	let state = $state<ActionState>('idle');
	let error = $state<unknown>(null);

	async function run(...args: A): Promise<R | undefined> {
		if (state === 'pending') return undefined;
		state = 'pending';
		error = null;
		try {
			const result = await fn(...args);
			state = 'success';
			return result;
		} catch (e) {
			error = e;
			state = 'error';
			return undefined;
		}
	}

	function reset() {
		state = 'idle';
		error = null;
	}

	return {
		get state() {
			return state;
		},
		get pending() {
			return state === 'pending';
		},
		get error() {
			return error;
		},
		run,
		reset
	};
}
