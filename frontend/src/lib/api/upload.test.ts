import { describe, it, expect, vi } from 'vitest';
import { ApiError } from './client.js';
import { uploadArtwork } from './upload.js';

/** Just enough of XMLHttpRequest to drive the helper from a test. */
class FakeXhr {
	upload: { onprogress: ((e: ProgressEvent) => void) | null } = { onprogress: null };
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	ontimeout: (() => void) | null = null;
	onabort: (() => void) | null = null;
	status = 200;
	responseText = '';
	timeout = 0;
	method = '';
	url = '';
	headers: Record<string, string> = {};
	body: FormData | null = null;
	aborted = false;
	open(method: string, url: string) {
		this.method = method;
		this.url = url;
	}
	setRequestHeader(name: string, value: string) {
		this.headers[name] = value;
	}
	send(body: FormData) {
		this.body = body;
	}
	abort() {
		this.aborted = true;
		this.onabort?.();
	}
	progress(loaded: number, total: number, computable = true) {
		this.upload.onprogress?.({ loaded, total, lengthComputable: computable } as ProgressEvent);
	}
	respond(status: number, text: string) {
		this.status = status;
		this.responseText = text;
		this.onload?.();
	}
}

const png = new File([new Uint8Array([1, 2, 3])], 'poster.png', { type: 'image/png' });
const artwork = {
	thumbnailUrl: '/t',
	url: '/o',
	width: 1,
	height: 2,
	contentType: 'image/png',
	sizeBytes: 3
};

function start(options: Parameters<typeof uploadArtwork>[2] = {}) {
	const xhr = new FakeXhr();
	const handle = uploadArtwork('m1', png, {
		createXhr: () => xhr as unknown as XMLHttpRequest,
		...options
	});
	return { xhr, handle };
}

describe('uploadArtwork', () => {
	it('sends a GraphQL multipart request with the preflight header, in spec order', async () => {
		const { xhr, handle } = start();
		expect(xhr.method).toBe('POST');
		expect(xhr.url).toBe('/graphql');
		expect(xhr.headers['Apollo-Require-Preflight']).toBe('true');
		expect([...xhr.body!.keys()]).toEqual(['operations', 'map', '0']);
		const operations = JSON.parse(xhr.body!.get('operations') as string);
		expect(operations.variables).toEqual({ movieId: 'm1', file: null });
		expect(operations.query).toContain('uploadArtwork(movieId: $movieId, file: $file)');
		expect(JSON.parse(xhr.body!.get('map') as string)).toEqual({ '0': ['variables.file'] });
		expect((xhr.body!.get('0') as File).name).toBe('poster.png');
		xhr.respond(200, JSON.stringify({ data: { uploadArtwork: { artwork } } }));
		await expect(handle.promise).resolves.toEqual(artwork);
	});

	it('reports whole-percent progress and ignores events without a length', () => {
		const onProgress = vi.fn();
		const { xhr } = start({ onProgress });
		xhr.progress(1, 3);
		xhr.progress(5, 10, false);
		xhr.progress(3, 3);
		xhr.progress(5, 0);
		expect(onProgress.mock.calls.map((c) => c[0])).toEqual([33, 100]);
	});

	it('rejects with the server error and its field errors', async () => {
		const { xhr, handle } = start();
		xhr.respond(
			200,
			JSON.stringify({
				data: null,
				errors: [
					{
						message: 'Some of the information is not valid.',
						extensions: {
							code: 'VALIDATION_FAILED',
							fieldErrors: [{ field: 'file', messageKey: 'artwork.corrupt' }],
							correlationId: 'abc'
						}
					}
				]
			})
		);
		const error = await handle.promise.catch((e) => e);
		expect(error).toBeInstanceOf(ApiError);
		expect(error.code).toBe('VALIDATION_FAILED');
		expect(error.fieldErrors).toEqual({ file: 'artwork.corrupt' });
		expect(error.correlationId).toBe('abc');
	});

	it('maps network failure, timeout and cancellation', async () => {
		const failed = start();
		failed.xhr.onerror?.();
		await expect(failed.handle.promise).rejects.toMatchObject({ code: 'NETWORK' });

		const slow = start({ timeoutMs: 1234 });
		expect(slow.xhr.timeout).toBe(1234);
		slow.xhr.ontimeout?.();
		await expect(slow.handle.promise).rejects.toMatchObject({ code: 'NETWORK' });

		const cancelled = start();
		cancelled.handle.abort();
		expect(cancelled.xhr.aborted).toBe(true);
		await expect(cancelled.handle.promise).rejects.toMatchObject({ name: 'AbortError' });
	});

	it('treats an unreadable or empty answer as an error', async () => {
		const html = start();
		html.xhr.respond(403, '<html>no</html>');
		await expect(html.handle.promise).rejects.toMatchObject({ code: 'HTTP_ERROR' });

		const garbage = start();
		garbage.xhr.respond(200, 'not json');
		await expect(garbage.handle.promise).rejects.toMatchObject({ code: 'BAD_RESPONSE' });

		const empty = start();
		empty.xhr.respond(200, JSON.stringify({ data: { uploadArtwork: { artwork: null } } }));
		await expect(empty.handle.promise).rejects.toMatchObject({ code: 'BAD_RESPONSE' });
	});
});
