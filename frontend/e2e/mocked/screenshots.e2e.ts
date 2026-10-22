import { test, expect } from '@playwright/test';
import { mockApi, sampleMovies } from '../support/mock-api';

// S-701 definition of done: screenshots of the restyled pages for the owner and the reviewer.
// Skipped unless SCREENSHOT_DIR is set, so normal runs write no files.
const dir = process.env.SCREENSHOT_DIR;

const WIDTHS = [360, 768, 1280];
const PAGES = [
	{ name: 'movies', route: '/movies' },
	{ name: 'people', route: '/people' },
	{ name: 'movie-with-artwork', route: '/movies/m1' },
	{ name: 'movie-without-artwork', route: '/movies/m2' },
	{ name: 'person', route: '/people/p1' }
];

// A fuller list than the three samples, so the grid can be judged at five columns.
const movies = () =>
	Array.from({ length: 10 }, (_, i) => {
		const base = sampleMovies()[i % 3];
		return {
			...base,
			id: i < 3 ? base.id : `m${i + 1}`,
			title: i < 3 ? base.title : `${base.title} ${i + 1}`
		};
	});

for (const width of WIDTHS) {
	for (const { name, route } of PAGES) {
		test(`screenshot ${name} at ${width}px`, async ({ page }) => {
			test.skip(!dir, 'set SCREENSHOT_DIR to save screenshots');
			await page.setViewportSize({ width, height: 900 });
			await mockApi(page, { movies: movies() });
			await page.goto(route);
			await expect(page.locator('h1').first()).toBeVisible();
			await page.waitForLoadState('networkidle');
			await page.screenshot({ path: `${dir}/${name}-${width}.png`, fullPage: true });
		});
	}
}

for (const width of [360, 1280]) {
	test(`screenshot drop zone at rest and while dragging at ${width}px`, async ({ page }) => {
		test.skip(!dir, 'set SCREENSHOT_DIR to save screenshots');
		await page.setViewportSize({ width, height: 900 });
		await mockApi(page);
		await page.goto('/movies/m1');
		const zone = page.locator('section.uploader');
		await expect(zone).toBeVisible();
		await page.waitForLoadState('networkidle');
		await zone.screenshot({ path: `${dir}/dropzone-rest-${width}.png` });
		const dataTransfer = await page.evaluateHandle(() => {
			const dt = new DataTransfer();
			dt.items.add(new File(['x'], 'poster.png', { type: 'image/png' }));
			return dt;
		});
		await page.locator('label.zone').dispatchEvent('dragenter', { dataTransfer });
		await expect(zone).toContainText('Drop to upload');
		await zone.screenshot({ path: `${dir}/dropzone-dragging-${width}.png` });
	});
}

for (const width of [360, 1280]) {
	test(`screenshot search scope menu and a scoped result at ${width}px`, async ({ page }) => {
		test.skip(!dir, 'set SCREENSHOT_DIR to save screenshots');
		await page.setViewportSize({ width, height: 700 });
		await mockApi(page);
		await page.goto('/search?q=the');
		await page.waitForLoadState('networkidle');
		await page.getByRole('button', { name: 'Search in: All' }).click();
		await expect(page.getByRole('menu')).toBeVisible();
		await page.screenshot({ path: `${dir}/search-scope-menu-${width}.png` });
		await page.getByRole('menuitemradio', { name: 'Titles' }).click();
		await expect(page).toHaveURL(/scope=titles/);
		await page.waitForLoadState('networkidle');
		await page.screenshot({ path: `${dir}/search-scope-titles-${width}.png`, fullPage: true });
	});
}

for (const width of [360, 1280]) {
	test(`screenshot year picker open at ${width}px`, async ({ page }) => {
		test.skip(!dir, 'set SCREENSHOT_DIR to save screenshots');
		await page.setViewportSize({ width, height: 800 });
		await mockApi(page);
		await page.goto('/movies');
		await page.waitForLoadState('networkidle');
		await page.getByLabel('From year', { exact: true }).fill('1995');
		await page.getByRole('button', { name: 'Choose from year' }).click();
		await expect(page.getByRole('dialog')).toBeVisible();
		await page.screenshot({ path: `${dir}/year-picker-from-${width}.png` });
		await page.keyboard.press('Escape');
		await page.getByRole('button', { name: 'Choose to year' }).click();
		await expect(page.getByRole('dialog')).toBeVisible();
		await page.screenshot({ path: `${dir}/year-picker-to-${width}.png` });
	});
}
