<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { createPerson } from '#lib/api/catalog.js';
	import { toasts } from '#lib/stores/toast.svelte.js';
	import PersonForm from '#lib/components/PersonForm.svelte';
</script>

<svelte:head><title>Add person · Movie Catalog</title></svelte:head>
<h1>Add person</h1>
<PersonForm
	submitLabel="Create person"
	onsubmit={async (input) => {
		const person = await createPerson(input);
		toasts.success('Person created.');
		await goto(resolve('/people/[id]', { id: person.id }));
	}}
	oncancel={() => goto(resolve('/people'))}
/>
