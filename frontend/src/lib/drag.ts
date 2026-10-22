/** True when the drag carries files, not text or a link (S-701 AC12, AC13). */
export function isFileDrag(event: DragEvent): boolean {
	return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}
