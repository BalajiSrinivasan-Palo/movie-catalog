<script lang="ts">
	import type { Snippet } from 'svelte';
	import { resolve } from '$app/paths';

	interface Props {
		/** Current pathname, used to mark the active section for assistive technology. */
		currentPath: string;
		/** Slot for the search box (added in story S-502). */
		search?: Snippet;
	}

	let { currentPath, search }: Props = $props();

	const links = [
		{ href: '/movies', label: 'Movies' },
		{ href: '/people', label: 'People' }
	] as const;

	const isActive = (href: string) => currentPath === href || currentPath.startsWith(href + '/');
</script>

<header class="app-header">
	<div class="container bar">
		<a class="brand" href={resolve('/movies')}>Movie Catalog</a>
		<nav aria-label="Main">
			<ul>
				{#each links as link (link.href)}
					<li>
						<a href={resolve(link.href)} aria-current={isActive(link.href) ? 'page' : undefined}>
							{link.label}
						</a>
					</li>
				{/each}
			</ul>
		</nav>
		{#if search}
			<div class="search">{@render search()}</div>
		{/if}
	</div>
</header>

<style>
	.app-header {
		background: var(--color-bg);
		border-bottom: 1px solid var(--color-border);
	}
	.bar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-4);
		padding-block: var(--space-2);
	}
	.brand {
		font-weight: 700;
		font-size: 1.1rem;
		color: var(--color-text);
		text-decoration: none;
		min-height: var(--touch-target);
		display: inline-flex;
		align-items: center;
	}
	ul {
		display: flex;
		gap: var(--space-1);
		list-style: none;
		margin: 0;
		padding: 0;
	}
	nav a {
		display: inline-flex;
		align-items: center;
		min-height: var(--touch-target);
		min-width: var(--touch-target);
		padding-inline: var(--space-3);
		border-radius: var(--radius-pill);
		color: var(--color-muted);
		font-weight: 600;
		text-decoration: none;
	}
	nav a:hover {
		color: var(--color-text);
	}
	/* The current section is a tinted pill rather than a solid block (S-701 AC1). */
	nav a[aria-current='page'] {
		background: var(--color-accent-tint);
		color: var(--color-primary);
	}
	/* Below 600 px the search box takes its own full-width row. */
	.search {
		flex: 1 1 100%;
	}
	@media (min-width: 600px) {
		.search {
			margin-left: auto;
			flex: 0 1 20rem;
		}
	}
</style>
