<script lang="ts">
	import { page } from '$app/state';
	import MovieDetailView from '#lib/views/MovieDetailView.svelte';
</script>

<MovieDetailView id={page.params.id ?? ''} />
