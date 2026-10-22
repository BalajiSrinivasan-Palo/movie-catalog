<script lang="ts">
	import { uploadArtwork, type UploadHandle } from '#lib/api/upload.js';
	import { ApiError, isAbortError } from '#lib/api/client.js';
	import { validateArtworkFile } from '#lib/forms/validate.js';
	import { describeError, messageFor } from '#lib/messages.js';
	import { isFileDrag } from '#lib/drag.js';
	import Button from './Button.svelte';
	import ProgressBar from './ProgressBar.svelte';

	interface Props {
		movieId: string;
		hasArtwork: boolean;
		/** Called when an upload ends in any way, so the page can reload the movie and show what is really stored (EC-08). */
		onsettled: () => void;
		/** Called after a successful upload. */
		onuploaded?: () => void;
	}

	let { movieId, hasArtwork, onsettled, onuploaded }: Props = $props();

	type Phase = 'idle' | 'uploading' | 'processing' | 'error' | 'cancelled';
	let phase = $state<Phase>('idle');
	let percent = $state(0);
	let message = $state('');
	let file = $state<File | null>(null); // kept so Retry needs no new choice
	let handle: UploadHandle | null = null;
	let dragging = $state(false);
	let note = $state(''); // for example when several files were dropped

	const busy = $derived(phase === 'uploading' || phase === 'processing');

	function errorText(e: unknown): string {
		if (e instanceof ApiError && e.fieldErrors.file) return messageFor(e.fieldErrors.file);
		return describeError(e);
	}

	function choose(event: Event & { currentTarget: HTMLInputElement }) {
		const picked = event.currentTarget.files?.[0];
		event.currentTarget.value = ''; // the same file can be chosen again
		if (!picked) return;
		note = '';
		accept(picked);
	}

	// Drag and drop (S-701 AC11 to AC13). A dropped file takes the same path as a chosen one.
	function dragOver(event: DragEvent) {
		if (busy || !isFileDrag(event)) return;
		event.preventDefault();
		if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
		dragging = true;
	}

	function dragLeave(event: DragEvent & { currentTarget: HTMLElement }) {
		// Moving over a child of the zone also fires dragleave; only leaving the zone counts.
		if (!event.currentTarget.contains(event.relatedTarget as Node | null)) dragging = false;
	}

	function drop(event: DragEvent) {
		dragging = false;
		if (!isFileDrag(event)) return;
		event.preventDefault();
		const files = event.dataTransfer?.files;
		if (busy || !files || files.length === 0) return;
		note = files.length > 1 ? 'Only the first file was used.' : '';
		accept(files[0]);
	}

	function accept(picked: File) {
		file = picked;
		const problem = validateArtworkFile(picked);
		if (problem) {
			phase = 'error';
			message = messageFor(problem);
			return;
		}
		void start();
	}

	async function start() {
		if (!file || busy) return;
		phase = 'uploading';
		percent = 0;
		message = '';
		handle = uploadArtwork(movieId, file, {
			onProgress: (p) => {
				percent = p;
				if (p >= 100 && phase === 'uploading') phase = 'processing'; // sent; the server is still working
			}
		});
		try {
			await handle.promise;
			phase = 'idle';
			file = null;
			onuploaded?.();
		} catch (e) {
			if (isAbortError(e)) {
				phase = 'cancelled';
				message = 'Upload cancelled. The existing artwork is unchanged.';
			} else {
				phase = 'error';
				message = errorText(e);
			}
		} finally {
			handle = null;
			onsettled();
		}
	}

	function cancel() {
		handle?.abort();
	}
</script>

<section class="uploader" aria-labelledby="uploader-title-{movieId}">
	<h2 id="uploader-title-{movieId}">{hasArtwork ? 'Replace artwork' : 'Add artwork'}</h2>

	<!-- The label is the drop zone; the file input inside it stays focusable and keeps its text label. -->
	<label
		class="zone"
		class:dragging
		class:busy
		ondragenter={dragOver}
		ondragover={dragOver}
		ondragleave={dragLeave}
		ondrop={drop}
	>
		<span class="visually-hidden">Image file (JPEG, PNG or WebP, up to 5 MB)</span>
		<input
			class="visually-hidden"
			type="file"
			accept="image/jpeg,image/png,image/webp"
			disabled={busy}
			onchange={choose}
		/>
		{#if dragging}
			<span class="main" aria-hidden="true">Drop to upload</span>
			{#if hasArtwork}<span class="hint" aria-hidden="true">Replaces the current artwork</span>{/if}
		{:else}
			<span class="main" aria-hidden="true">Drag an image here or <u>choose a file</u></span>
			<span class="hint" aria-hidden="true">JPEG, PNG or WebP · up to 5 MB</span>
		{/if}
	</label>
	<p class="note" aria-live="polite">{note}</p>

	{#if phase === 'uploading'}
		<ProgressBar value={percent} label="Uploading artwork" />
		<p role="status">Uploading… {percent}%</p>
	{:else if phase === 'processing'}
		<ProgressBar label="Processing artwork" />
		<p role="status">Processing…</p>
	{/if}

	<!-- Once the whole file is sent the gateway forwards it whatever the browser does, so cancel would be a false promise. -->
	{#if phase === 'uploading'}
		<Button variant="secondary" onclick={cancel}>Cancel upload</Button>
	{/if}

	{#if phase === 'error'}
		<p role="alert" class="error">{message}</p>
		{#if file && validateArtworkFile(file) === null}
			<Button onclick={start}>Retry upload</Button>
		{/if}
	{:else if phase === 'cancelled'}
		<p role="status">{message}</p>
	{/if}
</section>

<style>
	.uploader {
		display: grid;
		gap: var(--space-2);
		margin-block: var(--space-3);
		max-width: 28rem;
		min-width: 0;
	}
	h2 {
		margin: 0;
		font-size: 1.1rem;
	}
	.zone {
		/* Containing block for the visually hidden input, so it cannot widen the page. */
		position: relative;
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		min-height: calc(2 * var(--touch-target));
		padding: var(--space-4) var(--space-3);
		border: 2px dashed var(--color-muted);
		border-radius: var(--radius-card);
		background: var(--color-bg);
		text-align: center;
		cursor: pointer;
	}
	.zone:hover {
		border-color: var(--color-primary);
	}
	.zone:has(input:focus-visible) {
		outline: var(--focus-ring);
		outline-offset: 2px;
	}
	.zone.dragging {
		border-style: solid;
		border-color: var(--color-primary);
		background: var(--color-accent-tint);
		color: var(--color-primary);
	}
	.zone.busy {
		cursor: progress;
		border-color: var(--color-border);
	}
	.main {
		font-weight: 600;
	}
	.main u {
		color: var(--color-primary);
	}
	.hint {
		font-size: 0.875rem;
		color: var(--color-muted);
	}
	.dragging .hint {
		color: var(--color-primary);
	}
	/* Always rendered, even when empty, so screen readers announce text put into it. */
	.note {
		margin: 0;
		color: var(--color-muted);
	}
	.error {
		color: var(--color-danger);
		margin: 0;
	}
</style>
