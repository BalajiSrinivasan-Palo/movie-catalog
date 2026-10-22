import { describe, it, expect } from 'vitest';
import { isFileDrag } from './drag.js';

const drag = (types: string[] | null) =>
	({ dataTransfer: types === null ? null : { types } }) as unknown as DragEvent;

describe('isFileDrag (S-701 AC12, AC13)', () => {
	it('is true when the drag carries files', () => {
		expect(isFileDrag(drag(['Files']))).toBe(true);
		expect(isFileDrag(drag(['text/uri-list', 'Files']))).toBe(true);
	});

	it('is false for text, links or no data', () => {
		expect(isFileDrag(drag(['text/plain']))).toBe(false);
		expect(isFileDrag(drag([]))).toBe(false);
		expect(isFileDrag(drag(null))).toBe(false);
	});
});
