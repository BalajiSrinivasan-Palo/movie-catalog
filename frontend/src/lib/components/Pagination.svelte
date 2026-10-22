<script lang="ts">
	import Button from './Button.svelte';

	interface Props {
		/** Zero-based current page. */
		page: number;
		totalPages: number;
		onchange: (page: number) => void;
		/** While a page is loading the controls stay focusable but ignore clicks (aria-disabled). */
		busy?: boolean;
	}

	let { page, totalPages, onchange, busy = false }: Props = $props();
</script>

{#if totalPages > 1}
	<nav class="pagination" aria-label="Pagination">
		<Button
			variant="secondary"
			disabled={page <= 0}
			pending={busy}
			onclick={() => onchange(page - 1)}
		>
			Previous
		</Button>
		<span aria-live="polite">Page {page + 1} of {totalPages}</span>
		<Button
			variant="secondary"
			disabled={page >= totalPages - 1}
			pending={busy}
			onclick={() => onchange(page + 1)}
		>
			Next
		</Button>
	</nav>
{/if}

<style>
	.pagination {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: center;
		gap: var(--space-3);
		margin-block: var(--space-4);
	}
</style>
