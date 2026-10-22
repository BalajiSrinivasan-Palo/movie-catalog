<script lang="ts">
	import { resolve } from '$app/paths';
	import {
		addCredit,
		fetchPeople,
		removeCredit,
		roleLabel,
		ROLES,
		updateCredit,
		type Credit,
		type CreditRole,
		type Person
	} from '#lib/api/catalog.js';
	import { describeError, fieldMessages } from '#lib/messages.js';
	import { keysToText } from '#lib/forms/keys.js';
	import { validateCredit } from '#lib/forms/validate.js';
	import { createAction } from '#lib/stores/action.svelte.js';
	import { initials } from '#lib/initials.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import Button from '#lib/components/Button.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Dialog from '#lib/components/Dialog.svelte';
	import Select from '#lib/components/Select.svelte';
	import TextField from '#lib/components/TextField.svelte';

	interface Props {
		movieId: string;
		credits: Credit[];
		/** True when the movie has more credits than are shown (EC-14). */
		truncated: boolean;
		creditCount: number;
		/** Called after any change, so the page reloads the movie. */
		onchanged: () => void;
	}

	let { movieId, credits, truncated, creditCount, onchanged }: Props = $props();

	const roleOptions = ROLES.map((r) => ({ value: r, label: roleLabel(r) }));

	// ---- add ----
	let adding = $state(false);
	let search = $state('');
	let matches = $state<Person[]>([]);
	let searched = $state(false);
	let personId = $state('');
	let role = $state<string>('ACTOR');
	let characterName = $state('');
	let formErrors = $state<Record<string, string>>({});

	async function findPeople(event: SubmitEvent) {
		event.preventDefault();
		try {
			const result = await fetchPeople({ page: 0, size: 20, query: search.trim() || null });
			matches = result.items;
			searched = true;
			personId = result.items[0]?.id ?? '';
		} catch (e) {
			toasts.error(describeError(e));
		}
	}

	const add = createAction(async () => {
		await addCredit(movieId, {
			personId,
			role: role as CreditRole,
			characterName: role === 'ACTOR' ? characterName.trim() || null : null
		});
	});

	async function submitAdd(event: SubmitEvent) {
		event.preventDefault();
		const errors = validateCredit({ role, characterName: role === 'ACTOR' ? characterName : '' });
		if (!personId) errors.personId = 'credit.person.not_found';
		formErrors = keysToText(errors);
		if (Object.keys(errors).length > 0) return;
		await add.run();
		if (add.state === 'success') {
			toasts.success('Credit added.');
			adding = false;
			search = '';
			matches = [];
			searched = false;
			personId = '';
			characterName = '';
			onchanged();
		} else {
			formErrors = fieldMessages(add.error);
			toasts.error(describeError(add.error));
		}
	}

	// ---- edit ----
	let editing = $state<Credit | null>(null);
	let editRole = $state<string>('ACTOR');
	let editCharacter = $state('');
	let editErrors = $state<Record<string, string>>({});

	function startEdit(credit: Credit) {
		editing = credit;
		editRole = credit.role;
		editCharacter = credit.characterName ?? '';
		editErrors = {};
	}

	const save = createAction(async () => {
		if (!editing) return;
		await updateCredit(editing.id, {
			role: editRole as CreditRole,
			characterName: editRole === 'ACTOR' ? editCharacter.trim() || null : null
		});
	});

	async function submitEdit(event?: SubmitEvent) {
		event?.preventDefault();
		const errors = validateCredit({
			role: editRole,
			characterName: editRole === 'ACTOR' ? editCharacter : ''
		});
		editErrors = keysToText(errors);
		if (Object.keys(errors).length > 0) return;
		await save.run();
		if (save.state === 'success') {
			toasts.success('Credit updated.');
			editing = null;
			onchanged();
		} else {
			editErrors = fieldMessages(save.error);
			toasts.error(describeError(save.error));
		}
	}

	// ---- remove ----
	let removing = $state<Credit | null>(null);

	const remove = createAction(async () => {
		if (removing) await removeCredit(removing.id);
	});

	async function confirmRemove() {
		await remove.run();
		if (remove.state === 'success') {
			toasts.success('Credit removed.');
			removing = null;
			onchanged();
		} else {
			toasts.error(describeError(remove.error));
			// A credit that is already gone still means the list is stale: refresh it.
			removing = null;
			onchanged();
		}
	}

	const nameOf = (c: Credit) =>
		c.personStatus === 'RESOLVED' && c.person
			? c.person.name
			: c.personStatus === 'UNAVAILABLE'
				? 'Name unavailable'
				: 'Unknown person';
</script>

