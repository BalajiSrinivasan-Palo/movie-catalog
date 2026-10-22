import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockApi, type MockOptions } from '../support/mock-api';

// S-602 AC2 and AC4: axe-core must report nothing serious or critical, on page loads and on states
// (dialog open, error, empty, validation errors, upload error), not only on the first paint.
async function scan(page: Page, what: string) {
	const results = await new AxeBuilder({ page })
		.withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
		.analyze();
	const blocking = results.violations.filter(
		(v) => v.impact === 'serious' || v.impact === 'critical'
	);
	const summary = blocking.map(
		(v) =>
			`${v.id} (${v.impact}): ${v.nodes
				.map((n) => n.target.join(' '))
				.slice(0, 3)
				.join(' | ')}`
	);
	expect(summary, `${what}: serious or critical axe violations`).toEqual([]);
	// Anything less severe is listed in the test output so it is not hidden.
	const minor = results.violations
		.filter((v) => !blocking.includes(v))
		.map((v) => `${v.id} (${v.impact})`);
	if (minor.length > 0)
		test
			.info()
			.annotations.push({ type: 'axe-minor', description: `${what}: ${minor.join(', ')}` });
}

async function open(page: Page, route: string, options: MockOptions = {}) {
	await mockApi(page, options);
	await page.goto(route);
	await expect(page.locator('h1').first()).toBeVisible();
	await page.waitForLoadState('networkidle');
}

const ROUTES = [
	'/movies',
	'/movies/new',
	'/movies/m1',
	'/movies/m2',
	'/movies/m1/edit',
	'/people',
	'/people/new',
	'/people/p1',
	'/people/p1/edit',
	'/search?q=the'
];

for (const route of ROUTES) {
	test(`page load ${route}`, async ({ page }) => {
		await open(page, route);
		await scan(page, route);
	});
}

test('movie list: empty state', async ({ page }) => {
	await open(page, '/movies', { movies: [] });
	await expect(page.getByText('There are no movies yet.')).toBeVisible();
	await scan(page, 'empty movie list');
});

test('movie list: error state', async ({ page }) => {
	await open(page, '/movies', { fail: ['Movies'] });
	await expect(page.getByRole('alert')).toBeVisible();
	await scan(page, 'movie list error');
});

test('movie list: filtered with no matches', async ({ page }) => {
	await open(page, '/movies');
	await page.getByText('War', { exact: true }).click(); // S-701: clicking a genre chip applies it at once
	await expect(page.getByText('No movies match these filters.')).toBeVisible();
	await scan(page, 'no filter matches');
});

test('movie detail: delete confirmation dialog open', async ({ page }) => {
	await open(page, '/movies/m1');
	await page.getByRole('button', { name: 'Delete', exact: true }).click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await scan(page, 'delete dialog');
});

test('movie detail: remove artwork dialog open', async ({ page }) => {
	await open(page, '/movies/m1');
	await page.getByRole('button', { name: 'Remove artwork' }).click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await scan(page, 'remove artwork dialog');
});

test('movie detail: not found', async ({ page }) => {
	await open(page, '/movies/does-not-exist');
	await scan(page, 'movie not found');
});

test('movie form: validation errors', async ({ page }) => {
	await open(page, '/movies/new');
	await page.getByRole('button', { name: 'Create movie' }).click();
	await expect(page.getByText(/title/i).first()).toBeVisible();
	await scan(page, 'movie form with errors');
});

test('person form: validation errors', async ({ page }) => {
	await open(page, '/people/new');
	await page.getByRole('button', { name: /Create person/ }).click();
	await scan(page, 'person form with errors');
});

test('uploader: error message for a file that is not an image', async ({ page }) => {
	await open(page, '/movies/m2');
	await page
		.getByLabel(/Image file/)
		.setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
	await expect(page.getByRole('alert')).toContainText('JPEG, PNG or WebP');
	await scan(page, 'uploader error');
});

test('credits: add form open and the person picker', async ({ page }) => {
	await open(page, '/movies/m1');
	await page
		.getByRole('button', { name: /Add credit/ })
		.first()
		.click();
	await scan(page, 'add credit form');
});

test('person: delete blocked by credits shows the explanation', async ({ page }) => {
	await open(page, '/people/p1');
	await page.getByRole('button', { name: 'Delete', exact: true }).click();
	await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
	await expect(page.getByText(/credits/i).first()).toBeVisible();
	await scan(page, 'blocked person delete');
});

test('search: too short, no results, error', async ({ page }) => {
	await open(page, '/search?q=a');
	await expect(page.getByRole('status').first()).toContainText('Enter between 2 and 100');
	await scan(page, 'search too short');
	await page.goto('/search?q=zzzz');
	await expect(page.getByText('No results for “zzzz”.')).toBeVisible();
	await scan(page, 'search no results');
});

test('search: backend error', async ({ page }) => {
	await open(page, '/search?q=the', { fail: ['Search'] });
	await expect(page.getByRole('alert')).toBeVisible();
	await scan(page, 'search error');
});

test('keyboard: skip link, search box and navigation are reachable in order', async ({ page }) => {
	await open(page, '/movies');
	await page.keyboard.press('Tab');
	await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
	await page.keyboard.press('Tab');
	await expect(page.getByRole('link', { name: 'Movie Catalog' })).toBeFocused();
	await page.keyboard.press('Tab');
	await expect(page.getByRole('link', { name: 'Movies', exact: true })).toBeFocused();
	await page.keyboard.press('Tab');
	await expect(page.getByRole('link', { name: 'People', exact: true })).toBeFocused();
	await page.keyboard.press('Tab');
	await expect(page.getByRole('button', { name: 'Search in: All' })).toBeFocused(); // S-702
	await page.keyboard.press('Tab');
	await expect(page.getByRole('searchbox')).toBeFocused();
});

test('search scope: menu open, then Titles shows only movies (S-702 AC1, AC2)', async ({
	page
}) => {
	await open(page, '/search?q=the');
	await expect(page.getByRole('heading', { name: /^People/ })).toBeVisible();
	await page.getByRole('button', { name: 'Search in: All' }).click();
	await expect(page.getByRole('menu', { name: 'Search in' })).toBeVisible();
	await scan(page, 'search scope menu open');
	await page.keyboard.press('ArrowDown');
	await page.keyboard.press('Enter');
	await expect(page).toHaveURL(/\/search\?q=the&scope=titles$/);
	await expect(page.getByRole('heading', { name: /^Movies/ })).toBeVisible();
	await expect(page.getByRole('heading', { name: /^People/ })).toHaveCount(0);
	await expect(page.getByRole('searchbox', { name: 'Search movies' })).toBeVisible();
	await expect(page.getByRole('button', { name: 'Search in: Titles' })).toBeFocused();
	await scan(page, 'search scoped to titles');
});
