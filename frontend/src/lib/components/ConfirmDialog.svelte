<script lang="ts">
	import type { Snippet } from 'svelte';
	import Button from './Button.svelte';
	import Dialog from './Dialog.svelte';

	interface Props {
		title: string;
		confirmLabel: string;
		pending?: boolean;
		onconfirm: () => void;
		oncancel: () => void;
		children: Snippet;
	}

	let { title, confirmLabel, pending = false, onconfirm, oncancel, children }: Props = $props();
</script>

<Dialog {title} onclose={oncancel}>
	{@render children()}
	{#snippet actions()}
		<Button variant="secondary" onclick={oncancel}>Cancel</Button>
		<Button variant="danger" {pending} onclick={onconfirm}>{confirmLabel}</Button>
	{/snippet}
</Dialog>
