<script lang="ts">
	import { ApiError } from '#lib/api/client.js';
	import { describeError } from '#lib/messages.js';
	import Button from './Button.svelte';

	interface Props {
		error: unknown;
		onretry?: () => void;
	}

	let { error, onretry }: Props = $props();
	const missing = $derived(error instanceof ApiError && error.code === 'NOT_FOUND');
</script>

<div class="panel" role="alert">
	<p>{describeError(error)}</p>
	{#if onretry && !missing}
		<Button variant="secondary" onclick={onretry}>Try again</Button>
	{/if}
</div>

<style>
	.panel {
		padding: var(--space-3);
		border: 2px solid var(--color-danger);
		border-radius: var(--radius);
	}
	p {
		margin-top: 0;
	}
</style>
