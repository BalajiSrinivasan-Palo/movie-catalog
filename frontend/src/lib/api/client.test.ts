import { describe, it, expect, vi } from 'vitest';
import { ApiError, gql, gqlRaw, isAbortError, toApiError } from './client';

const json = (body: unknown, init: ResponseInit = { status: 200 }) =>
	new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' }, ...init });

describe('gql client', () => {
	it('posts the query and variables as JSON and returns data', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(json({ data: { movies: { items: [] } } }));
		const data = await gql<{ movies: { items: unknown[] } }>(
			'query { movies }',
			{ a: 1 },
			{ fetchImpl }
		);
		expect(data.movies.items).toEqual([]);
		const [url, init] = fetchImpl.mock.calls[0];
		expect(url).toBe('/graphql');
		expect(init.method).toBe('POST');
		expect(JSON.parse(init.body)).toEqual({ query: 'query { movies }', variables: { a: 1 } });
	});

	it('maps a validation error with field errors and correlation id', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(
			json({
				data: null,
				errors: [
					{
						message: 'invalid',
						extensions: {
							code: 'VALIDATION_FAILED',
							correlationId: 'c-1',
							fieldErrors: [{ field: 'title', messageKey: 'movie.title.invalid' }]
						}
					}
				]
			})
		);
		const error = (await gql('q', {}, { fetchImpl }).catch((e) => e)) as ApiError;
		expect(error).toBeInstanceOf(ApiError);
		expect(error.code).toBe('VALIDATION_FAILED');
		expect(error.fieldErrors).toEqual({ title: 'movie.title.invalid' });
		expect(error.correlationId).toBe('c-1');
	});

	it('keeps extra extensions such as blockingMovies in details', () => {
		const error = toApiError({
			message: 'conflict',
			extensions: { code: 'CONFLICT', blockingMovies: [{ id: '1' }], creditCount: 12 }
		});
		expect(error.details).toEqual({ blockingMovies: [{ id: '1' }], creditCount: 12 });
	});

	it('treats an unknown or missing code as INTERNAL', () => {
		expect(toApiError({ message: 'x', extensions: { code: 'WEIRD' } }).code).toBe('INTERNAL');
		expect(toApiError({ message: 'x' }).code).toBe('INTERNAL');
	});

	it('ignores malformed field errors', () => {
		const error = toApiError({
			message: 'x',
			extensions: {
				code: 'VALIDATION_FAILED',
				fieldErrors: [{ field: 1 }, null, { field: 'a', messageKey: 'k' }]
			}
		});
		expect(error.fieldErrors).toEqual({ a: 'k' });
	});

	it('turns a network failure into a NETWORK error', async () => {
		const fetchImpl = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
		await expect(gql('q', {}, { fetchImpl })).rejects.toMatchObject({ code: 'NETWORK' });
	});

	it('rethrows aborts unchanged so callers can ignore them', async () => {
		const fetchImpl = vi.fn().mockRejectedValue(new DOMException('aborted', 'AbortError'));
		const error = await gql('q', {}, { fetchImpl }).catch((e) => e);
		expect(isAbortError(error)).toBe(true);
	});

	it('reports a non-JSON error page as HTTP_ERROR', async () => {
		const fetchImpl = vi
			.fn()
			.mockResolvedValue(new Response('<html>bad gateway</html>', { status: 502 }));
		await expect(gql('q', {}, { fetchImpl })).rejects.toMatchObject({ code: 'HTTP_ERROR' });
	});

	it('reports unreadable JSON on a 200 as BAD_RESPONSE', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(new Response('not json', { status: 200 }));
		await expect(gql('q', {}, { fetchImpl })).rejects.toMatchObject({ code: 'BAD_RESPONSE' });
	});

	it('reports a non-OK response without GraphQL errors as HTTP_ERROR', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(json({ message: 'nope' }, { status: 500 }));
		await expect(gql('q', {}, { fetchImpl })).rejects.toMatchObject({ code: 'HTTP_ERROR' });
	});

	it('reports missing data as BAD_RESPONSE', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(json({}));
		await expect(gql('q', {}, { fetchImpl })).rejects.toMatchObject({ code: 'BAD_RESPONSE' });
	});

	it('gqlRaw returns partial data together with errors', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(
			json({
				data: { a: 1 },
				errors: [{ message: 'partial', extensions: { code: 'UNAVAILABLE' } }]
			})
		);
		const result = await gqlRaw<{ a: number }>('q', {}, { fetchImpl });
		expect(result.data).toEqual({ a: 1 });
		expect(result.errors).toHaveLength(1);
	});

	it('passes the abort signal to fetch and honours a custom endpoint', async () => {
		const fetchImpl = vi.fn().mockResolvedValue(json({ data: { ok: true } }));
		const controller = new AbortController();
		await gql('q', {}, { fetchImpl, signal: controller.signal, endpoint: '/other' });
		expect(fetchImpl.mock.calls[0][0]).toBe('/other');
		expect(fetchImpl.mock.calls[0][1].signal).toBe(controller.signal);
	});
});
