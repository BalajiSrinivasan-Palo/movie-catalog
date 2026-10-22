<script lang="ts">
	import { tick } from 'svelte';
	import { EARLIEST_YEAR, maxYear } from '#lib/forms/validate.js';
	import { clampYear, moveYear, pageStart, yearsOnPage } from '#lib/years.js';

	interface Props {
		label: string;
		/** The year as typed, or '' for none (the form validates it, S-702 AC6). */
		value?: string;
		error?: string;
		min?: number;
		max?: number;
	}

	let {
		label,
		value = $bindable(''),
		error,
		min = EARLIEST_YEAR,
		max = maxYear()
	}: Props = $props();

	const id = $props.id();
	const errorId = `${id}-error`;
	const chooseLabel = $derived(`Choose ${label.toLowerCase()}`);

	let open = $state(false);
	let start = $state(0); // first year of the shown page
	let focused = $state(0); // the year with tabindex 0 (roving focus)
	let alignEnd = $state(false); // open towards the left when there is no room on the right
	let root: HTMLDivElement | undefined = $state();
	let toggle: HTMLButtonElement | undefined = $state();
	let popover: HTMLDivElement | undefined = $state();

	const selected = $derived(/^\d{4}$/.test(value.trim()) ? Number(value.trim()) : null);
	const years = $derived(yearsOnPage(start, min, max));
	const thisYear = new Date().getFullYear();

	async function show() {
		focused = clampYear(selected ?? thisYear, min, max);
		start = pageStart(focused);
		alignEnd = false;
		open = true;
		await tick();
		const box = popover?.getBoundingClientRect();
		if (box && box.right > window.innerWidth - 8) alignEnd = true;
		focusYear();
	}

	function hide(returnFocus: boolean) {
		open = false;
		if (returnFocus) toggle?.focus();
	}

	function focusYear() {
		popover?.querySelector<HTMLButtonElement>(`[data-year="${focused}"]`)?.focus();
	}

	async function moveTo(year: number) {
		focused = clampYear(year, min, max);
		start = pageStart(focused);
		await tick();
		focusYear();
	}

	function pick(year: number | null) {
		value = year === null ? '' : String(year);
		hide(true);
	}

	// The range shown in the header: only years that are offered.
	const first = $derived(Math.max(start, min));
	const last = $derived(Math.min(start + years.length - 1, max));
	const atStart = $derived(start <= min);
	const atEnd = $derived(start + years.length - 1 >= max);

	// aria-disabled, not disabled: a focused button that became disabled would drop focus to <body>.
	function page(direction: -1 | 1) {
		if ((direction < 0 && atStart) || (direction > 0 && atEnd)) return;
		start += direction * years.length;
		// Keep focus on a real year of the new page, so arrow keys go on working.
		focused = clampYear(start + (focused - pageStart(focused)), min, max);
	}

	function yearKey(event: KeyboardEvent, year: number) {
		const next = moveYear(year, event.key);
		if (next !== null) {
			event.preventDefault();
			void moveTo(next);
		}
	}

	function popoverKey(event: KeyboardEvent) {
		if (event.key === 'Escape') {
			event.preventDefault();
			hide(true);
		}
	}

	function outside(event: PointerEvent) {
		if (open && root && !root.contains(event.target as Node)) hide(false);
	}
</script>

<svelte:window onpointerdown={outside} />

