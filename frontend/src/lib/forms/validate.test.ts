import { describe, it, expect } from 'vitest';
import {
	normalizeQuery,
	validateArtworkFile,
	validateCredit,
	validateMovie,
	validatePerson,
	validateSearchQuery
} from './validate';

const now = new Date('2026-10-22T12:00:00Z');
const movie = (over: Partial<Parameters<typeof validateMovie>[0]> = {}) =>
	validateMovie(
		{ title: 'Heat', releaseYear: '1995', runtimeMinutes: '', synopsis: '', genres: [], ...over },
		now
	);

describe('validateMovie', () => {
	it('accepts a minimal movie and trims the title', () => {
		const r = movie({ title: '  Heat ' });
		expect(r.errors).toEqual({});
		expect(r.value).toMatchObject({ title: 'Heat', releaseYear: 1995, runtimeMinutes: null });
	});

	it('checks the title length', () => {
		expect(movie({ title: '   ' }).errors.title).toBe('movie.title.invalid');
		expect(movie({ title: 'x'.repeat(201) }).errors.title).toBe('movie.title.invalid');
		expect(movie({ title: 'x'.repeat(200) }).errors).toEqual({});
	});

	it('checks the year range 1888 to now + 5 and rejects non-integers', () => {
		expect(movie({ releaseYear: '1888' }).errors).toEqual({});
		expect(movie({ releaseYear: '2031' }).errors).toEqual({});
		for (const bad of ['1887', '2032', '', 'abc', '19.5'])
			expect(movie({ releaseYear: bad }).errors.releaseYear).toBe('movie.year.invalid');
	});

	it('checks the optional runtime', () => {
		expect(movie({ runtimeMinutes: '120' }).value?.runtimeMinutes).toBe(120);
		for (const bad of ['0', '1001', 'x', '1.5'])
			expect(movie({ runtimeMinutes: bad }).errors.runtimeMinutes).toBe('movie.runtime.invalid');
	});

	it('checks synopsis length and genre count', () => {
		expect(movie({ synopsis: 's'.repeat(2001) }).errors.synopsis).toBe('movie.synopsis.too_long');
		expect(movie({ genres: ['ACTION', 'WAR', 'DRAMA', 'CRIME', 'COMEDY'] }).errors).toEqual({});
		expect(
			movie({ genres: ['ACTION', 'WAR', 'DRAMA', 'CRIME', 'COMEDY', 'HORROR'] }).errors.genres
		).toBe('movie.genres.invalid');
	});

	it('reports every problem at once and produces no value', () => {
		const r = movie({ title: '', releaseYear: '1' });
		expect(Object.keys(r.errors).sort()).toEqual(['releaseYear', 'title']);
		expect(r.value).toBeNull();
	});
});

const person = (over: Partial<Parameters<typeof validatePerson>[0]> = {}) =>
	validatePerson({ name: 'Ada', birthDate: '', bio: '', ...over }, now);

describe('validatePerson', () => {
	it('accepts name only and trims it', () => {
		expect(person({ name: ' Ada ' }).value).toEqual({ name: 'Ada', birthDate: null, bio: '' });
	});
	it('rejects blank and over-long names', () => {
		expect(person({ name: ' ' }).errors.name).toBe('person.name.invalid');
		expect(person({ name: 'n'.repeat(201) }).errors.name).toBe('person.name.invalid');
	});
	it('checks birth date bounds', () => {
		expect(person({ birthDate: '1850-01-01' }).errors).toEqual({});
		expect(person({ birthDate: '2026-10-22' }).errors).toEqual({});
		for (const bad of ['1849-12-31', '2026-10-23', 'nope', '2000-02-31x'])
			expect(person({ birthDate: bad }).errors.birthDate).toBe('person.birthdate.invalid');
	});
	it('limits the bio', () => {
		expect(person({ bio: 'b'.repeat(2001) }).errors.bio).toBe('person.bio.too_long');
	});
});

describe('validateCredit', () => {
	it('allows a character only for actors', () => {
		expect(validateCredit({ role: 'ACTOR', characterName: 'Neo' })).toEqual({});
		expect(validateCredit({ role: 'DIRECTOR', characterName: '' })).toEqual({});
		expect(validateCredit({ role: 'DIRECTOR', characterName: 'Neo' }).characterName).toBe(
			'credit.character.invalid'
		);
		expect(validateCredit({ role: 'ACTOR', characterName: 'c'.repeat(201) }).characterName).toBe(
			'credit.character.invalid'
		);
	});
});

describe('validateArtworkFile', () => {
	const MB = 1024 * 1024;
	it('accepts JPEG, PNG and WebP up to exactly 5 MiB', () => {
		expect(validateArtworkFile({ type: 'image/jpeg', size: 1 })).toBeNull();
		expect(validateArtworkFile({ type: 'image/png', size: 5 * MB })).toBeNull();
		expect(validateArtworkFile({ type: 'image/webp', size: 100 })).toBeNull();
	});
	it('rejects empty, oversized and other types with the message key', () => {
		expect(validateArtworkFile({ type: 'image/png', size: 0 })).toBe('artwork.empty');
		expect(validateArtworkFile({ type: 'image/png', size: 5 * MB + 1 })).toBe('artwork.too_large');
		expect(validateArtworkFile({ type: 'image/gif', size: 10 })).toBe('artwork.type.unsupported');
		expect(validateArtworkFile({ type: '', size: 10 })).toBe('artwork.type.unsupported');
	});
});

describe('validateSearchQuery', () => {
	it('needs 2 to 100 characters after trimming', () => {
		for (const bad of ['', ' ', 'a', ' a ', '   ', 'x'.repeat(101)]) {
			expect(validateSearchQuery(bad)).toBe('search.query.invalid');
		}
		for (const good of ['ab', ' ab ', 'x'.repeat(100), '% _ \\', "o'b"]) {
			expect(validateSearchQuery(good)).toBeNull();
		}
		expect(normalizeQuery('  nol ')).toBe('nol');
	});
});
