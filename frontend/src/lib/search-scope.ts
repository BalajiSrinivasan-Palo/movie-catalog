/** What the search box looks in (S-702 AC1 to AC3). "People" covers cast and crew. */
export type Scope = 'all' | 'titles' | 'people';

export const SCOPES: { value: Scope; label: string }[] = [
	{ value: 'all', label: 'All' },
	{ value: 'titles', label: 'Titles' },
	{ value: 'people', label: 'People' }
];

/** The scope in the address; anything unknown or missing means All. */
export function parseScope(raw: string | null): Scope {
	return raw === 'titles' || raw === 'people' ? raw : 'all';
}

/** Search page address for a trimmed query; All is the default and is left out. */
export function searchPath(query: string, scope: Scope): '/search' | `/search?${string}` {
	const parts: string[] = [];
	if (query) parts.push(`q=${encodeURIComponent(query)}`);
	if (scope !== 'all') parts.push(`scope=${scope}`);
	return parts.length > 0 ? `/search?${parts.join('&')}` : '/search';
}

/** Label and placeholder of the search box. */
export function searchLabel(scope: Scope): string {
	return scope === 'titles'
		? 'Search movies'
		: scope === 'people'
			? 'Search people'
			: 'Search movies and people';
}
