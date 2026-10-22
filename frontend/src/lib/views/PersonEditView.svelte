<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { fetchPerson, updatePerson } from '#lib/api/catalog.js';
	import { createLoader } from '#lib/stores/loader.svelte.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import ErrorPanel from '#lib/components/ErrorPanel.svelte';
	import PersonForm from '#lib/components/PersonForm.svelte';
	import Skeleton from '#lib/components/Skeleton.svelte';

	interface Props {
		id: string;
	}

	let { id }: Props = $props();

	const loader = createLoader((signal) => fetchPerson(id, 0, { signal }));

	$effect(() => {
		void id;
		loader.load();
		return () => loader.cancel();
	});
</script>

<svelte:head><title>Edit person · Movie Catalog</title></svelte:head>
<h1>Edit person</h1>
{#if loader.state === 'loading' && !loader.data}
	<Skeleton lines={5} label="Loading person…" />
{:else if loader.state === 'error' && !loader.data}
	<h1>Edit person</h1>
	<ErrorPanel error={loader.error} onretry={() => loader.load()} />
	<p><a href={resolve('/people')}>Back to people</a></p>
{:else if loader.data}
	<PersonForm
		initial={{ name: loader.data.name, birthDate: loader.data.birthDate, bio: loader.data.bio }}
		submitLabel="Save changes"
		onsubmit={async (input) => {
			await updatePerson(id, input);
			toasts.success('Person saved.');
			await goto(resolve('/people/[id]', { id }));
		}}
		oncancel={() => goto(resolve('/people/[id]', { id }))}
	/>
{/if}
