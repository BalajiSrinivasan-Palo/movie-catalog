<script lang="ts">
	import { tick } from 'svelte';
	import { SCOPES, type Scope } from '#lib/search-scope.js';

	interface Props {
		value: Scope;
		onchange: (value: Scope) => void;
	}

	let { value, onchange }: Props = $props();

	const id = $props.id();
	let open = $state(false);
	let root: HTMLDivElement | undefined = $state();
	let button: HTMLButtonElement | undefined = $state();
	let items: HTMLButtonElement[] = $state([]);

	const current = $derived(SCOPES.find((s) => s.value === value) ?? SCOPES[0]);

	async function show(focusIndex: number) {
		open = true;
		await tick();
		items[(focusIndex + SCOPES.length) % SCOPES.length]?.focus();
	}

	function hide(returnFocus: boolean) {
		open = false;
		if (returnFocus) button?.focus();
	}

	function choose(scope: Scope) {
		hide(true);
		if (scope !== value) onchange(scope);
	}

	const checkedIndex = () =>
		Math.max(
			0,
			SCOPES.findIndex((s) => s.value === value)
		);

	// Menu-button keyboard pattern (S-702 AC1).
	function buttonKey(event: KeyboardEvent) {
		if (event.key === 'ArrowDown') {
			event.preventDefault();
			void show(checkedIndex());
		} else if (event.key === 'ArrowUp') {
			event.preventDefault();
			void show(SCOPES.length - 1);
		}
	}

	function itemKey(event: KeyboardEvent, index: number) {
		const moves: Record<string, number> = {
			ArrowDown: index + 1,
			ArrowUp: index - 1,
			Home: 0,
			End: SCOPES.length - 1
		};
		if (event.key in moves) {
			event.preventDefault();
			items[(moves[event.key] + SCOPES.length) % SCOPES.length]?.focus();
		} else if (event.key === 'Escape') {
			event.preventDefault();
			hide(true);
		} else if (event.key === 'Tab') {
			hide(false);
		}
	}

	function outside(event: PointerEvent) {
		if (open && root && !root.contains(event.target as Node)) hide(false);
	}
</script>

<svelte:window onpointerdown={outside} />

<div class="scope" bind:this={root}>
	<button
		bind:this={button}
		type="button"
		aria-haspopup="menu"
		aria-expanded={open}
		aria-controls="{id}-menu"
		onclick={() => (open ? hide(false) : show(checkedIndex()))}
		onkeydown={buttonKey}
	>
		<span class="visually-hidden">Search in: </span>{current.label}
		<span class="chevron" class:up={open} aria-hidden="true"></span>
	</button>
	{#if open}
		<ul id="{id}-menu" role="menu" aria-label="Search in">
			{#each SCOPES as scope, i (scope.value)}
				<!-- Items are buttons, so Enter and Space choose them natively. -->
				<li role="none">
					<button
						bind:this={items[i]}
						type="button"
						role="menuitemradio"
						aria-checked={scope.value === value}
						tabindex="-1"
						onclick={() => choose(scope.value)}
						onkeydown={(e) => itemKey(e, i)}
					>
						{scope.label}{#if scope.value === 'people'}<span class="hint">
								(cast and crew)</span
							>{/if}
					</button>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.scope {
		position: relative;
		flex: none;
	}
	[aria-haspopup] {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		min-height: var(--touch-target);
		min-width: var(--touch-target);
		height: 100%;
		padding-inline: var(--space-3);
		border: 0;
		border-right: 1px solid var(--color-border);
		background: var(--color-surface);
		color: var(--color-text);
		font: inherit;
		font-weight: 600;
		cursor: pointer;
	}
	/* A small CSS triangle, so no icon font is needed (ADR-010). */
	.chevron {
		border-inline: 0.3rem solid transparent;
		border-top: 0.35rem solid currentColor;
	}
	.chevron.up {
		border-top: 0;
		border-bottom: 0.35rem solid currentColor;
	}
	ul {
		--focus-ring: 3px solid var(--color-hero-focus);
		position: absolute;
		top: calc(100% + var(--space-1));
		left: 0;
		z-index: 10;
		min-width: 14rem;
		margin: 0;
		padding: var(--space-1) 0;
		list-style: none;
		border-radius: var(--radius);
		background: var(--color-hero-bg);
		color: var(--color-hero-text);
	}
	[role='menuitemradio'] {
		display: flex;
		align-items: center;
		width: 100%;
		min-height: var(--touch-target);
		padding-inline: var(--space-3);
		border: 0;
		background: transparent;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}
	[role='menuitemradio']:hover,
	[role='menuitemradio']:focus {
		background: var(--color-hero-pill);
	}
	[role='menuitemradio']:focus-visible {
		outline: var(--focus-ring);
		outline-offset: -3px;
	}
	[role='menuitemradio'][aria-checked='true'] {
		color: var(--color-hero-focus);
		font-weight: 700;
	}
	/* aria-checked carries the state; the empty alternative keeps "✓" out of the item's name. */
	[role='menuitemradio'][aria-checked='true']::after {
		content: '✓';
		content: '✓' / '';
		margin-left: auto;
	}
	.hint {
		margin-left: var(--space-1);
		color: var(--color-hero-muted);
		font-weight: 400;
	}
</style>