<div class="field" bind:this={root}>
	<label for={id}>{label}</label>
	<div class="row">
		<input
			{id}
			bind:value
			inputmode="numeric"
			autocomplete="off"
			placeholder="yyyy"
			aria-invalid={error ? true : undefined}
			aria-describedby={error ? errorId : undefined}
		/>
		<button
			bind:this={toggle}
			type="button"
			class="toggle"
			aria-label={chooseLabel}
			aria-haspopup="dialog"
			aria-expanded={open}
			onclick={() => (open ? hide(false) : show())}
		>
			<!-- A small calendar drawn in CSS, so no icon font is needed (ADR-010). -->
			<span class="calendar" aria-hidden="true"></span>
		</button>
	</div>
	{#if error}<p id={errorId} class="error">{error}</p>{/if}

	{#if open}
		<!-- Esc is handled once for the whole popover. -->
		<div
			bind:this={popover}
			class="popover"
			class:end={alignEnd}
			role="dialog"
			aria-label={chooseLabel}
			tabindex="-1"
			onkeydown={popoverKey}
		>
			<div class="head">
				<button
					type="button"
					class="nav"
					aria-label="Earlier years"
					aria-disabled={atStart}
					onclick={() => page(-1)}>‹</button
				>
				<p aria-live="polite">{first}–{last}</p>
				<button
					type="button"
					class="nav"
					aria-label="Later years"
					aria-disabled={atEnd}
					onclick={() => page(1)}>›</button
				>
			</div>
			<div class="grid" role="group" aria-label="Years {first} to {last}">
				{#each years as year, i (i)}
					{#if year === null}
						<span class="gap" aria-hidden="true"></span>
					{:else}
						<button
							type="button"
							class="year"
							data-year={year}
							tabindex={year === focused ? 0 : -1}
							aria-pressed={year === selected}
							onclick={() => pick(year)}
							onkeydown={(e) => yearKey(e, year)}>{year}</button
						>
					{/if}
				{/each}
			</div>
			<div class="foot">
				<button type="button" class="link" onclick={() => pick(null)}>Clear</button>
				<button type="button" class="link" onclick={() => pick(clampYear(thisYear, min, max))}
					>This year</button
				>
			</div>
		</div>
	{/if}
</div>

<style>
	.field {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}
	label {
		font-weight: 600;
	}
	.row {
		display: flex;
		border: 2px solid var(--color-border);
		border-radius: var(--radius);
		background: var(--color-bg);
	}
	.row:has(input[aria-invalid='true']) {
		border-color: var(--color-danger);
	}
	.row:focus-within {
		border-color: var(--color-primary);
	}
	input {
		flex: 1;
		min-width: 0;
		min-height: var(--touch-target);
		padding: 0 var(--space-2);
		border: 0;
		border-radius: var(--radius) 0 0 var(--radius);
		background: transparent;
		font: inherit;
	}
	button {
		font: inherit;
		cursor: pointer;
	}
	.toggle {
		flex: none;
		display: grid;
		place-items: center;
		min-width: var(--touch-target);
		min-height: var(--touch-target);
		border: 0;
		border-radius: 0 var(--radius) var(--radius) 0;
		background: transparent;
		color: var(--color-text);
	}
	.calendar {
		width: 1.1rem;
		height: 1rem;
		border: 2px solid currentColor;
		border-top-width: 5px;
		border-radius: 3px;
	}
	.error {
		margin: 0;
		color: var(--color-danger);
		font-weight: 600;
	}
	.popover {
		position: absolute;
		top: calc(100% + var(--space-1));
		left: 0;
		z-index: 10;
		width: min(19rem, calc(100vw - 2 * var(--space-3)));
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-card);
		background: var(--color-bg);
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.12);
	}
	.popover.end {
		left: auto;
		right: 0;
	}
	.head,
	.foot {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.head p {
		margin: 0;
		font-weight: 700;
	}
	.nav,
	.link {
		min-width: var(--touch-target);
		min-height: var(--touch-target);
		border: 0;
		border-radius: var(--radius);
		background: transparent;
		color: var(--color-primary);
		font-weight: 600;
	}
	.nav {
		font-size: 1.4rem;
	}
	.nav[aria-disabled='true'] {
		color: var(--color-border);
		cursor: default;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: var(--space-1);
		margin-block: var(--space-2);
	}
	.year {
		min-height: var(--touch-target);
		border: 0;
		border-radius: var(--radius);
		background: transparent;
		color: var(--color-text);
	}
	.year:hover {
		background: var(--color-surface);
	}
	.year[aria-pressed='true'] {
		background: var(--color-primary);
		color: var(--color-primary-text);
		font-weight: 700;
	}
</style>
