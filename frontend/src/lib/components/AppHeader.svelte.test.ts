import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import AppHeader from './AppHeader.svelte';

describe('AppHeader', () => {
	it('shows the brand and both main sections', () => {
		render(AppHeader, { currentPath: '/' });
		expect(screen.getByRole('link', { name: 'Movie Catalog' })).toHaveAttribute('href', '/movies');
		expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Movies' })).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'People' })).toBeInTheDocument();
	});

	it('marks the current section for assistive technology', () => {
		render(AppHeader, { currentPath: '/movies/123' });
		expect(screen.getByRole('link', { name: 'Movies' })).toHaveAttribute('aria-current', 'page');
		expect(screen.getByRole('link', { name: 'People' })).not.toHaveAttribute('aria-current');
	});

	it('does not mark a section whose name merely starts the same', () => {
		render(AppHeader, { currentPath: '/moviesX' });
		expect(screen.getByRole('link', { name: 'Movies' })).not.toHaveAttribute('aria-current');
	});
});
