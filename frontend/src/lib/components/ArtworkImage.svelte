<script lang="ts">
	interface Props {
		/** Image address, or null when there is no artwork. */
		src: string | null;
		title: string;
		width?: number;
		height?: number;
		lazy?: boolean;
	}

	let { src, title, width, height, lazy = false }: Props = $props();

	// A picture that fails to load is replaced by the same placeholder as a missing one (S-305 AC2).
	let failedSrc = $state<string | null>(null);
	const showImage = $derived(src !== null && failedSrc !== src);
</script>

{#if showImage}
	<img
		class="artwork"
		{src}
		alt="Poster of {title}"
		{width}
		{height}
		loading={lazy ? 'lazy' : undefined}
		onerror={() => (failedSrc = src)}
	/>
{:else}
	<div class="artwork placeholder" role="img" aria-label="No artwork for {title}">No image</div>
{/if}

<style>
	/* A fixed ratio reserves the space before the image arrives, so the page does not jump (S-305 AC3). */
	.artwork {
		display: block;
		width: 100%;
		aspect-ratio: 2 / 3;
		object-fit: cover;
		border-radius: var(--radius);
	}
	.placeholder {
		display: grid;
		place-items: center;
		background: var(--color-surface);
		color: var(--color-muted);
	}
</style>