<section aria-labelledby="credits-heading">
	<div class="heading">
		<h2 id="credits-heading">Cast and crew</h2>
		{#if !adding}<Button variant="secondary" onclick={() => (adding = true)}>Add credit</Button
			>{/if}
	</div>

	{#if credits.some((c) => c.personStatus === 'UNAVAILABLE')}
		<p class="notice" role="status">
			Some names cannot be shown right now.
			<Button variant="secondary" onclick={onchanged}>Try again</Button>
		</p>
	{/if}

	{#if adding}
		<form class="add" onsubmit={findPeople} aria-label="Find a person">
			<TextField label="Find a person by name" bind:value={search} />
			<Button type="submit" variant="secondary">Search people</Button>
		</form>
		{#if searched}
			{#if matches.length === 0}
				<p>No people found. <a href={resolve('/people/new')}>Create a person first.</a></p>
			{:else}
				<form class="add" onsubmit={submitAdd} aria-label="Add credit">
					<Select
						label="Person"
						options={matches.map((p) => ({ value: p.id, label: p.name }))}
						bind:value={personId}
						error={formErrors.personId}
					/>
					<Select label="Role" options={roleOptions} bind:value={role} />
					{#if role === 'ACTOR'}
						<TextField
							label="Character (optional)"
							bind:value={characterName}
							error={formErrors.characterName}
						/>
					{/if}
					<div class="row">
						<Button variant="secondary" onclick={() => (adding = false)}>Cancel</Button>
						<Button type="submit" pending={add.pending}>Add to movie</Button>
					</div>
				</form>
			{/if}
		{:else}
			<div class="row">
				<Button variant="secondary" onclick={() => (adding = false)}>Cancel</Button>
			</div>
		{/if}
	{/if}

	{#if credits.length === 0}
		<p>No credits yet.</p>
	{:else}
		<ul class="credits">
			{#each credits as credit (credit.id)}
				{@const resolved = credit.personStatus === 'RESOLVED' && credit.person !== null}
				<!-- A person that cannot be shown is a dashed card with the role kept (S-701 AC7). -->
				<li class="card" class:missing={!resolved}>
					<span class="avatar" aria-hidden="true">{resolved ? initials(nameOf(credit)) : '?'}</span>
					<span class="who">
						{#if credit.personStatus === 'RESOLVED' && credit.person}
							<a href={resolve('/people/[id]', { id: credit.person.id })}>{nameOf(credit)}</a>
						{:else}
							{nameOf(credit)}
						{/if}
						<span class="role"
							>{roleLabel(credit.role)}{credit.characterName
								? ` as ${credit.characterName}`
								: ''}</span
						>
					</span>
					<span class="row">
						<Button
							variant="secondary"
							onclick={() => startEdit(credit)}
							aria-label="Edit credit of {nameOf(credit)}">Edit</Button
						>
						<Button
							variant="secondary"
							onclick={() => (removing = credit)}
							aria-label="Remove credit of {nameOf(credit)}">Remove</Button
						>
					</span>
				</li>
			{/each}
		</ul>
		{#if truncated}
			<p class="notice">Showing {credits.length} of {creditCount} credits.</p>
		{/if}
	{/if}
</section>

{#if editing}
	<Dialog title="Edit credit" onclose={() => (editing = null)}>
		<form id="edit-credit" onsubmit={submitEdit} novalidate>
			<Select label="Role" options={roleOptions} bind:value={editRole} />
			{#if editRole === 'ACTOR'}
				<TextField
					label="Character (optional)"
					bind:value={editCharacter}
					error={editErrors.characterName}
				/>
			{/if}
		</form>
		{#snippet actions()}
			<Button variant="secondary" onclick={() => (editing = null)}>Cancel</Button>
			<Button pending={save.pending} onclick={() => submitEdit()}>Save</Button>
		{/snippet}
	</Dialog>
{/if}

{#if removing}
	<ConfirmDialog
		title="Remove credit"
		confirmLabel="Remove"
		pending={remove.pending}
		onconfirm={confirmRemove}
		oncancel={() => (removing = null)}
	>
		<p>Remove {nameOf(removing)} ({roleLabel(removing.role)}) from this movie?</p>
	</ConfirmDialog>
{/if}

<style>
	.heading,
	.row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}
	.heading {
		justify-content: space-between;
	}
	.credits {
		list-style: none;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 15rem), 1fr));
		gap: var(--space-3);
	}
	.card {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: var(--space-2) var(--space-3);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-card);
	}
	.card.missing {
		border-style: dashed;
		border-color: var(--color-muted);
	}
	.card .row {
		grid-column: 1 / -1;
	}
	.avatar {
		width: 2.5rem;
		height: 2.5rem;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--color-accent-tint);
		color: var(--color-primary);
		font-weight: 700;
	}
	.missing .avatar {
		background: var(--color-surface);
		color: var(--color-muted);
	}
	.who {
		display: flex;
		flex-direction: column;
		min-width: 0;
		overflow-wrap: anywhere;
	}
	.who a {
		font-weight: 600;
		color: var(--color-text);
	}
	.role,
	.notice {
		color: var(--color-muted);
	}
	.add {
		display: grid;
		gap: var(--space-3);
		max-width: 28rem;
		margin-block: var(--space-3);
	}
</style>
