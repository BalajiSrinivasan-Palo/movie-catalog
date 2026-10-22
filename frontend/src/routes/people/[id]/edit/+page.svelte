<script lang="ts">
	import { page } from '$app/state';
	import PersonEditView from '#lib/views/PersonEditView.svelte';
</script>

<PersonEditView id={page.params.id ?? ''} />
