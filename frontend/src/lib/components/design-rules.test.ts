import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const here = import.meta.dirname;

/** NFR-UX-01: interactive shared components reserve a 44 px touch target (checked statically, since jsdom has no layout). */
describe('touch targets', () => {
	it.each([
		'Button.svelte',
		'TextField.svelte',
		'Select.svelte',
		'ToastRegion.svelte',
		'AppHeader.svelte',
		'GenreChips.svelte',
		'HeroBand.svelte',
		'ScopeMenu.svelte',
		'YearPicker.svelte'
	])('%s uses the --touch-target token', (file) => {
		expect(readFileSync(join(here, file), 'utf8')).toContain('var(--touch-target)');
	});

	it('defines the token as 44px', () => {
		expect(readFileSync(join(here, '../styles/global.css'), 'utf8')).toMatch(
			/--touch-target:\s*44px/
		);
	});
});

/** NFR-A11Y-01: text colours reach 4.5:1 against the page background. */
describe('colour contrast', () => {
	const css = readFileSync(join(here, '../styles/global.css'), 'utf8');
	const token = (name: string) => css.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`))![1];
	const luminance = (hex: string) => {
		const [r, g, b] = [1, 3, 5]
			.map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
			.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	};
	const ratio = (a: string, b: string) => {
		const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
		return (hi + 0.05) / (lo + 0.05);
	};

	it.each(['color-text', 'color-muted', 'color-primary', 'color-danger', 'color-success'])(
		'%s on white is at least 4.5:1',
		(name) => {
			expect(ratio(token(name), token('color-bg'))).toBeGreaterThanOrEqual(4.5);
		}
	);

	it('white text on primary, danger and success backgrounds is at least 4.5:1', () => {
		for (const name of ['color-primary', 'color-danger', 'color-success']) {
			expect(ratio('#ffffff', token(name))).toBeGreaterThanOrEqual(4.5);
		}
	});

	it('muted text on the surface colour is at least 4.5:1', () => {
		expect(ratio(token('color-muted'), token('color-surface'))).toBeGreaterThanOrEqual(4.5);
	});

	it('primary text on the accent tint (nav pill, avatars) is at least 4.5:1 (S-701)', () => {
		expect(ratio(token('color-primary'), token('color-accent-tint'))).toBeGreaterThanOrEqual(4.5);
	});

	it('hero band text, muted text, pills and focus ring are at least 4.5:1 (S-701 AC9)', () => {
		const bg = token('color-hero-bg');
		for (const name of ['color-hero-text', 'color-hero-muted', 'color-hero-focus']) {
			expect(ratio(token(name), bg), name).toBeGreaterThanOrEqual(4.5);
		}
		expect(ratio(token('color-hero-text'), token('color-hero-pill'))).toBeGreaterThanOrEqual(4.5);
	});

	it('a selected genre chip (page colour on text colour) is at least 4.5:1 (S-701)', () => {
		expect(ratio(token('color-bg'), token('color-text'))).toBeGreaterThanOrEqual(4.5);
	});

	it('does not render user text as HTML anywhere in the app source', () => {
		const walk = (dir: string): string[] =>
			readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
				e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]
			);
		const svelteFiles = walk(join(here, '../..')).filter((f) => f.endsWith('.svelte'));
		expect(svelteFiles.length).toBeGreaterThan(5);
		for (const file of svelteFiles) expect(readFileSync(file, 'utf8')).not.toContain('{@html');
	});
});
