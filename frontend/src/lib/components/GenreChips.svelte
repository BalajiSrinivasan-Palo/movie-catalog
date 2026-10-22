<script lang="ts">
	import { GENRES, type Genre } from '#lib/api/catalog.js';

	interface Props {
		/** Selected genres; none selected means all genres. */
		value: Genre[];
		/** Called with the new selection, in the fixed genre order. */
		onchange: (value: Genre[]) => void;
	}

	let { value, onchange }: Props = $props();

	function toggle(genre: Genre, on: boolean) {
		const next = on ? [...value, genre] : value.filter((g) => g !== genre);
		onchange(GENRES.map(([g]) => g).filter((g) => next.includes(g)));
	}
</script>

<!-- Native checkboxes keep keyboard and screen-reader behaviour; only their look is a chip (S-702 AC7). -->
<fieldset class="chips">
	<legend class="visually-hidden">Genre</legend>
	{#each GENRES as [genre, label] (genre)}
		<label class="chip">
			<input
				class="visually-hidden"
				type="checkbox"
				checked={value.includes(genre)}
				onchange={(e) => toggle(genre, e.currentTarget.checked)}
			/>
			{label}
			{#if value.includes(genre)}<span class="remove" aria-hidden="true">✕</span>{/if}
		</label>
	{/each}
</fieldset>

<style>
	/* Below 600 px the chips are one row that scrolls inside itself, so they do not push the posters
	   down; the padding leaves room for the focus ring. The page itself never scrolls sideways.
	   position: relative makes the row the containing block of the visually hidden checkboxes, which
	   would otherwise escape the scroll box and widen the page. */
	.chips {
		position: relative;
		display: flex;
		gap: var(--space-2);
		margin: 0;
		padding: 4px;
		border: 0;
		min-width: 0;
		overflow-x: auto;
		scrollbar-width: thin;
	}
	@media (min-width: 600px) {
		.chips {
			flex-wrap: wrap;
			overflow-x: visible;
		}
	}
	.chip {
		flex: none;
		white-space: nowrap;
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		min-height: var(--touch-target);
		padding-inline: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-pill);
		background: var(--color-bg);
		color: var(--color-text);
		cursor: pointer;
	}
	.chip:hover {
		border-color: var(--color-muted);
	}
	.chip:has(input:checked) {
		background: var(--color-primary);
		border-color: var(--color-primary);
		color: var(--color-primary-text);
	}
	.chip:has(input:focus-visible) {
		outline: var(--focus-ring);
		outline-offset: 2px;
	}
	.remove {
		font-size: 0.85em;
	}
</style>
