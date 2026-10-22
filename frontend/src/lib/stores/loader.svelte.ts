export type LoadState = 'loading' | 'ready' | 'error';

/**
 * Loads data for a view. Every `load()` cancels the previous request and ignores a late answer, so
 * a slow earlier response can never replace a newer one (EC-13). A failed reload keeps the last data.
 */
export function createLoader<T>(fetcher: (signal: AbortSignal) => Promise<T>) {
	let state = $state<LoadState>('loading');
	let data = $state<T | null>(null);
	let error = $state<unknown>(null);
	let controller: AbortController | null = null;
	let counter = 0;

	async function load(): Promise<void> {
		controller?.abort();
		const mine = ++counter;
		controller = new AbortController();
		state = 'loading';
		error = null;
		try {
			const result = await fetcher(controller.signal);
			if (mine !== counter) return;
			data = result;
			state = 'ready';
		} catch (e) {
			if (mine !== counter) return; // superseded or cancelled: not an error
			error = e;
			state = 'error';
		}
	}

	function cancel() {
		counter++;
		controller?.abort();
	}

	return {
		get state() {
			return state;
		},
		get data() {
			return data;
		},
		get error() {
			return error;
		},
		load,
		cancel
	};
}
