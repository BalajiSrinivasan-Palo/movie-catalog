<script lang="ts">
	import { page } from '$app/state';
	import PersonDetailView from '#lib/views/PersonDetailView.svelte';
</script>

<PersonDetailView id={page.params.id ?? ''} />
