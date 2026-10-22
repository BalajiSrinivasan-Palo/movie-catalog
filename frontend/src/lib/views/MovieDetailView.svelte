<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { deleteMovie, fetchMovie, genreLabel, removeArtwork } from '#lib/api/catalog.js';
	import { describeError } from '#lib/messages.js';
	import { createAction } from '#lib/stores/action.svelte.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import ArtworkImage from '#lib/components/ArtworkImage.svelte';
	import ArtworkUploader from '#lib/components/ArtworkUploader.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Button from '#lib/components/Button.svelte';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import HeroBand from '#lib/components/HeroBand.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';
	import CreditsSection from './CreditsSection.svelte';

	interface Props {
		id: string;
	}

	let { id }: Props = $props();

	const loader = createLoader((signal) => fetchMovie(id, { signal }));
	let confirming = $state(false);
	let confirmingArtworkRemoval = $state(false);

	$effect(() => {
		void id;
		loader.load();
		return () => loader.cancel();
	});

	const remove = createAction(() => deleteMovie(id));
	const removeImage = createAction(() => removeArtwork(id));

	async function confirmRemoveArtwork() {
		await removeImage.run();
		confirmingArtworkRemoval = false;
		if (removeImage.state === 'success') {
			toasts.success('Artwork removed.');
		} else {
			toasts.error(describeError(removeImage.error));
		}
		await loader.load(); // show what is really stored, success or not
	}

	async function confirmDelete() {
		await remove.run();
		if (remove.state === 'success') {
			toasts.success('Movie deleted.');
			await goto(resolve('/movies'));
		} else {
			confirming = false;
			toasts.error(describeError(remove.error));
		}
	}
</script>

<svelte:head><title>{loader.data ? loader.data.title : 'Movie'} · Movie Catalog</title></svelte:head
>

{#if loader.state === 'loading' && !loader.data}
	<Skeleton lines={8} label="Loading movie…" />
{:else if loader.state === 'error' && !loader.data}
	<h1>Movie</h1>
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
	<p><a href={resolve('/movies')}>Back to movies</a></p>
{:else if loader.data}
	{@const movie = loader.data}
	<article>
		<HeroBand>
			{#snippet back()}<a href={resolve('/movies')}>Back to movies</a>{/snippet}
			{#snippet media()}
				<div class="poster">
					<ArtworkImage
						src={movie.artwork?.url ?? null}
						title={movie.title}
						width={movie.artwork?.width}
						height={movie.artwork?.height}
					/>
				</div>
			{/snippet}
			<h1>{movie.title}</h1>
			<p class="meta">
				{[
					String(movie.releaseYear),
					movie.runtimeMinutes ? `${movie.runtimeMinutes} min` : '',
					movie.creditCount === 1 ? '1 credit' : `${movie.creditCount} credits`
				]
					.filter(Boolean)
					.join(' · ')}
			</p>
			{#if movie.genres.length > 0}
				<ul class="pills" aria-label="Genres">
					{#each movie.genres as genre (genre)}<li>{genreLabel(genre)}</li>{/each}
				</ul>
			{/if}
			{#if movie.synopsis}<p class="synopsis">{movie.synopsis}</p>{:else}<p class="meta">
					No synopsis.
				</p>{/if}
			{#snippet actions()}
				<a class="button-link secondary" href={resolve('/movies/[id]/edit', { id })}>Edit</a>
				<Button variant="danger" onclick={() => (confirming = true)}>Delete</Button>
			{/snippet}
		</HeroBand>

		<div class="artwork">
			<ArtworkUploader
				movieId={id}
				hasArtwork={movie.artwork !== null}
				onsettled={() => loader.load()}
				onuploaded={() => toasts.success('Artwork saved.')}
			/>
			{#if movie.artwork}
				<Button variant="secondary" onclick={() => (confirmingArtworkRemoval = true)}>
					Remove artwork
				</Button>
			{/if}
		</div>

		<CreditsSection
			movieId={id}
			credits={movie.credits}
			truncated={movie.creditsTruncated}
			creditCount={movie.creditCount}
			onchanged={() => loader.load()}
		/>
	</article>

	{#if confirmingArtworkRemoval}
		<ConfirmDialog
			title="Remove artwork"
			confirmLabel="Remove"
			pending={removeImage.pending}
			onconfirm={confirmRemoveArtwork}
			oncancel={() => (confirmingArtworkRemoval = false)}
		>
			<p>Remove the artwork of “{movie.title}”? The movie stays; it will show a placeholder.</p>
		</ConfirmDialog>
	{/if}

	{#if confirming}
		<ConfirmDialog
			title="Delete movie"
			confirmLabel="Delete"
			pending={remove.pending}
			onconfirm={confirmDelete}
			oncancel={() => (confirming = false)}
		>
			<p>Delete “{movie.title}”? Its credits and artwork are deleted too. This cannot be undone.</p>
		</ConfirmDialog>
	{/if}
{/if}

<style>
	.poster {
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--color-hero-pill);
		font-size: 0.875rem;
		text-align: center;
	}
	.poster :global(.artwork) {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	/* On the dark band the placeholder needs a dark box too, or its light text fails contrast. */
	.poster :global(.placeholder) {
		height: 100%;
		background: var(--color-hero-pill);
		color: var(--color-hero-text);
	}
	.meta {
		margin: 0;
		color: var(--color-hero-muted);
	}
	.pills {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		list-style: none;
		margin: var(--space-3) 0 0;
		padding: 0;
	}
	.pills li {
		padding: var(--space-1) var(--space-3);
		border-radius: var(--radius-pill);
		background: var(--color-hero-pill);
		font-size: 0.875rem;
	}
	.synopsis {
		margin: var(--space-3) 0 0;
		max-width: 65ch;
		line-height: 1.6;
		white-space: pre-wrap;
	}
	.artwork {
		margin-block: var(--space-4);
	}
</style>
