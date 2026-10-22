<script lang="ts">
	import { untrack } from 'svelte';
	import type { PersonInput } from '#lib/api/catalog.js';
	import { describeError, fieldMessages } from '#lib/messages.js';
	import { keysToText } from '#lib/forms/keys.js';
	import { validatePerson } from '#lib/forms/validate.js';
	import { createAction } from '#lib/stores/action.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import Button from './Button.svelte';
	import TextArea from './TextArea.svelte';
	import TextField from './TextField.svelte';

	interface Props {
		initial?: PersonInput;
		submitLabel: string;
		onsubmit: (input: PersonInput) => Promise<void>;
		oncancel: () => void;
	}

	let { initial, submitLabel, onsubmit, oncancel }: Props = $props();
	// The form edits its own copy: later changes to `initial` must not overwrite what the user typed.
	const seed = untrack(() => initial);

	let name = $state(seed?.name ?? '');
	let birthDate = $state(seed?.birthDate ?? '');
	let bio = $state(seed?.bio ?? '');
	let errors = $state<Record<string, string>>({});

	const save = createAction((input: PersonInput) => onsubmit(input));

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		const result = validatePerson({ name, birthDate, bio });
		errors = keysToText(result.errors);
		if (!result.value) return;
		await save.run(result.value);
		if (save.state === 'error') {
			errors = fieldMessages(save.error);
			toasts.error(describeError(save.error));
		}
	}
</script>

<form onsubmit={submit} novalidate aria-label={submitLabel}>
	<TextField label="Name" bind:value={name} error={errors.name} required maxlength={200} />
	<TextField
		label="Birth date"
		type="date"
		bind:value={birthDate}
		error={errors.birthDate}
		hint="Optional"
	/>
	<TextArea label="Biography" bind:value={bio} error={errors.bio} rows={5} />
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
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}
</style>
