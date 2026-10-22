<script lang="ts">
	import { resolve } from '$app/paths';
	import { fetchPeople } from '#lib/api/catalog.js';
	import { initials } from '#lib/initials.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import Button from '#lib/components/Button.svelte';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import Pagination from '#lib/components/Pagination.svelte';
	import Select from '#lib/components/Select.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';
	import TextField from '#lib/components/TextField.svelte';

	let nameInput = $state('');
	let order = $state('ASC');
	let applied = $state({ query: '', order: 'ASC' });
	let page = $state(0);

	const loader = createLoader((signal) =>
		fetchPeople(
			{ page, query: applied.query || null, direction: applied.order as 'ASC' | 'DESC' },
			{ signal }
		)
	);

	$effect(() => {
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

	function apply(event: SubmitEvent) {
		event.preventDefault();
		page = 0;
		applied = { query: nameInput.trim(), order };
	}

	function clear() {
		nameInput = '';
		order = 'ASC';
		page = 0;
		applied = { query: '', order: 'ASC' };
	}
</script>

<svelte:head><title>People · Movie Catalog</title></svelte:head>

<div class="heading">
	<div class="title">
		<h1>People</h1>
		{#if loader.data}<p class="count">
				{loader.data.pageInfo.totalElements === 1
					? '1 person'
					: `${loader.data.pageInfo.totalElements} people`}
			</p>{/if}
	</div>
	<a class="button-link" href={resolve('/people/new')}>Add person</a>
</div>

<form class="filters" onsubmit={apply} aria-label="Filter and sort people">
	<TextField label="Name contains" bind:value={nameInput} maxlength={100} />
	<Select
		label="Sort by"
		options={[
			{ value: 'ASC', label: 'Name A–Z' },
			{ value: 'DESC', label: 'Name Z–A' }
		]}
		bind:value={order}
	/>
	<div class="filter-actions">
		<Button type="submit">Apply</Button>
		{#if applied.query}<Button variant="secondary" onclick={clear}>Clear filter</Button>{/if}
	</div>
</form>

{#if loader.state === 'loading' && !loader.data}
	<Skeleton variant="posters" lines={10} label="Loading people…" />
{:else if loader.state === 'error' && !loader.data}
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
{:else if loader.data}
	{#if loader.state === 'error'}<ErrorPanel
			error={loader.error}
			onretry={() => loader.load()}
		/>{/if}
	{#if loader.data.pageInfo.totalElements === 0}
		<div class="empty">
			{#if applied.query}
				<p>No people match “{applied.query}”.</p>
				<Button variant="secondary" onclick={clear}>Clear filter</Button>
			{:else}
				<p>There are no people yet.</p>
				<a class="button-link" href={resolve('/people/new')}>Add the first person</a>
			{/if}
		</div>
	{:else}
		<ul class="poster-grid people" aria-busy={loader.state === 'loading'}>
			{#each loader.data.items as person (person.id)}
				<li class="person">
					<div class="avatar" aria-hidden="true">{initials(person.name)}</div>
					<h2><a href={resolve('/people/[id]', { id: person.id })}>{person.name}</a></h2>
					{#if person.birthDate}<p class="born">born {person.birthDate}</p>{/if}
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
	/* A wrapping row: the fields share the width, and the buttons stay side by side, level with the fields. */
	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-end;
		gap: var(--space-3);
		margin-block: var(--space-3);
	}
	.filters > :global(.field) {
		flex: 1 1 10rem;
	}
	/* On phones a field takes the full width; from 600 px fields stop growing, so they sit beside the buttons. */
	@media (min-width: 600px) {
		.filters > :global(.field) {
			max-width: 20rem;
		}
	}
	.filter-actions {
		flex: none;
		display: flex;
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
	.people {
		margin-top: var(--space-4);
	}
	/* The name link is stretched over the card, as in PosterCard (S-701 AC5). */
	.person {
		position: relative;
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-4) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-card);
		text-align: center;
		min-width: 0;
	}
	.person:hover {
		border-color: var(--color-muted);
	}
	.avatar {
		width: 4rem;
		height: 4rem;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--color-accent-tint);
		color: var(--color-primary);
		font-size: 1.25rem;
		font-weight: 700;
	}
	h2 {
		margin: var(--space-2) 0 0;
		font-size: 1rem;
		overflow-wrap: anywhere;
	}
	h2 a {
		color: var(--color-text);
		text-decoration: none;
	}
	h2 a::after {
		content: '';
		position: absolute;
		inset: 0;
		border-radius: var(--radius-card);
	}
	h2 a:focus-visible {
		outline: none;
	}
	h2 a:focus-visible::after {
		outline: var(--focus-ring);
		outline-offset: 2px;
	}
	.born {
		margin: 0;
		font-size: 0.875rem;
		color: var(--color-muted);
	}
	.empty {
		padding: var(--space-4);
		text-align: center;
		border: 2px dashed var(--color-border);
		border-radius: var(--radius);
	}
</style>
