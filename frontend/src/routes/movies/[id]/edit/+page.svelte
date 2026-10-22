<script lang="ts">
	import { page } from '$app/state';
	import MovieEditView from '#lib/views/MovieEditView.svelte';
</script>

<MovieEditView id={page.params.id ?? ''} />
