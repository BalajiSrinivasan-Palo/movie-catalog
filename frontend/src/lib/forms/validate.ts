/**
 * Client-side mirror of the BRD section 7 rules, so users get instant feedback. The server stays
 * authoritative (NFR-SEC-01): these functions only decide what to show before a request is sent.
 * Each returns a message key or null; text comes from messages.ts.
 */
import type { MovieInput, PersonInput } from '#lib/api/catalog.js';

export type Errors = Record<string, string>;

export const EARLIEST_YEAR = 1888;
export const maxYear = (now = new Date()) => now.getFullYear() + 5;

export function validateMovie(
	input: {
		title: string;
		releaseYear: string;
		runtimeMinutes: string;
		synopsis: string;
		genres: string[];
	},
	now = new Date()
): { errors: Errors; value: MovieInput | null } {
	const errors: Errors = {};
	const title = input.title.trim();
	if (title.length < 1 || title.length > 200) errors.title = 'movie.title.invalid';

	const year = Number(input.releaseYear);
	if (
		input.releaseYear.trim() === '' ||
		!Number.isInteger(year) ||
		year < EARLIEST_YEAR ||
		year > maxYear(now)
	) {
		errors.releaseYear = 'movie.year.invalid';
	}

	const runtimeText = input.runtimeMinutes.trim();
	const runtime = runtimeText === '' ? null : Number(runtimeText);
	if (runtime !== null && (!Number.isInteger(runtime) || runtime < 1 || runtime > 1000)) {
		errors.runtimeMinutes = 'movie.runtime.invalid';
	}
	if (input.synopsis.length > 2000) errors.synopsis = 'movie.synopsis.too_long';
	if (input.genres.length > 5) errors.genres = 'movie.genres.invalid';

	if (Object.keys(errors).length > 0) return { errors, value: null };
	return {
		errors,
		value: {
			title,
			releaseYear: year,
			runtimeMinutes: runtime,
			synopsis: input.synopsis,
			genres: input.genres as MovieInput['genres']
		}
	};
}

const EARLIEST_BIRTH = '1850-01-01';

export function validatePerson(
	input: { name: string; birthDate: string; bio: string },
	now = new Date()
): { errors: Errors; value: PersonInput | null } {
	const errors: Errors = {};
	const name = input.name.trim();
	if (name.length < 1 || name.length > 200) errors.name = 'person.name.invalid';

	const date = input.birthDate.trim();
	if (date !== '') {
		const today = now.toISOString().slice(0, 10);
		const real = /^\d{4}-\d{2}-\d{2}$/.test(date) && !Number.isNaN(Date.parse(date));
		if (!real || date < EARLIEST_BIRTH || date > today)
			errors.birthDate = 'person.birthdate.invalid';
	}
	if (input.bio.length > 2000) errors.bio = 'person.bio.too_long';

	if (Object.keys(errors).length > 0) return { errors, value: null };
	return { errors, value: { name, birthDate: date === '' ? null : date, bio: input.bio } };
}

/** Credit character names are only for actors (BRD "Credit"). */
export function validateCredit(input: { role: string; characterName: string }): Errors {
	const errors: Errors = {};
	const name = input.characterName.trim();
	if (name !== '' && (input.role !== 'ACTOR' || name.length > 200)) {
		errors.characterName = 'credit.character.invalid';
	}
	return errors;
}

export const ARTWORK_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const ARTWORK_MAX_BYTES = 5 * 1024 * 1024;

/**
 * Quick check before uploading, so a wrong file fails at once instead of after the transfer (S-304 AC3).
 * It trusts the browser's idea of the type, which a user can fake, so the server decides from the bytes (BR-08).
 */
export function validateArtworkFile(file: { type: string; size: number }): string | null {
	if (file.size === 0) return 'artwork.empty';
	if (!ARTWORK_TYPES.includes(file.type)) return 'artwork.type.unsupported';
	if (file.size > ARTWORK_MAX_BYTES) return 'artwork.too_large';
	return null;
}

export const SEARCH_MIN = 2;
export const SEARCH_MAX = 100;

/** The text sent to the server: trimmed, as the server trims too (BRD section 7). */
export const normalizeQuery = (text: string) => text.trim();

/** A message key when the query cannot be searched, otherwise null. No request is sent for a bad query (S-502 AC2). */
export function validateSearchQuery(text: string): string | null {
	const length = normalizeQuery(text).length;
	return length < SEARCH_MIN || length > SEARCH_MAX ? 'search.query.invalid' : null;
}
