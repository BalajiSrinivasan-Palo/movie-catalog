<script lang="ts">
	interface Props {
		/** 0 to 100. Leave undefined for an indeterminate bar (work with unknown duration). */
		value?: number;
		label: string;
	}

	let { value, label }: Props = $props();

	const clamped = $derived(
		value === undefined ? undefined : Math.min(100, Math.max(0, Math.round(value)))
	);
</script>

<div
	class="progress"
	role="progressbar"
	aria-label={label}
	aria-valuemin="0"
	aria-valuemax="100"
	aria-valuenow={clamped}
>
	<div
		class="bar"
		class:indeterminate={clamped === undefined}
		style:width={clamped === undefined ? undefined : `${clamped}%`}
	></div>
</div>
{#if clamped !== undefined}<span class="visually-hidden">{clamped}%</span>{/if}

<style>
	.progress {
		height: 0.75rem;
		border-radius: var(--radius);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		overflow: hidden;
	}
	.bar {
		height: 100%;
		background: var(--color-primary);
		transition: width 0.15s linear;
	}
	.indeterminate {
		width: 40%;
		animation: slide 1.2s ease-in-out infinite;
	}
	@keyframes slide {
		0% {
			margin-left: -40%;
		}
		100% {
			margin-left: 100%;
		}
	}
</style>
