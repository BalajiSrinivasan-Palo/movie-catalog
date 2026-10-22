import { test, expect } from '@playwright/test';
import { mockApi } from '../support/mock-api';

// S-602 AC1: every route at three widths has no sideways scroll and touch-friendly controls.
const VIEWPORTS = [
	{ name: '360', width: 360, height: 740 },
	{ name: '768', width: 768, height: 1024 },
	{ name: '1280', width: 1280, height: 800 }
];
export const ROUTES = [
	'/movies',
	'/movies/new',
	'/movies/m1',
	'/movies/m2',
	'/movies/m3',
	'/movies/m1/edit',
	'/people',
	'/people/new',
	'/people/p1',
	'/people/p1/edit',
	'/search?q=the'
];

const TARGET = 'button, input:not([type=hidden]), select, textarea, nav a, a.button-link, .brand';

for (const viewport of VIEWPORTS) {
	for (const route of ROUTES) {
		test(`${route} at ${viewport.name}px: no horizontal scroll and touch targets of 44px`, async ({
			page
		}) => {
			await page.setViewportSize({ width: viewport.width, height: viewport.height });
			await mockApi(page);
			await page.goto(route);
			await expect(page.locator('h1').first()).toBeVisible();
			await page.waitForLoadState('networkidle');

			const overflow = await page.evaluate(
				() => document.documentElement.scrollWidth - window.innerWidth
			);
			expect(overflow, 'page is wider than the window').toBeLessThanOrEqual(0);

			// A checkbox, radio chip or file drop zone is tapped through its label, so the label is what must be big enough.
			const small = await page.$$eval(TARGET, (els) =>
				els
					.map((el) => {
						const isCheck =
							el instanceof HTMLInputElement && ['checkbox', 'radio', 'file'].includes(el.type);
						return { el, r: (isCheck ? (el.closest('label') ?? el) : el).getBoundingClientRect() };
					})
					.filter(({ r }) => r.width > 0 && r.height > 0)
					.filter(({ r }) => r.width < 43.5 || r.height < 43.5)
					.map(
						({ el, r }) =>
							`${el.tagName.toLowerCase()} "${(el.textContent || (el as HTMLInputElement).type || '').trim().slice(0, 30)}" ${Math.round(r.width)}x${Math.round(r.height)}`
					)
			);
			expect(small, 'controls smaller than 44 px').toEqual([]);
		});
	}
}
