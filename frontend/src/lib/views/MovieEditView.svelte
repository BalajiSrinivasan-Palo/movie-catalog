<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { fetchMovie, updateMovie } from '#lib/api/catalog.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import MovieForm from '#lib/components/MovieForm.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';

	interface Props {
		id: string;
	}

	let { id }: Props = $props();

	const loader = createLoader((signal) => fetchMovie(id, { signal }));

	$effect(() => {
		void id;
		loader.load();
		return () => loader.cancel();
	});
</script>

<svelte:head><title>Edit movie · Movie Catalog</title></svelte:head>
<h1>Edit movie</h1>
{#if loader.state === 'loading' && !loader.data}
	<Skeleton lines={6} label="Loading movie…" />
{:else if loader.state === 'error' && !loader.data}
	<h1>Edit movie</h1>
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
	<p><a href={resolve('/movies')}>Back to movies</a></p>
{:else if loader.data}
	<MovieForm
		initial={{
			title: loader.data.title,
			releaseYear: loader.data.releaseYear,
			runtimeMinutes: loader.data.runtimeMinutes,
			synopsis: loader.data.synopsis,
			genres: loader.data.genres
		}}
		submitLabel="Save changes"
		onsubmit={async (input) => {
			await updateMovie(id, input);
			toasts.success('Movie saved.');
			await goto(resolve('/movies/[id]', { id }));
		}}
		oncancel={() => goto(resolve('/movies/[id]', { id }))}
	/>
{/if}
