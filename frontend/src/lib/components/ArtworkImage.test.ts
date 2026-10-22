import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import { fireEvent } from '@testing-library/svelte';
import ArtworkImage from './ArtworkImage.svelte';

describe('ArtworkImage', () => {
	it('shows the picture with alt text and lazy loading when asked', () => {
		render(ArtworkImage, { src: '/artwork/m1/thumbnail?v=1', title: 'Heat', lazy: true });
		const img = screen.getByRole('img', { name: 'Poster of Heat' });
		expect(img).toHaveAttribute('src', '/artwork/m1/thumbnail?v=1');
		expect(img).toHaveAttribute('loading', 'lazy');
	});

	it('shows the placeholder when there is no artwork', () => {
		render(ArtworkImage, { src: null, title: 'Ran' });
		expect(screen.getByRole('img', { name: 'No artwork for Ran' })).toHaveTextContent('No image');
	});

	it('falls back to the placeholder when the picture fails to load', async () => {
		render(ArtworkImage, { src: '/broken.jpg', title: 'Heat' });
		await fireEvent.error(screen.getByRole('img', { name: 'Poster of Heat' }));
		expect(screen.getByRole('img', { name: 'No artwork for Heat' })).toBeInTheDocument();
	});

	it('reserves space with a fixed aspect ratio so the page does not jump', () => {
		// jsdom does not run layout; the rule is asserted on the source so removing it fails a test.
		const source = import.meta.glob('./ArtworkImage.svelte', {
			query: '?raw',
			import: 'default',
			eager: true
		});
		const text = Object.values(source)[0] as unknown as string;
		expect(text).toMatch(/aspect-ratio:\s*2\s*\/\s*3/);
	});
});
