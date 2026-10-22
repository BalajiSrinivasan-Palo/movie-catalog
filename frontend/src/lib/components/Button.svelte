<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends Omit<HTMLButtonAttributes, 'children'> {
		variant?: 'primary' | 'secondary' | 'danger';
		/** While pending the button is disabled and announces that it is working (NFR-UX-04, EC-20). */
		pending?: boolean;
		children: Snippet;
	}

	let {
		variant = 'primary',
		pending = false,
		disabled = false,
		type = 'button',
		children,
		onclick,
		...rest
	}: Props = $props();

	// While pending the button stays focusable (aria-disabled, not disabled) so focus is not lost
	// and screen readers still announce "Working…"; clicks are swallowed so a double submit cannot happen.
	function handleClick(event: MouseEvent & { currentTarget: EventTarget & HTMLButtonElement }) {
		if (pending) {
			event.preventDefault();
			return;
		}
		(onclick as ((e: typeof event) => void) | null | undefined)?.(event);
	}
</script>

<button
	{...rest}
	{type}
	class={['btn', variant]}
	{disabled}
	aria-disabled={pending ? 'true' : undefined}
	aria-busy={pending}
	onclick={handleClick}
>
	{@render children()}
	{#if pending}
		<span class="spinner" aria-hidden="true"></span>
		<span class="visually-hidden">Working…</span>
	{/if}
</button>

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: var(--touch-target);
		min-width: var(--touch-target);
		padding: 0 var(--space-3);
		border: 2px solid transparent;
		border-radius: var(--radius);
		font: inherit;
		font-weight: 600;
		cursor: pointer;
	}
	.primary {
		background: var(--color-primary);
		color: var(--color-primary-text);
	}
	.secondary {
		background: var(--color-bg);
		color: var(--color-primary);
		border-color: var(--color-primary);
	}
	.danger {
		background: var(--color-danger);
		color: var(--color-primary-text);
	}
	.btn:disabled,
	.btn[aria-disabled='true'] {
		opacity: 0.6;
		cursor: not-allowed;
	}
	.spinner {
		width: 1em;
		height: 1em;
		border: 2px solid currentColor;
		border-right-color: transparent;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
</style>
