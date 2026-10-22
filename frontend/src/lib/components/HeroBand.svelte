<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		/** Back link, shown first on every width. */
		back?: Snippet;
		/** Picture column: a poster or an initials circle. */
		media: Snippet;
		/** Heading, facts and text. */
		children: Snippet;
		/** Main actions, shown under the text. */
		actions?: Snippet;
	}

	let { back, media, children, actions }: Props = $props();
</script>

<!-- Dark band at the top of a detail page (S-701 AC6, AC8). Below 600 px the picture stacks above the text. -->
<div class="hero">
	{#if back}<div class="back">{@render back()}</div>{/if}
	<div class="media">{@render media()}</div>
	<div class="body">
		{@render children()}
		{#if actions}<div class="actions">{@render actions()}</div>{/if}
	</div>
</div>

<style>
	.hero {
		/* The default blue focus ring is too dark on this background. */
		--focus-ring: 3px solid var(--color-hero-focus);
		display: grid;
		gap: var(--space-4);
		padding: var(--space-4);
		border-radius: var(--radius-card);
		background: var(--color-hero-bg);
		color: var(--color-hero-text);
	}
	@media (min-width: 600px) {
		.hero {
			grid-template-columns: 11rem minmax(0, 1fr);
			align-items: start;
			padding: var(--space-5) var(--space-4);
		}
	}
	.back {
		grid-column: 1 / -1;
		margin-block: calc(-1 * var(--space-2));
		font-size: 0.875rem;
	}
	.back :global(a) {
		color: var(--color-hero-text);
		min-height: var(--touch-target);
		display: inline-flex;
		align-items: center;
	}
	.media {
		width: min(11rem, 60%);
	}
	@media (min-width: 600px) {
		.media {
			width: auto;
		}
	}
	.body {
		min-width: 0;
		overflow-wrap: anywhere;
	}
	.body :global(h1) {
		margin: var(--space-2) 0;
		color: var(--color-hero-text);
	}
	.body :global(a:not(.button-link)) {
		color: var(--color-hero-text);
		min-height: var(--touch-target);
		display: inline-flex;
		align-items: center;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		margin-top: var(--space-3);
	}
</style>
