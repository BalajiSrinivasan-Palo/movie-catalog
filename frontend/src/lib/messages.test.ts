import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ApiError } from './api/client';
import { describeError, fieldMessages, messageFor, messages } from './messages';

/** Reads the message keys from BRD section 7 so a new key without user text fails the build (NFR-UX-06). */
function keysFromBrd(): string[] {
	const brd = readFileSync(resolve(process.cwd(), '../docs/requirements/BRD.md'), 'utf8');
	const start = brd.indexOf('## 7. Data and validation rules');
	const end = brd.indexOf('## 8. Business rules');
	expect(start).toBeGreaterThan(-1);
	expect(end).toBeGreaterThan(start);
	const section = brd.slice(start, end);
	const keys = new Set<string>();
	for (const match of section.matchAll(/`([a-z]+(?:\.[a-z_]+)+)`/g)) keys.add(match[1]);
	return [...keys];
}

describe('message catalogue', () => {
	it('finds the expected number of keys in the BRD', () => {
		expect(keysFromBrd().length).toBeGreaterThanOrEqual(20);
	});

	it.each(keysFromBrd())('has user text for %s', (key) => {
		expect(messages[key], `missing text for ${key}`).toBeTruthy();
	});

	it('never shows technical detail in any text', () => {
		for (const text of Object.values(messages)) {
			expect(text).not.toMatch(/exception|stack|sql|null|undefined|\bgrpc\b|uuid/i);
		}
	});

	it('falls back to a generic sentence for an unknown key', () => {
		expect(messageFor('does.not.exist')).toBe('Something went wrong. Please try again.');
	});

	it('describes every error code and unknown errors without raw codes', () => {
		for (const code of [
			'VALIDATION_FAILED',
			'NOT_FOUND',
			'CONFLICT',
			'UNAVAILABLE',
			'INTERNAL',
			'NETWORK',
			'HTTP_ERROR',
			'BAD_RESPONSE'
		] as const) {
			const text = describeError(new ApiError(code, 'x'));
			expect(text).not.toContain(code);
			expect(text.length).toBeGreaterThan(10);
		}
		expect(describeError(new Error('boom'))).toBe('Something went wrong. Please try again.');
		expect(describeError(undefined)).toBe('Something went wrong. Please try again.');
	});

	it('maps field errors to text keyed by field', () => {
		const error = new ApiError('VALIDATION_FAILED', 'x', {
			fieldErrors: { title: 'movie.title.invalid' }
		});
		expect(fieldMessages(error)).toEqual({ title: messages['movie.title.invalid'] });
		expect(fieldMessages(new Error('x'))).toEqual({});
	});

	it('uses the specific text when an unavailable error carries a known message key', () => {
		const specific = new ApiError('UNAVAILABLE', 'x', {
			details: { messageKey: 'person.delete.check_unavailable' }
		});
		expect(describeError(specific)).toMatch(/nothing was deleted/);
		expect(
			describeError(new ApiError('UNAVAILABLE', 'x', { details: { messageKey: 'nope' } }))
		).toBe(describeError(new ApiError('UNAVAILABLE', 'x')));
	});
});
