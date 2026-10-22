/**
 * GraphQL client (ADR-010): a thin typed wrapper over fetch.
 * All data operations go through POST /graphql (NFR-ARCH-03). Errors follow ADR-005:
 * extensions.code, optional extensions.fieldErrors, extensions.correlationId.
 */

export type ErrorCode =
	| 'VALIDATION_FAILED'
	| 'NOT_FOUND'
	| 'CONFLICT'
	| 'UNAVAILABLE'
	| 'INTERNAL'
	// Client-side only:
	| 'NETWORK'
	| 'HTTP_ERROR'
	| 'BAD_RESPONSE';

export interface FieldError {
	field: string;
	messageKey: string;
}

export class ApiError extends Error {
	readonly code: ErrorCode;
	readonly fieldErrors: Record<string, string>;
	readonly correlationId?: string;
	/** Remaining server extensions, for example blockingMovies or creditCount. */
	readonly details: Record<string, unknown>;

	constructor(
		code: ErrorCode,
		message: string,
		options: {
			fieldErrors?: Record<string, string>;
			correlationId?: string;
			details?: Record<string, unknown>;
		} = {}
	) {
		super(message);
		this.name = 'ApiError';
		this.code = code;
		this.fieldErrors = options.fieldErrors ?? {};
		this.correlationId = options.correlationId;
		this.details = options.details ?? {};
	}
}

export interface GraphQLErrorPayload {
	message: string;
	path?: (string | number)[];
	extensions?: Record<string, unknown>;
}

export interface GraphQLResult<T> {
	data: T | null;
	errors: GraphQLErrorPayload[];
}

export interface RequestOptions {
	signal?: AbortSignal;
	/** Injected in tests. */
	fetchImpl?: typeof fetch;
	endpoint?: string;
}

const KNOWN_CODES = new Set<string>([
	'VALIDATION_FAILED',
	'NOT_FOUND',
	'CONFLICT',
	'UNAVAILABLE',
	'INTERNAL'
]);

/** True for an aborted request, including aborts with a custom reason (signal.abort(reason)). */
export const isAbortError = (e: unknown, signal?: AbortSignal): boolean =>
	(e instanceof DOMException && e.name === 'AbortError') || signal?.aborted === true;

export function toApiError(payload: GraphQLErrorPayload): ApiError {
	const ext = payload.extensions ?? {};
	const code = (
		typeof ext.code === 'string' && KNOWN_CODES.has(ext.code) ? ext.code : 'INTERNAL'
	) as ErrorCode;
	const fieldErrors: Record<string, string> = {};
	if (Array.isArray(ext.fieldErrors)) {
		for (const fe of ext.fieldErrors as Partial<FieldError>[]) {
			if (typeof fe?.field === 'string' && typeof fe?.messageKey === 'string') {
				fieldErrors[fe.field] = fe.messageKey;
			}
		}
	}
	const { code: _code, fieldErrors: _fe, correlationId, ...details } = ext;
	void _code;
	void _fe;
	return new ApiError(code, payload.message, {
		fieldErrors,
		correlationId: typeof correlationId === 'string' ? correlationId : undefined,
		details
	});
}

/** Sends a query and returns data plus any (possibly partial) errors without throwing for GraphQL errors. */
export async function gqlRaw<T>(
	query: string,
	variables: Record<string, unknown> = {},
	options: RequestOptions = {}
): Promise<GraphQLResult<T>> {
	const doFetch = options.fetchImpl ?? fetch;
	let response: Response;
	try {
		response = await doFetch(options.endpoint ?? '/graphql', {
			method: 'POST',
			headers: { 'content-type': 'application/json', accept: 'application/json' },
			body: JSON.stringify({ query, variables }),
			signal: options.signal,
			// The API is public and uses no cookies or credentials (D2).
			credentials: 'omit'
		});
	} catch (e) {
		if (isAbortError(e, options.signal)) throw e;
		throw new ApiError('NETWORK', 'Network request failed');
	}

	let body: { data?: T | null; errors?: GraphQLErrorPayload[] } | null;
	try {
		body = await response.json();
	} catch (e) {
		// An abort while the body is still being read must stay an abort (stale search guard, S-502).
		if (isAbortError(e, options.signal)) throw e;
		if (!response.ok) throw new ApiError('HTTP_ERROR', `HTTP ${response.status}`);
		throw new ApiError('BAD_RESPONSE', 'The server returned an unreadable response');
	}
	if (typeof body !== 'object' || body === null || Array.isArray(body)) {
		throw new ApiError(
			response.ok ? 'BAD_RESPONSE' : 'HTTP_ERROR',
			'The server returned an unexpected response'
		);
	}
	if (!response.ok && !body.errors) {
		throw new ApiError('HTTP_ERROR', `HTTP ${response.status}`);
	}
	return { data: body.data ?? null, errors: body.errors ?? [] };
}

/** Like gqlRaw, but throws an ApiError for the first GraphQL error. */
export async function gql<T>(
	query: string,
	variables: Record<string, unknown> = {},
	options: RequestOptions = {}
): Promise<T> {
	const { data, errors } = await gqlRaw<T>(query, variables, options);
	if (errors.length > 0) throw toApiError(errors[0]);
	if (data === null) throw new ApiError('BAD_RESPONSE', 'The server returned no data');
	return data;
}
