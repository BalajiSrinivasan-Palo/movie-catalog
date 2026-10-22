<script lang="ts">
	import { toasts } from '#lib/stores/toast.svelte.js';
</script>

<!-- Polite live region for status messages; errors use role="alert" so they are announced at once. -->
<div class="region" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class={['toast', toast.kind]} role={toast.kind === 'error' ? 'alert' : 'status'}>
			<span>{toast.message}</span>
			<button type="button" onclick={() => toasts.dismiss(toast.id)} aria-label="Dismiss message"
				>×</button
			>
		</div>
	{/each}
</div>

<style>
	.region {
		position: fixed;
		inset-inline: var(--space-3);
		bottom: var(--space-3);
		display: grid;
		gap: var(--space-2);
		justify-items: center;
		pointer-events: none;
		z-index: 200;
	}
	.toast {
		pointer-events: auto;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		max-width: 32rem;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius);
		color: #fff;
		background: var(--color-text);
	}
	.success {
		background: var(--color-success);
	}
	.error {
		background: var(--color-danger);
	}
	button {
		min-width: var(--touch-target);
		min-height: var(--touch-target);
		border: 0;
		background: transparent;
		color: inherit;
		font-size: 1.5rem;
		cursor: pointer;
	}
</style>
