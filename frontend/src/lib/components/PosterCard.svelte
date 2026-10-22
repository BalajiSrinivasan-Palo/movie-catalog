<script lang="ts">
	import { resolve } from '$app/paths';
	import ArtworkImage from './ArtworkImage.svelte';

	interface Props {
		/** Movie the card links to. */
		movieId: string;
		title: string;
		/** Thumbnail address, or null for the placeholder. */
		imageSrc: string | null;
		/** Short line under the title, for example "1995 · Crime". */
		meta?: string;
		/** Heading level of the title: 2 in a page list, 3 under a section heading. */
		level?: 2 | 3;
	}

	let { movieId, title, imageSrc, meta, level = 2 }: Props = $props();
</script>

<!-- The title link is stretched over the whole card, so the card is one click target named by the title (S-701 AC2). -->
<article class="poster-card">
	<div class="poster">
		<ArtworkImage src={imageSrc} {title} lazy />
	</div>
	<svelte:element this={`h${level}`} class="title"
		><a href={resolve('/movies/[id]', { id: movieId })}>{title}</a></svelte:element
	>
	{#if meta}<p class="meta">{meta}</p>{/if}
</article>

<style>
	.poster-card {
		position: relative;
		min-width: 0;
	}
	.poster {
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--color-surface);
		font-size: 0.8rem;
		text-align: center;
	}
	.poster :global(.artwork) {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.poster :global(.placeholder) {
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--color-muted);
		border: 1px dashed var(--color-border);
		border-radius: var(--radius);
	}
	.title {
		margin: var(--space-2) 0 0;
		font-size: 1rem;
		overflow-wrap: anywhere;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
	a {
		color: var(--color-text);
		text-decoration: none;
	}
	a::after {
		content: '';
		position: absolute;
		inset: 0;
		border-radius: var(--radius);
	}
	a:hover {
		text-decoration: underline;
	}
	a:focus-visible {
		outline: none;
	}
	a:focus-visible::after {
		outline: var(--focus-ring);
		outline-offset: 4px;
	}
	.meta {
		margin: 0;
		font-size: 0.875rem;
		color: var(--color-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
</style>
