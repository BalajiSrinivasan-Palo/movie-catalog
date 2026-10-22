<script lang="ts">
	import type { HTMLTextareaAttributes } from 'svelte/elements';

	interface Props extends Omit<HTMLTextareaAttributes, 'value'> {
		label: string;
		value?: string;
		error?: string;
		hint?: string;
	}

	let { label, value = $bindable(''), error, hint, ...rest }: Props = $props();

	const id = $props.id();
	const hintId = `${id}-hint`;
	const errorId = `${id}-error`;
	const describedBy = $derived(
		[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined
	);
</script>

<div class="field">
	<label for={id}
		>{label}{#if rest.required}<span aria-hidden="true"> *</span>{/if}</label
	>
	{#if hint}<p id={hintId} class="hint">{hint}</p>{/if}
	<textarea
		rows="5"
		{...rest}
		{id}
		bind:value
		aria-invalid={error ? true : undefined}
		aria-describedby={describedBy}></textarea>
	{#if error}<p id={errorId} class="error">{error}</p>{/if}
</div>

<style>
	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}
	label {
		font-weight: 600;
	}
	textarea {
		padding: var(--space-2);
		font: inherit;
		border: 2px solid var(--color-border);
		border-radius: var(--radius);
		resize: vertical;
	}
	textarea[aria-invalid='true'] {
		border-color: var(--color-danger);
	}
	.hint {
		margin: 0;
		color: var(--color-muted);
	}
	.error {
		margin: 0;
		color: var(--color-danger);
		font-weight: 600;
	}
</style>
