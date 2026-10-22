<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { normalizeQuery } from '#lib/forms/validate.js';
	import { parseScope, searchLabel, searchPath, type Scope } from '#lib/search-scope.js';
	import ScopeMenu from './ScopeMenu.svelte';

	interface Props {
		/** Wait this long after the last key before searching (S-502 AC1). */
		debounceMs?: number;
	}

	let { debounceMs = 300 }: Props = $props();

	const onSearchPage = $derived(page.url.pathname === resolve('/search'));
	const urlQuery = $derived(onSearchPage ? (page.url.searchParams.get('q') ?? '') : '');

	let value = $state('');
	let scope = $state<Scope>('all');

	// On the search page the address decides the scope; elsewhere the last choice is kept.
	$effect(() => {
		if (onSearchPage) scope = parseScope(page.url.searchParams.get('scope'));
	});
	let input: HTMLInputElement | undefined = $state();
	let timer: ReturnType<typeof setTimeout> | undefined;

	// Follow the address (back/forward, links) unless the user is typing here.
	$effect(() => {
		const fromUrl = urlQuery;
		if (typeof document !== 'undefined' && document.activeElement !== input) value = fromUrl;
	});

	function go() {
		clearTimeout(timer);
		const q = normalizeQuery(value);
		// While already searching, replace the history entry so typing does not fill the back button.
		// SvelteKit 3: `reset: false` keeps focus and scroll (it replaced keepFocus and noScroll, which it ignores).
		const options = { replace: onSearchPage, reset: false };
		void goto(resolve(searchPath(q, scope)), options);
	}

	// A new scope searches again at once when there is something to search (S-702 AC2).
	// Focus stays on the scope button, as the menu-button pattern expects.
	function chooseScope(next: Scope) {
		scope = next;
		if (normalizeQuery(value) || onSearchPage) go();
	}

	function typed() {
		clearTimeout(timer);
		timer = setTimeout(go, debounceMs);
	}

	function submit(event: SubmitEvent) {
		event.preventDefault();
		go();
	}

	$effect(() => () => clearTimeout(timer));
</script>

<form role="search" onsubmit={submit}>
	<ScopeMenu value={scope} onchange={chooseScope} />
	<label class="visually-hidden" for="site-search">{searchLabel(scope)}</label>
	<input
		bind:this={input}
		id="site-search"
		type="search"
		placeholder={searchLabel(scope)}
		autocomplete="off"
		maxlength="200"
		bind:value
		oninput={typed}
	/>
</form>

<style>
	/* One bordered box holding the scope button and the text field (S-702 AC1). */
	form {
		display: flex;
		align-items: stretch;
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
		background: var(--color-bg);
	}
	form:focus-within {
		border-color: var(--color-primary);
	}
	form > :global(.scope [aria-haspopup]) {
		border-radius: var(--radius) 0 0 var(--radius);
	}
	input {
		flex: 1;
		min-width: 0;
		min-height: var(--touch-target);
		padding-inline: var(--space-3);
		border: 0;
		border-radius: 0 var(--radius) var(--radius) 0;
		background: transparent;
		font: inherit;
	}
</style>
