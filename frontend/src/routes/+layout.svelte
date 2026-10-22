<script lang="ts">
	import '#lib/styles/global.css';
	import { page } from '$app/state';
	import SearchBox from '#lib/components/SearchBox.svelte';
	import AppHeader from '#lib/components/AppHeader.svelte';
	import StatusFooter from '#lib/components/StatusFooter.svelte';
	import ToastRegion from '#lib/components/ToastRegion.svelte';
	import { isFileDrag } from '#lib/drag.js';

	let { children } = $props();

	// S-701 AC13: a file dropped outside the drop zone must not make the browser open it and leave the app.
	// Only the default is stopped; the file is never read.
	function ignoreFileDrop(event: DragEvent) {
		if (isFileDrag(event)) event.preventDefault();
	}
</script>

<svelte:window ondragover={ignoreFileDrop} ondrop={ignoreFileDrop} />

<a class="skip-link visually-hidden" href="#main">Skip to content</a>
<AppHeader currentPath={page.url.pathname}>
	{#snippet search()}<SearchBox />{/snippet}
</AppHeader>
<main id="main" class="container">
	{@render children()}
</main>
<StatusFooter />
<ToastRegion />

<style>
	main {
		padding-block: var(--space-4);
	}
	.skip-link:focus {
		position: static !important;
		width: auto;
		height: auto;
		clip-path: none;
		display: inline-block;
		padding: var(--space-2) var(--space-3);
	}
</style>
