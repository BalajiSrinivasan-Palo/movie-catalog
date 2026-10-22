<script lang="ts" generics="T extends { id: string }">
	import type { Snippet } from 'svelte';
	import { untrack } from 'svelte';
	import type { Paged } from '#lib/api/catalog.js';
	import Button from './Button.svelte';

	interface Props {
		title: string;
		/** The first page, already loaded. */
		initial: Paged<T>;
		/** Loads a later page of this group only. */
		fetchPage: (page: number) => Promise<Paged<T>>;
		/** How one result is drawn. */
		item: Snippet<[T]>;
		emptyText: string;
	}

	let { title, initial, fetchPage, item, emptyText }: Props = $props();

	// The parent re-creates this component for each new query, so the first page is read once.
	let items = $state<T[]>(untrack(() => [...initial.items]));
	let page = $state(untrack(() => initial.pageInfo.page));
	let totalPages = $state(untrack(() => initial.pageInfo.totalPages));
	let total = $state(untrack(() => initial.pageInfo.totalElements));
	let pending = $state(false);
	let failed = $state(false);

	const hasMore = $derived(page + 1 < totalPages);

	async function showMore() {
		if (pending) return;
		pending = true;
		failed = false;
		try {
			const next = await fetchPage(page + 1);
			// Pages can overlap when data changes between requests, so skip what is already shown (S-503 AC2).
			const seen = new Set(items.map((i) => i.id));
			items = [...items, ...next.items.filter((i) => !seen.has(i.id))];
			page = next.pageInfo.page;
			totalPages = next.pageInfo.totalPages;
			total = next.pageInfo.totalElements;
		} catch {
			failed = true; // what is already shown stays
		} finally {
			pending = false;
		}
	}
</script>

<section aria-labelledby="group-{title}">
	<h2 id="group-{title}">{title} ({total})</h2>
	{#if items.length === 0}
		<p class="muted">{emptyText}</p>
	{:else}
		<ul>
			{#each items as it (it.id)}
				<li>{@render item(it)}</li>
			{/each}
		</ul>
	{/if}
	{#if failed}
		<p role="alert" class="error">Could not load more results.</p>
	{/if}
	{#if hasMore}
		<Button variant="secondary" {pending} onclick={showMore}>
			{failed ? 'Retry' : 'Show more'}
		</Button>
	{/if}
</section>

<style>
	ul {
		list-style: none;
		padding: 0;
		margin: 0 0 var(--space-3);
		display: grid;
		gap: var(--space-2);
	}
	li {
		overflow-wrap: anywhere;
	}
	.muted {
		color: var(--color-muted);
	}
	.error {
		color: var(--color-danger);
	}
</style>
