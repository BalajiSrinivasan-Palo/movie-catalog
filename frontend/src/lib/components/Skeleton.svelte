<script lang="ts">
	interface Props {
		/** Number of placeholder lines. */
		lines?: number;
		/** Text announced to assistive technology. */
		label?: string;
		/** 'posters' draws 2:3 boxes in the same grid as the poster lists (S-701 AC4). */
		variant?: 'lines' | 'posters';
	}

	let { lines = 3, label = 'Loading…', variant = 'lines' }: Props = $props();
</script>

<div role="status" class="skeleton" class:poster-grid={variant === 'posters'}>
	<span class="visually-hidden">{label}</span>
	{#each Array.from({ length: lines }, (_, i) => i) as i (i)}
		<div class={variant === 'posters' ? 'poster' : 'line'} aria-hidden="true"></div>
	{/each}
</div>

<style>
	.skeleton {
		display: grid;
		gap: var(--space-2);
	}
	/* Keep the shared grid's gap; the scoped .skeleton rule would otherwise win. */
	.skeleton.poster-grid {
		gap: var(--space-4) var(--space-3);
	}
	.line,
	.poster {
		height: 1rem;
		border-radius: var(--radius);
		background: linear-gradient(
			90deg,
			var(--color-surface),
			var(--color-border),
			var(--color-surface)
		);
		background-size: 200% 100%;
		animation: shimmer 1.4s ease-in-out infinite;
	}
	.poster {
		height: auto;
		aspect-ratio: 2 / 3;
	}
	@keyframes shimmer {
		to {
			background-position: -200% 0;
		}
	}
</style>
