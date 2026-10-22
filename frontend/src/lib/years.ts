/** Year grid maths for the year picker (S-702 AC4, AC5). Pages are 20 years, aligned to multiples of 20. */
export const PAGE_SIZE = 20;
export const COLUMNS = 4;

export const pageStart = (year: number) => Math.floor(year / PAGE_SIZE) * PAGE_SIZE;

/** The 20 years of the page holding `start`; years outside min..max are null (shown as gaps). */
export function yearsOnPage(start: number, min: number, max: number): (number | null)[] {
	return Array.from({ length: PAGE_SIZE }, (_, i) => {
		const year = start + i;
		return year >= min && year <= max ? year : null;
	});
}

export const clampYear = (year: number, min: number, max: number) =>
	Math.min(max, Math.max(min, year));

/** Where a key moves the focused year; null when the key is not a move. */
export function moveYear(year: number, key: string): number | null {
	const steps: Record<string, number> = {
		ArrowLeft: -1,
		ArrowRight: 1,
		ArrowUp: -COLUMNS,
		ArrowDown: COLUMNS,
		PageUp: -PAGE_SIZE,
		PageDown: PAGE_SIZE
	};
	return key in steps ? year + steps[key] : null;
}
