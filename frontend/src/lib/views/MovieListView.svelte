<script lang="ts">
	import { resolve } from '$app/paths';
	import { fetchMovies, genreLabel, type Genre, type MovieListParams } from '#lib/api/catalog.js';
	import { messageFor } from '#lib/messages.js';
	import { maxYear, EARLIEST_YEAR } from '#lib/forms/validate.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import Button from '#lib/components/Button.svelte';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import GenreChips from '#lib/components/GenreChips.svelte';
	import Pagination from '#lib/components/Pagination.svelte';
	import PosterCard from '#lib/components/PosterCard.svelte';
	import Select from '#lib/components/Select.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';
	import YearPicker from '#lib/components/YearPicker.svelte';

	const SORTS = [
		{ value: 'TITLE:ASC', label: 'Title A–Z' },
		{ value: 'TITLE:DESC', label: 'Title Z–A' },
		{ value: 'RELEASE_YEAR:DESC', label: 'Newest first' },
		{ value: 'RELEASE_YEAR:ASC', label: 'Oldest first' }
	];

	// What the user is editing in the filter form, and what has been applied to the list.
	let sort = $state('TITLE:ASC');
	let genres = $state<Genre[]>([]);
	let yearFrom = $state('');
	let yearTo = $state('');
	let filterErrors = $state<Record<string, string>>({});
	let applied = $state<{ sort: string; genres: Genre[]; yearFrom: string; yearTo: string }>({
		sort: 'TITLE:ASC',
		genres: [],
		yearFrom: '',
		yearTo: ''
	});
	let page = $state(0);

	const filtered = $derived(
		applied.genres.length > 0 || applied.yearFrom !== '' || applied.yearTo !== ''
	);

	function params(): MovieListParams {
		const [sortField, direction] = applied.sort.split(':') as [
			'TITLE' | 'RELEASE_YEAR',
			'ASC' | 'DESC'
		];
		return {
			page,
			sortField,
			direction,
			genres: applied.genres,
			yearFrom: applied.yearFrom === '' ? null : Number(applied.yearFrom),
			yearTo: applied.yearTo === '' ? null : Number(applied.yearTo)
		};
	}

	const loader = createLoader((signal) => fetchMovies(params(), { signal }));

	$effect(() => {
		// Reload whenever the applied filters or the page change.
		void [applied, page];
		loader.load();
		return () => loader.cancel();
	});

	// EC-11: a page beyond the last one returns to the last valid page.
	$effect(() => {
		const data = loader.data;
		if (
			loader.state === 'ready' &&
			data &&
			data.items.length === 0 &&
			page > 0 &&
			data.pageInfo.totalPages > 0
		) {
			page = data.pageInfo.totalPages - 1;
		}
	});

	function validYear(text: string): boolean {
		if (text === '') return true;
		const n = Number(text);
		return Number.isInteger(n) && n >= EARLIEST_YEAR && n <= maxYear();
	}

	function apply(event: SubmitEvent) {
		event.preventDefault();
		const errors: Record<string, string> = {};
		const from = yearFrom.trim();
		const to = yearTo.trim();
		if (
			!validYear(from) ||
			!validYear(to) ||
			(from !== '' && to !== '' && Number(from) > Number(to))
		) {
			errors.year = messageFor('filter.year.invalid');
		}
		filterErrors = errors;
		if (Object.keys(errors).length > 0) return;
		page = 0;
		applied = { sort, genres, yearFrom: from, yearTo: to };
	}

	// Genre chips apply at once; sort and years still wait for Apply (ADR-014, ADR-015).
	function chooseGenres(value: Genre[]) {
		genres = value;
		page = 0;
		applied = { ...applied, genres: value };
	}

	const countText = (n: number) => (n === 1 ? '1 title' : `${n} titles`);

	function clear() {
		sort = 'TITLE:ASC';
		genres = [];
		yearFrom = '';
		yearTo = '';
		filterErrors = {};
		page = 0;
		applied = { sort, genres, yearFrom, yearTo };
	}
</script>

<svelte:head><title>Movies · Movie Catalog</title></svelte:head>

<div class="heading">
	<div class="title">
		<h1>Movies</h1>
		{#if loader.data}<p class="count">{countText(loader.data.pageInfo.totalElements)}</p>{/if}
	</div>
	<a class="button-link" href={resolve('/movies/new')}>Add movie</a>
</div>

<form class="filters" onsubmit={apply} aria-label="Filter and sort movies" novalidate>
	<div class="genres-row"><GenreChips value={applied.genres} onchange={chooseGenres} /></div>
	<Select label="Sort by" options={SORTS} bind:value={sort} />
	<YearPicker label="From year" bind:value={yearFrom} error={filterErrors.year} />
	<YearPicker label="To year" bind:value={yearTo} />
	<div class="filter-actions">
		<Button type="submit">Apply</Button>
		{#if filtered}<Button variant="secondary" onclick={clear}>Clear filters</Button>{/if}
	</div>
</form>

{#if loader.state === 'loading' && !loader.data}
	<Skeleton variant="posters" lines={10} label="Loading movies…" />
{:else if loader.state === 'error' && !loader.data}
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
{:else if loader.data}
	{#if loader.state === 'error'}
		<ErrorPanel error={loader.error} onretry={() => loader.load()} />
	{/if}
	{#if loader.data.items.length === 0 && loader.data.pageInfo.totalElements === 0}
		<div class="empty">
			{#if filtered}
				<p>No movies match these filters.</p>
				<Button variant="secondary" onclick={clear}>Clear filters</Button>
			{:else}
				<p>There are no movies yet.</p>
				<a class="button-link" href={resolve('/movies/new')}>Add the first movie</a>
			{/if}
		</div>
	{:else}
		<ul class="poster-grid" aria-busy={loader.state === 'loading'}>
			{#each loader.data.items as movie (movie.id)}
				<li>
					<PosterCard
						movieId={movie.id}
						title={movie.title}
						imageSrc={movie.artwork?.thumbnailUrl ?? null}
						meta={[String(movie.releaseYear), movie.genres.map(genreLabel).join(', ')]
							.filter(Boolean)
							.join(' · ')}
					/>
				</li>
			{/each}
		</ul>
		<Pagination
			page={loader.data.pageInfo.page}
			totalPages={loader.data.pageInfo.totalPages}
			busy={loader.state === 'loading'}
			onchange={(p) => (page = p)}
		/>
	{/if}
{/if}

<style>
	.heading {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
	}
	.title {
		display: flex;
		flex-wrap: wrap;
		align-items: baseline;
		gap: var(--space-2);
	}
	h1 {
		margin: 0;
	}
	.count {
		margin: 0;
		color: var(--color-muted);
	}
	/* A wrapping row: the fields share the width, and the buttons stay side by side, level with the fields. */
	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-end;
		gap: var(--space-3);
		margin-block: var(--space-4);
	}
	.filters > :global(.field) {
		flex: 1 1 10rem;
	}
	/* On phones a field takes the full width; from 600 px fields stop growing, so they sit beside the buttons. */
	@media (min-width: 600px) {
		.filters > :global(.field) {
			max-width: 14rem;
		}
	}
	.filter-actions {
		flex: none;
		display: flex;
		gap: var(--space-2);
	}
	.genres-row {
		flex: 1 1 100%;
		min-width: 0;
	}
	.empty {
		padding: var(--space-4);
		text-align: center;
		border: 2px dashed var(--color-border);
		border-radius: var(--radius-card);
	}
</style>
