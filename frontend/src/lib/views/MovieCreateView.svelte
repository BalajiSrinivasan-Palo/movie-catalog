<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { createMovie } from '#lib/api/catalog.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import MovieForm from '#lib/components/MovieForm.svelte';
</script>

<svelte:head><title>Add movie · Movie Catalog</title></svelte:head>
<h1>Add movie</h1>
<MovieForm
	submitLabel="Create movie"
	onsubmit={async (input) => {
		const movie = await createMovie(input);
		toasts.success('Movie created. You can add artwork below.');
		await goto(resolve('/movies/[id]', { id: movie.id }));
	}}
	oncancel={() => goto(resolve('/movies'))}
/>
