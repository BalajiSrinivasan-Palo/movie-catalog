<script lang="ts">
	import { resolve } from '$app/paths';
	import {
		genreLabel,
		searchAll,
		searchMovies,
		searchPeople,
		type Movie,
		type Person
	} from '#lib/api/catalog.js';
	import { normalizeQuery, validateSearchQuery } from '#lib/forms/validate.js';
	import { messageFor } from '#lib/messages.js';
	import type { Scope } from '#lib/search-scope.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import ProgressBar from '#lib/components/ProgressBar.svelte';
	import SearchGroup from '#lib/components/SearchGroup.svelte';

	interface Props {
		/** The raw text from the address (`?q=`). */
		q: string;
		/** Which groups to search (`?scope=`, S-702 AC2). */
		scope?: Scope;
	}

	let { q, scope = 'all' }: Props = $props();

	const query = $derived(normalizeQuery(q));
	const problem = $derived(validateSearchQuery(q));

	// The answer carries the query and scope it was for, so a late answer for an older search is never
	// shown (EC-13). A group that was not searched is null.
	const loader = createLoader(async (signal) => {
		const asked = { query, scope };
		if (asked.scope === 'titles') {
			return { ...asked, movies: await searchMovies(asked.query, 0, { signal }), people: null };
		}
		if (asked.scope === 'people') {
			return { ...asked, movies: null, people: await searchPeople(asked.query, 0, { signal }) };
		}
		return { ...asked, ...(await searchAll(asked.query, { signal })) };
	});

	$effect(() => {
		if (problem) {
			loader.cancel(); // no request for a query that cannot be searched
			return;
		}
		void [query, scope];
		loader.load();
		return () => loader.cancel();
	});

	const result = $derived(
		loader.state === 'ready' && loader.data?.query === query && loader.data.scope === scope
			? loader.data
			: null
	);
	const searching = $derived(!problem && loader.state !== 'error' && result === null);
	const noResults = $derived(
		result !== null &&
			(result.movies?.items.length ?? 0) === 0 &&
			(result.people?.items.length ?? 0) === 0
	);

	const found = (r: NonNullable<typeof result>) =>
		[
			r.movies ? `${r.movies.pageInfo.totalElements} movies` : '',
			r.people ? `${r.people.pageInfo.totalElements} people` : ''
		]
			.filter(Boolean)
			.join(' and ');

	const status = $derived.by(() => {
		if (problem) return messageFor(problem);
		if (loader.state === 'error' && !searching) return '';
		if (searching) return 'Searching…';
		if (result === null) return '';
		if (noResults) return `No results for “${query}”.`;
		return `${found(result)} found for “${query}”.`;
	});
</script>

<svelte:head><title>{query ? `Search: ${query}` : 'Search'} · Movie Catalog</title></svelte:head>

<h1>Search</h1>
<p role="status" aria-live="polite" class="status">{status}</p>

{#if searching}
	<ProgressBar label="Searching" />
{:else if !problem && loader.state === 'error'}
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
{:else if result && !noResults}
	{#key `${result.scope}:${result.query}`}
		<div class="groups">
			{#if result.movies}<SearchGroup
					title="Movies"
					initial={result.movies}
					fetchPage={(p) => searchMovies(result.query, p)}
					emptyText="No movies match."
				>
					{#snippet item(movie: Movie)}
						<a href={resolve('/movies/[id]', { id: movie.id })}>{movie.title}</a>
						<span class="muted">({movie.releaseYear})</span>
						{#if movie.genres.length > 0}
							<span class="muted"> · {movie.genres.map(genreLabel).join(', ')}</span>
						{/if}
					{/snippet}
				</SearchGroup>{/if}
			{#if result.people}<SearchGroup
					title="People"
					initial={result.people}
					fetchPage={(p) => searchPeople(result.query, p)}
					emptyText="No people match."
				>
					{#snippet item(person: Person)}
						<a href={resolve('/people/[id]', { id: person.id })}>{person.name}</a>
					{/snippet}
				</SearchGroup>{/if}
		</div>
	{/key}
{/if}

<style>
	.status {
		color: var(--color-muted);
		min-height: 1.5em;
	}
	.groups {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
	}
	.muted {
		color: var(--color-muted);
	}
</style>
