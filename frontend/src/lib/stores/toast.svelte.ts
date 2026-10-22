export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
	id: number;
	kind: ToastKind;
	message: string;
}

const DEFAULT_TIMEOUT_MS = 5000;

/** App-wide toast list. Errors stay until dismissed; others disappear after a timeout. */
class ToastStore {
	items = $state<Toast[]>([]);
	#nextId = 1;
	#timers = new Map<number, ReturnType<typeof setTimeout>>();

	push(kind: ToastKind, message: string, timeoutMs?: number): number {
		const id = this.#nextId++;
		this.items.push({ id, kind, message });
		const ms = timeoutMs ?? (kind === 'error' ? 0 : DEFAULT_TIMEOUT_MS);
		if (ms > 0)
			this.#timers.set(
				id,
				setTimeout(() => this.dismiss(id), ms)
			);
		return id;
	}

	success = (message: string) => this.push('success', message);
	error = (message: string) => this.push('error', message);
	info = (message: string) => this.push('info', message);

	dismiss(id: number): void {
		const timer = this.#timers.get(id);
		if (timer) clearTimeout(timer);
		this.#timers.delete(id);
		this.items = this.items.filter((t) => t.id !== id);
	}

	clear(): void {
		[...this.items].forEach((t) => this.dismiss(t.id));
	}
}

export const toasts = new ToastStore();
