import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { ApiError } from '#lib/api/client.js';
import * as upload from '#lib/api/upload.js';
import ArtworkUploader from './ArtworkUploader.svelte';

vi.mock('#lib/api/upload.js', () => ({ uploadArtwork: vi.fn() }));
const uploadArtwork = vi.mocked(upload.uploadArtwork);

type Controls = {
	progress: (p: number) => void;
	resolve: () => void;
	reject: (e: unknown) => void;
	abort: () => void;
	aborted: boolean;
};

/** Makes uploadArtwork a promise the test settles by hand. */
function fakeUpload(): Controls {
	const controls = {} as Controls;
	uploadArtwork.mockImplementationOnce((_id, _file, options) => {
		controls.progress = (p) => options?.onProgress?.(p);
		controls.aborted = false;
		controls.abort = () => {
			controls.aborted = true;
			controls.reject(new DOMException('Upload cancelled', 'AbortError'));
		};
		return {
			promise: new Promise((resolve, reject) => {
				controls.resolve = () => resolve({} as never);
				controls.reject = reject;
			}),
			abort: controls.abort
		};
	});
	return controls;
}

const file = (type = 'image/png', size = 100, name = 'poster.png') => {
	const f = new File([new Uint8Array(1)], name, { type });
	Object.defineProperty(f, 'size', { value: size });
	return f;
};

function setup(hasArtwork = false) {
	const onsettled = vi.fn();
	const onuploaded = vi.fn();
	render(ArtworkUploader, { movieId: 'm1', hasArtwork, onsettled, onuploaded });
	const input = screen.getByLabelText(/Image file/) as HTMLInputElement;
	return { input, onsettled, onuploaded, user: userEvent.setup({ applyAccept: false }) };
}

beforeEach(() => vi.resetAllMocks());

