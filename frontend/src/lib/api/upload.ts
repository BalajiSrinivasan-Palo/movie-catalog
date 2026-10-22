/**
 * Artwork upload (S-304). fetch cannot report upload progress, so this one call uses XMLHttpRequest.
 * It sends a GraphQL multipart request (graphql-multipart-request-spec): `operations`, `map`, then the file.
 */
import { ApiError, toApiError, type GraphQLErrorPayload } from './client.js';
import type { Artwork } from './catalog.js';

const UPLOAD_MUTATION = `mutation UploadArtwork($movieId: ID!, $file: Upload!) {
	uploadArtwork(movieId: $movieId, file: $file) { artwork { thumbnailUrl url width height contentType sizeBytes } }
}`;

export interface UploadOptions {
	/** Whole percent of bytes sent, 0 to 100. 100 means the file has left the browser, not that it is stored. */
	onProgress?: (percent: number) => void;
	endpoint?: string;
	timeoutMs?: number;
	/** Injected in tests. */
	createXhr?: () => XMLHttpRequest;
}

export interface UploadHandle {
	promise: Promise<Artwork>;
	/** Cancels the request. The promise then rejects with an AbortError. */
	abort: () => void;
}

export function uploadArtwork(
	movieId: string,
	file: File,
	options: UploadOptions = {}
): UploadHandle {
	const xhr = options.createXhr?.() ?? new XMLHttpRequest();

	const promise = new Promise<Artwork>((resolve, reject) => {
		const form = new FormData();
		form.append(
			'operations',
			JSON.stringify({ query: UPLOAD_MUTATION, variables: { movieId, file: null } })
		);
		form.append('map', JSON.stringify({ '0': ['variables.file'] }));
		form.append('0', file, file.name);

		xhr.open('POST', options.endpoint ?? '/graphql');
		xhr.setRequestHeader('Accept', 'application/json');
		// A custom header makes browsers preflight the request, so other sites cannot post files (CSRF).
		xhr.setRequestHeader('Apollo-Require-Preflight', 'true');
		xhr.timeout = options.timeoutMs ?? 60_000;

		xhr.upload.onprogress = (event) => {
			if (event.lengthComputable && event.total > 0) {
				options.onProgress?.(Math.min(100, Math.floor((event.loaded * 100) / event.total)));
			}
		};
		xhr.onerror = () => reject(new ApiError('NETWORK', 'Network request failed'));
		xhr.ontimeout = () => reject(new ApiError('NETWORK', 'The upload timed out'));
		xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
		xhr.onload = () => {
			let body: {
				data?: { uploadArtwork?: { artwork?: Artwork | null } | null } | null;
				errors?: GraphQLErrorPayload[];
			};
			try {
				body = JSON.parse(xhr.responseText);
			} catch {
				reject(
					xhr.status >= 200 && xhr.status < 300
						? new ApiError('BAD_RESPONSE', 'The server returned an unreadable response')
						: new ApiError('HTTP_ERROR', `HTTP ${xhr.status}`)
				);
				return;
			}
			if (body?.errors && body.errors.length > 0) {
				reject(toApiError(body.errors[0]));
				return;
			}
			const artwork = body?.data?.uploadArtwork?.artwork;
			if (artwork) resolve(artwork);
			else reject(new ApiError('BAD_RESPONSE', 'The server returned no artwork'));
		};
		xhr.send(form);
	});

	return { promise, abort: () => xhr.abort() };
}
