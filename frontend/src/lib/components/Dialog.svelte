<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		onclose: () => void;
		children: Snippet;
		actions?: Snippet;
	}

	let { title, onclose, children, actions }: Props = $props();

	const titleId = $props.id();
	let panel: HTMLDivElement | undefined = $state();
	let returnFocusTo: Element | null = null;

	const FOCUSABLE =
		'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

	$effect(() => {
		// Remember the opener, move focus into the dialog, and give focus back on close.
		returnFocusTo = document.activeElement;
		const first = panel?.querySelector<HTMLElement>(FOCUSABLE);
		(first ?? panel)?.focus();

		// Make everything outside the dialog inert, so Tab, screen readers and clicks cannot reach the page behind it.
		const inerted: Element[] = [];
		let node: Element | null = panel?.parentElement ?? null; // the backdrop
		while (node && node !== document.body) {
			const parent: Element | null = node.parentElement;
			for (const sibling of parent?.children ?? []) {
				if (sibling !== node && !sibling.hasAttribute('inert')) {
					sibling.setAttribute('inert', '');
					inerted.push(sibling);
				}
			}
			node = parent;
		}
		return () => {
			inerted.forEach((el) => el.removeAttribute('inert'));
			if (returnFocusTo instanceof HTMLElement) returnFocusTo.focus();
		};
	});

	// A click on the backdrop must not drop focus onto the page behind: keep it in the dialog.
	function onbackdropmousedown(event: MouseEvent) {
		if (event.target === event.currentTarget) {
			event.preventDefault();
			panel?.focus();
		}
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key === 'Escape') {
			event.stopPropagation();
			onclose();
			return;
		}
		if (event.key !== 'Tab' || !panel) return;
		// Focus trap: Tab and Shift+Tab cycle inside the dialog.
		const items = [...panel.querySelectorAll<HTMLElement>(FOCUSABLE)];
		if (items.length === 0) {
			event.preventDefault();
			return;
		}
		const first = items[0];
		const last = items[items.length - 1];
		const active = document.activeElement;
		if (event.shiftKey && (active === first || active === panel)) {
			event.preventDefault();
			last.focus();
		} else if (!event.shiftKey && active === last) {
			event.preventDefault();
			first.focus();
		}
	}
</script>

<div class="backdrop" role="presentation" onmousedown={onbackdropmousedown}>
	<div
		bind:this={panel}
		class="panel"
		role="dialog"
		aria-modal="true"
		aria-labelledby={titleId}
		tabindex="-1"
		{onkeydown}
	>
		<h2 id={titleId}>{title}</h2>
		<div class="body">{@render children()}</div>
		{#if actions}<div class="actions">{@render actions()}</div>{/if}
	</div>
</div>

<style>
	.backdrop {
		position: fixed;
		inset: 0;
		display: grid;
		place-items: center;
		padding: var(--space-3);
		background: rgb(0 0 0 / 0.5);
		z-index: 100;
	}
	.panel {
		width: min(32rem, 100%);
		max-height: 90dvh;
		overflow: auto;
		padding: var(--space-4);
		background: var(--color-bg);
		border-radius: var(--radius);
	}
	h2 {
		margin-top: 0;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		gap: var(--space-2);
		margin-top: var(--space-4);
	}
</style>
