<script lang="ts">
	import type { HTMLSelectAttributes } from 'svelte/elements';

	export interface Option {
		value: string;
		label: string;
	}

	interface Props extends Omit<HTMLSelectAttributes, 'value'> {
		label: string;
		options: Option[];
		value?: string;
		error?: string;
		/** Text of an empty first option, for example "Any genre". */
		placeholder?: string;
	}

	let { label, options, value = $bindable(''), error, placeholder, ...rest }: Props = $props();

	const id = $props.id();
	const errorId = `${id}-error`;
</script>

<div class="field">
	<label for={id}>{label}</label>
	<select
		{...rest}
		{id}
		bind:value
		aria-invalid={error ? true : undefined}
		aria-describedby={error ? errorId : undefined}
	>
		{#if placeholder !== undefined}<option value="">{placeholder}</option>{/if}
		{#each options as option (option.value)}
			<option value={option.value}>{option.label}</option>
		{/each}
	</select>
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
	select {
		min-height: var(--touch-target);
		padding: 0 var(--space-2);
		font: inherit;
		border: 2px solid var(--color-border);
		border-radius: var(--radius);
		background: var(--color-bg);
	}
	.error {
		margin: 0;
		color: var(--color-danger);
		font-weight: 600;
	}
</style>
