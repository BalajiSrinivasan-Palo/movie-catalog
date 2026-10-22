<script lang="ts">
	import { untrack } from 'svelte';
	import { GENRES, type MovieInput } from '#lib/api/catalog.js';
	import { describeError, fieldMessages } from '#lib/messages.js';
	import { keysToText } from '#lib/forms/keys.js';
	import { validateMovie } from '#lib/forms/validate.js';
	import { createAction } from '#lib/stores/action.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import Button from './Button.svelte';
	import TextArea from './TextArea.svelte';
	import TextField from './TextField.svelte';

	interface Props {
		initial?: MovieInput;
		submitLabel: string;
		/** Resolves when saved. A rejection keeps the form as typed and shows the error. */
		onsubmit: (input: MovieInput) => Promise<void>;
		oncancel: () => void;
	}

	let { initial, submitLabel, onsubmit, oncancel }: Props = $props();
	// The form edits its own copy: later changes to `initial` must not overwrite what the user typed.
	const seed = untrack(() => initial);

	let title = $state(seed?.title ?? '');
	let releaseYear = $state(seed ? String(seed.releaseYear) : '');
	let runtimeMinutes = $state(seed?.runtimeMinutes != null ? String(seed.runtimeMinutes) : '');
	let synopsis = $state(seed?.synopsis ?? '');
	let genres = $state<string[]>([...(seed?.genres ?? [])]);
	let errors = $state<Record<string, string>>({});

	const save = createAction((input: MovieInput) => onsubmit(input));

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		const result = validateMovie({ title, releaseYear, runtimeMinutes, synopsis, genres });
		errors = keysToText(result.errors);
		if (!result.value) return;
		await save.run(result.value);
		if (save.state === 'error') {
			errors = fieldMessages(save.error);
			toasts.error(describeError(save.error));
		}
	}

	function toggle(id: string) {
		genres = genres.includes(id) ? genres.filter((g) => g !== id) : [...genres, id];
	}
</script>

<form onsubmit={submit} novalidate aria-label={submitLabel}>
	<TextField label="Title" bind:value={title} error={errors.title} required maxlength={200} />
	<TextField
		label="Release year"
		bind:value={releaseYear}
		error={errors.releaseYear}
		required
		inputmode="numeric"
	/>
	<TextField
		label="Runtime (minutes)"
		bind:value={runtimeMinutes}
		error={errors.runtimeMinutes}
		inputmode="numeric"
		hint="Optional"
	/>
	<TextArea label="Synopsis" bind:value={synopsis} error={errors.synopsis} rows={5} />
	<fieldset aria-describedby={errors.genres ? 'genres-error' : undefined}>
		<legend>Genres (up to 5)</legend>
		<div class="genres">
			{#each GENRES as [id, label] (id)}
				<label class="check">
					<input type="checkbox" checked={genres.includes(id)} onchange={() => toggle(id)} />
					{label}
				</label>
			{/each}
		</div>
		{#if errors.genres}<p id="genres-error" class="error">{errors.genres}</p>{/if}
	</fieldset>
	<div class="actions">
		<Button variant="secondary" onclick={oncancel}>Cancel</Button>
		<Button type="submit" pending={save.pending}>{submitLabel}</Button>
	</div>
</form>

<style>
	form {
		display: grid;
		gap: var(--space-3);
		max-width: 40rem;
	}
	fieldset {
		border: 2px solid var(--color-border);
		border-radius: var(--radius);
	}
	legend {
		font-weight: 600;
	}
	.genres {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-3);
	}
	.check {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: var(--touch-target);
		min-width: var(--touch-target);
	}
	.check input {
		width: 1.25rem;
		height: 1.25rem;
	}
	.error {
		color: var(--color-danger);
		margin: 0;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}
</style>
