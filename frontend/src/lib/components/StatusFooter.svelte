<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchHealth, type ServiceHealth } from '#lib/api/health.js';

	interface Props {
		/** Injected in tests. */
		load?: () => Promise<ServiceHealth[]>;
	}

	let { load = () => fetchHealth() }: Props = $props();

	type View =
		| { kind: 'loading' }
		| { kind: 'ok' }
		| { kind: 'degraded'; down: string[] }
		| { kind: 'unreachable' };

	let view = $state<View>({ kind: 'loading' });

	onMount(() => {
		let active = true;
		load().then(
			(services) => {
				if (!active) return;
				const down = services.filter((s) => s.status !== 'UP').map((s) => s.service);
				view = down.length === 0 ? { kind: 'ok' } : { kind: 'degraded', down };
			},
			() => {
				if (active) view = { kind: 'unreachable' };
			}
		);
		return () => {
			active = false;
		};
	});
</script>

<!-- Quiet while checking and when every service is up; it speaks only when something is wrong (D23).
     The live region is always rendered, so a warning put into it is announced. -->
<footer
	class="status container"
	class:quiet={view.kind === 'loading' || view.kind === 'ok'}
	aria-label="Backend status"
>
	<p role="status">
		{#if view.kind === 'degraded'}
			Some features may not work: {view.down.join(', ')} not responding.
		{:else if view.kind === 'unreachable'}
			Cannot reach the server. Please try again later.
		{/if}
	</p>
</footer>

<style>
	.status {
		padding-block: var(--space-3);
		color: var(--color-text-muted);
		font-size: 0.875rem;
	}
	.quiet {
		padding-block: 0;
	}
	p {
		margin: 0;
	}
</style>