describe('ArtworkUploader', () => {
	it('shows a progress bar with values, then Processing at 100% until the answer arrives', async () => {
		const controls = fakeUpload();
		const { input, user, onsettled, onuploaded } = setup();
		await user.upload(input, file());
		const bar = await screen.findByRole('progressbar', { name: 'Uploading artwork' });
		controls.progress(42);
		await waitFor(() => expect(bar).toHaveAttribute('aria-valuenow', '42'));
		expect(screen.getByRole('status')).toHaveTextContent('Uploading… 42%');
		controls.progress(100);
		await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Processing…'));
		expect(screen.getByRole('progressbar', { name: 'Processing artwork' })).not.toHaveAttribute(
			'aria-valuenow'
		);
		controls.resolve();
		await waitFor(() => expect(onuploaded).toHaveBeenCalledOnce());
		expect(onsettled).toHaveBeenCalledOnce();
		expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
	});

	it('cancel aborts the request and says the existing artwork is unchanged', async () => {
		const controls = fakeUpload();
		const { input, user, onsettled, onuploaded } = setup(true);
		await user.upload(input, file());
		await user.click(await screen.findByRole('button', { name: 'Cancel upload' }));
		expect(controls.aborted).toBe(true);
		expect(await screen.findByText(/existing artwork is unchanged/)).toBeInTheDocument();
		expect(onsettled).toHaveBeenCalledOnce(); // reload to show what is really stored
		expect(onuploaded).not.toHaveBeenCalled();
	});

	it('offers Cancel only while the file is being sent, not while the server is processing it', async () => {
		const controls = fakeUpload();
		const { input, user } = setup();
		await user.upload(input, file());
		expect(await screen.findByRole('button', { name: 'Cancel upload' })).toBeInTheDocument();
		controls.progress(100);
		await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Processing…'));
		expect(screen.queryByRole('button', { name: 'Cancel upload' })).not.toBeInTheDocument();
		controls.resolve();
		await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument());
	});

	it('shows the specific server message and retries with the same file', async () => {
		const first = fakeUpload();
		const { input, user } = setup();
		await user.upload(input, file());
		await screen.findByRole('progressbar');
		first.reject(
			new ApiError('VALIDATION_FAILED', 'x', { fieldErrors: { file: 'artwork.corrupt' } })
		);
		expect(await screen.findByRole('alert')).toHaveTextContent('The image could not be read');

		const second = fakeUpload();
		await user.click(screen.getByRole('button', { name: 'Retry upload' }));
		expect(uploadArtwork).toHaveBeenCalledTimes(2);
		expect(uploadArtwork.mock.calls[1][1]).toBe(uploadArtwork.mock.calls[0][1]);
		second.resolve();
		await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
	});

	it('a network failure gets the generic connection message and a Retry action', async () => {
		const controls = fakeUpload();
		const { input, user } = setup();
		await user.upload(input, file());
		await screen.findByRole('progressbar');
		controls.reject(new ApiError('NETWORK', 'down'));
		expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server');
		expect(screen.getByRole('button', { name: 'Retry upload' })).toBeInTheDocument();
	});

	it('checks type and size before sending anything, with no Retry for a file that cannot work', async () => {
		const { input, user } = setup();
		await user.upload(input, file('application/pdf'));
		expect(await screen.findByRole('alert')).toHaveTextContent('Use a JPEG, PNG or WebP image.');
		await user.upload(input, file('image/png', 5 * 1024 * 1024 + 1));
		expect(await screen.findByRole('alert')).toHaveTextContent('larger than 5 MB');
		await user.upload(input, file('image/png', 0));
		expect(await screen.findByRole('alert')).toHaveTextContent('empty');
		expect(uploadArtwork).not.toHaveBeenCalled();
		expect(screen.queryByRole('button', { name: 'Retry upload' })).not.toBeInTheDocument();
	});

	it('names the action by whether artwork exists, and the file input has a text label', () => {
		setup(true);
		expect(screen.getByRole('heading', { name: 'Replace artwork' })).toBeInTheDocument();
		expect(screen.getByLabelText(/Image file/)).toHaveAttribute('type', 'file');
	});

	// ---- drag and drop (S-701 AC10 to AC13) ----

	/** jsdom has no DataTransfer; Testing Library copies this plain object onto the event. */
	const transfer = (files: File[], types = ['Files']) => ({ dataTransfer: { types, files } });
	const zone = () => screen.getByLabelText(/Image file/).closest('label')!;

	it('shows the drop zone text, and the file input keeps its label for keyboard users (AC10)', () => {
		setup();
		expect(zone()).toHaveTextContent('Drag an image here or choose a file');
		expect(zone()).toHaveTextContent('JPEG, PNG or WebP · up to 5 MB');
		expect(screen.getByLabelText('Image file (JPEG, PNG or WebP, up to 5 MB)')).toHaveAttribute(
			'type',
			'file'
		);
	});

	it('highlights while a file is dragged over and restores on leave (AC11)', async () => {
		setup(true);
		await fireEvent.dragEnter(zone(), transfer([file()]));
		expect(zone()).toHaveClass('dragging');
		expect(zone()).toHaveTextContent('Drop to upload');
		expect(zone()).toHaveTextContent('Replaces the current artwork');
		await fireEvent.dragLeave(zone(), { relatedTarget: null });
		expect(zone()).not.toHaveClass('dragging');
		expect(zone()).toHaveTextContent('Drag an image here');
	});

	it('does not mention replacing when there is no artwork yet (AC11)', async () => {
		setup(false);
		await fireEvent.dragEnter(zone(), transfer([file()]));
		expect(zone()).toHaveTextContent('Drop to upload');
		expect(zone()).not.toHaveTextContent('Replaces the current artwork');
	});

	it('uploads a dropped file through the same path as a chosen one (AC12)', async () => {
		const controls = fakeUpload();
		const { onuploaded } = setup();
		const dropped = file();
		const notCancelled = await fireEvent.drop(zone(), transfer([dropped]));
		expect(notCancelled).toBe(false); // the browser must not open the file
		expect(uploadArtwork).toHaveBeenCalledWith('m1', dropped, expect.anything());
		expect(
			await screen.findByRole('progressbar', { name: 'Uploading artwork' })
		).toBeInTheDocument();
		controls.resolve();
		await waitFor(() => expect(onuploaded).toHaveBeenCalledOnce());
	});

	it('applies the same client checks to a dropped file, with no upload (AC12)', async () => {
		setup();
		await fireEvent.drop(zone(), transfer([file('application/pdf')]));
		expect(await screen.findByRole('alert')).toHaveTextContent('Use a JPEG, PNG or WebP image.');
		expect(uploadArtwork).not.toHaveBeenCalled();
	});

	it('uses only the first of several dropped files and says so (AC12)', async () => {
		fakeUpload();
		setup();
		const first = file('image/png', 100, 'a.png');
		await fireEvent.drop(zone(), transfer([first, file('image/png', 100, 'b.png')]));
		expect(uploadArtwork).toHaveBeenCalledOnce();
		expect(uploadArtwork.mock.calls[0][1]).toBe(first);
		expect(screen.getByText('Only the first file was used.')).toBeInTheDocument();
	});

	it('ignores a drop while an upload is running (AC12)', async () => {
		fakeUpload();
		setup();
		await fireEvent.drop(zone(), transfer([file()]));
		await screen.findByRole('progressbar');
		await fireEvent.dragEnter(zone(), transfer([file()]));
		expect(zone()).not.toHaveClass('dragging');
		await fireEvent.drop(zone(), transfer([file()]));
		expect(uploadArtwork).toHaveBeenCalledOnce();
	});

	it('ignores drags that carry no files, such as text (AC12)', async () => {
		setup();
		const notCancelled = await fireEvent.dragOver(zone(), transfer([], ['text/plain']));
		expect(notCancelled).toBe(true);
		expect(zone()).not.toHaveClass('dragging');
		await fireEvent.drop(zone(), transfer([], ['text/plain']));
		expect(uploadArtwork).not.toHaveBeenCalled();
		expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	});
});
