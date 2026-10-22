import { describe, it, expect } from 'vitest';
import { parseScope, searchLabel, searchPath } from './search-scope.js';

describe('search scope (S-702 AC1 to AC3)', () => {
	it('reads the scope from the address, falling back to All', () => {
		expect(parseScope('titles')).toBe('titles');
		expect(parseScope('people')).toBe('people');
		expect(parseScope('all')).toBe('all');
		expect(parseScope(null)).toBe('all');
		expect(parseScope('TITLES')).toBe('all');
		expect(parseScope('<script>')).toBe('all');
	});

	it('builds the search address, leaving out All and an empty query', () => {
		expect(searchPath('heat', 'all')).toBe('/search?q=heat');
		expect(searchPath('heat', 'titles')).toBe('/search?q=heat&scope=titles');
		expect(searchPath('a&b c', 'people')).toBe('/search?q=a%26b%20c&scope=people');
		expect(searchPath('', 'people')).toBe('/search?scope=people');
		expect(searchPath('', 'all')).toBe('/search');
	});

	it('names the search box after the scope', () => {
		expect(searchLabel('all')).toBe('Search movies and people');
		expect(searchLabel('titles')).toBe('Search movies');
		expect(searchLabel('people')).toBe('Search people');
	});
});
