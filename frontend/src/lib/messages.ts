/**
 * User-facing text for every message key and error code (ADR-005, NFR-UX-06).
 * Keys come from docs/requirements/BRD.md section 7; a test fails when a key has no text.
 * Text never contains internal identifiers or technical details.
 */
import { ApiError } from './api/client';

export const messages: Record<string, string> = {
	'movie.title.invalid': 'Enter a title between 1 and 200 characters.',
	'movie.year.invalid': 'Enter a release year between 1888 and five years from now.',
	'movie.runtime.invalid': 'Enter a runtime between 1 and 1000 minutes, or leave it empty.',
	'movie.synopsis.too_long': 'The synopsis can have at most 2000 characters.',
	'movie.genres.invalid': 'Choose up to 5 different genres from the list.',
	'movie.duplicate': 'A movie with this title and release year already exists.',
	'person.name.invalid': 'Enter a name between 1 and 200 characters.',
	'person.birthdate.invalid': 'Enter a valid birth date that is not in the future.',
	'person.bio.too_long': 'The biography can have at most 2000 characters.',
	'person.has_credits': 'This person still has credits and cannot be deleted.',
	'person.delete.check_unavailable':
		'Cannot check whether this person has credits right now, so nothing was deleted. Please try again.',
	'credit.duplicate': 'This person already has that role in this movie.',
	'credit.person.not_found': 'This person no longer exists.',
	'credit.character.invalid': 'A character name is only allowed for actors, up to 200 characters.',
	'credit.role.invalid': 'Choose a role from the list.',
	'artwork.type.unsupported': 'Use a JPEG, PNG or WebP image.',
	'artwork.too_large': 'The image is larger than 5 MB.',
	'artwork.empty': 'The selected file is empty.',
	'artwork.corrupt': 'The image could not be read. Try another file.',
	'artwork.dimensions.too_large': 'The image is larger than 6000 × 6000 pixels.',
	'artwork.protocol': 'The upload was not sent correctly. Please try again.',
	'artwork.not_found': 'This movie has no artwork.',
	'paging.invalid': 'The page request is not valid.',
	'sort.invalid': 'The sort option is not valid.',
	'filter.year.invalid': 'Enter a valid year range, with the first year not after the second.',
	'id.invalid': 'The link is not valid.',
	'search.query.invalid': 'Enter between 2 and 100 characters to search.'
};

const codeMessages: Record<string, string> = {
	VALIDATION_FAILED: 'Some of the information is not valid. Check the highlighted fields.',
	NOT_FOUND: 'This item no longer exists. It may have been deleted.',
	CONFLICT: 'This change conflicts with existing data.',
	UNAVAILABLE: 'The service is not available right now. Please try again.',
	INTERNAL: 'Something went wrong on our side. Please try again.',
	NETWORK: 'Cannot reach the server. Check your connection and try again.',
	HTTP_ERROR: 'The server could not handle the request. Please try again.',
	BAD_RESPONSE: 'The server sent an unexpected response. Please try again.'
};

const FALLBACK = 'Something went wrong. Please try again.';

/** Text for a message key, or a generic fallback so users never see a raw key. */
export function messageFor(key: string): string {
	return messages[key] ?? FALLBACK;
}

/** Operation-level text for any error. Field-level text comes from fieldMessages(). */
export function describeError(error: unknown): string {
	if (error instanceof ApiError) {
		// A specific key from the server (for example a degraded dependency) is more helpful than the generic text.
		const key = error.details.messageKey;
		if (error.code === 'UNAVAILABLE' && typeof key === 'string' && messages[key])
			return messages[key];
		return codeMessages[error.code] ?? FALLBACK;
	}
	return FALLBACK;
}

/** Maps an ApiError's field errors to user text, keyed by input field name. */
export function fieldMessages(error: unknown): Record<string, string> {
	if (!(error instanceof ApiError)) return {};
	return Object.fromEntries(
		Object.entries(error.fieldErrors).map(([field, key]) => [field, messageFor(key)])
	);
}
