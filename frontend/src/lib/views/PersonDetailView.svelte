<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { ApiError } from '#lib/api/client.js';
	import { deletePerson, fetchPerson, roleLabel } from '#lib/api/catalog.js';
	import { describeError, messageFor } from '#lib/messages.js';
	import { createAction } from '#lib/stores/action.svelte.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import Button from '#lib/components/Button.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import HeroBand from '#lib/components/HeroBand.svelte';
	import PosterCard from '#lib/components/PosterCard.svelte';
	import { initials } from '#lib/initials.js';
	import Pagination from '#lib/components/Pagination.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';

	interface Props {
		id: string;
	}

	let { id }: Props = $props();

	let filmographyPage = $state(0);
	const loader = createLoader((signal) => fetchPerson(id, filmographyPage, { signal }));
	let confirming = $state(false);
	let blocked = $state<{ id: string; title: string; releaseYear: number }[] | null>(null);

	$effect(() => {
		void [id, filmographyPage];
		loader.load();
		return () => loader.cancel();
	});

	const remove = createAction(() => deletePerson(id));

	async function confirmDelete() {
		await remove.run();
		confirming = false;
		if (remove.state === 'success') {
			toasts.success('Person deleted.');
			await goto(resolve('/people'));
			return;
		}
		const error = remove.error;
		if (error instanceof ApiError && error.details.blockingMovies) {
			// D8: a person with credits cannot be deleted; show which movies block it.
			blocked = error.details.blockingMovies as {
				id: string;
				title: string;
				releaseYear: number;
			}[];
			toasts.error(messageFor('person.has_credits'));
		} else {
			toasts.error(describeError(error));
		}
	}
</script>

<svelte:head><title>{loader.data ? loader.data.name : 'Person'} · Movie Catalog</title></svelte:head
>

{#if loader.state === 'loading' && !loader.data}
	<Skeleton lines={8} label="Loading person…" />
{:else if loader.state === 'error' && !loader.data}
	<h1>Person</h1>
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
	<p><a href={resolve('/people')}>Back to people</a></p>
{:else if loader.data}
	{@const person = loader.data}
	<article>
		<HeroBand>
			{#snippet back()}<a href={resolve('/people')}>Back to people</a>{/snippet}
			{#snippet media()}
				<div class="avatar" aria-hidden="true">{initials(person.name)}</div>
			{/snippet}
			<h1>{person.name}</h1>
			{#if person.birthDate}<p class="meta">Born {person.birthDate}</p>{/if}
			{#if person.bio}<p class="bio">{person.bio}</p>{:else}<p class="meta">No biography.</p>{/if}
			{#snippet actions()}
				<a class="button-link secondary" href={resolve('/people/[id]/edit', { id })}>Edit</a>
				<Button variant="danger" onclick={() => (confirming = true)}>Delete</Button>
			{/snippet}
		</HeroBand>

		{#if blocked}
			<div class="blocked" role="alert">
				<p>{messageFor('person.has_credits')} Remove the credits from these movies first:</p>
				<ul>
					{#each blocked as movie (movie.id)}
						<li>
							<a href={resolve('/movies/[id]', { id: movie.id })}
								>{movie.title} ({movie.releaseYear})</a
							>
						</li>
					{/each}
				</ul>
			</div>
		{/if}

		<section aria-labelledby="film-heading">
			<h2 id="film-heading">Filmography</h2>
			{#if person.filmography.items.length === 0}
				<p>No credits yet.</p>
			{:else}
				<ul class="poster-grid">
					{#each person.filmography.items as entry (entry.creditId)}
						<li>
							<PosterCard
								movieId={entry.movie.id}
								title={entry.movie.title}
								imageSrc={entry.movie.thumbnailUrl}
								level={3}
								meta="{entry.movie.releaseYear} · {roleLabel(entry.role)}{entry.characterName
									? ` as ${entry.characterName}`
									: ''}"
							/>
						</li>
					{/each}
				</ul>
				<Pagination
					page={person.filmography.pageInfo.page}
					totalPages={person.filmography.pageInfo.totalPages}
					busy={loader.state === 'loading'}
					onchange={(p) => (filmographyPage = p)}
				/>
			{/if}
		</section>
	</article>

	{#if confirming}
		<ConfirmDialog
			title="Delete person"
			confirmLabel="Delete"
			pending={remove.pending}
			onconfirm={confirmDelete}
			oncancel={() => (confirming = false)}
		>
			<p>Delete {person.name}? This cannot be undone.</p>
		</ConfirmDialog>
	{/if}
{/if}

<style>
	.avatar {
		aspect-ratio: 1;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--color-hero-pill);
		color: var(--color-hero-text);
		font-size: 2.5rem;
		font-weight: 700;
	}
	.meta {
		margin: 0;
		color: var(--color-hero-muted);
	}
	.bio {
		margin: var(--space-3) 0 0;
		max-width: 65ch;
		line-height: 1.6;
		white-space: pre-wrap;
	}
	.blocked {
		margin-block: var(--space-3);
		padding: var(--space-3);
		border: 2px solid var(--color-danger);
		border-radius: var(--radius);
	}
	section {
		margin-top: var(--space-4);
	}
</style>
