import { describe, it, expect } from 'vitest';
import { clampYear, moveYear, pageStart, yearsOnPage } from './years.js';

describe('year grid maths (S-702 AC4, AC5)', () => {
	it('aligns pages to multiples of 20', () => {
		expect(pageStart(1995)).toBe(1980);
		expect(pageStart(1980)).toBe(1980);
		expect(pageStart(1999)).toBe(1980);
		expect(pageStart(2000)).toBe(2000);
	});

	it('leaves gaps for years outside the allowed range', () => {
		const first = yearsOnPage(1880, 1888, 2031);
		expect(first.slice(0, 8)).toEqual(Array(8).fill(null));
		expect(first[8]).toBe(1888);
		const last = yearsOnPage(2020, 1888, 2031);
		expect(last[11]).toBe(2031);
		expect(last.slice(12)).toEqual(Array(8).fill(null));
		expect(yearsOnPage(1980, 1888, 2031)).toHaveLength(20);
	});

	it('moves by one year, one row of four, or one page of twenty', () => {
		expect(moveYear(1990, 'ArrowRight')).toBe(1991);
		expect(moveYear(1990, 'ArrowLeft')).toBe(1989);
		expect(moveYear(1990, 'ArrowDown')).toBe(1994);
		expect(moveYear(1990, 'ArrowUp')).toBe(1986);
		expect(moveYear(1990, 'PageDown')).toBe(2010);
		expect(moveYear(1990, 'PageUp')).toBe(1970);
		expect(moveYear(1990, 'Enter')).toBeNull();
	});

	it('clamps to the allowed range', () => {
		expect(clampYear(1800, 1888, 2031)).toBe(1888);
		expect(clampYear(2100, 1888, 2031)).toBe(2031);
		expect(clampYear(1999, 1888, 2031)).toBe(1999);
	});
});
