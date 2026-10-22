<script lang="ts">
	import { page } from '$app/state';
	import { parseScope } from '#lib/search-scope.js';
	import SearchView from '#lib/views/SearchView.svelte';
</script>

<SearchView
	q={page.url.searchParams.get('q') ?? ''}
	scope={parseScope(page.url.searchParams.get('scope'))}
/>
