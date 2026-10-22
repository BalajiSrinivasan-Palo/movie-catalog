import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockApi } from '../support/mock-api';

// S-702 AC4 to AC6 in a real browser: the year picker opens, is accessible, fits a phone screen and fills the field.

async function open(page: Page, width: number) {
	await page.setViewportSize({ width, height: 800 });
	await mockApi(page);
	await page.goto('/movies');
	await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
	await page.waitForLoadState('networkidle');
}

for (const width of [360, 1280]) {
	test(`year picker at ${width}px: no serious axe issues, no sideways scroll, picks a year`, async ({
		page
	}) => {
		await open(page, width);
		await page.getByRole('button', { name: 'Choose to year' }).click();
		const dialog = page.getByRole('dialog', { name: 'Choose to year' });
		await expect(dialog).toBeVisible();

		const results = await new AxeBuilder({ page })
			.withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
			.analyze();
		const blocking = results.violations
			.filter((v) => v.impact === 'serious' || v.impact === 'critical')
			.map((v) => v.id);
		expect(blocking, 'serious or critical axe violations').toEqual([]);

		const overflow = await page.evaluate(
			() => document.documentElement.scrollWidth - window.innerWidth
		);
		expect(overflow, 'page is wider than the window').toBeLessThanOrEqual(0);
		const box = await dialog.boundingBox();
		expect(box!.x).toBeGreaterThanOrEqual(0);
		expect(box!.x + box!.width).toBeLessThanOrEqual(width);

		const small = await dialog
			.locator('button')
			.evaluateAll(
				(els) =>
					els
						.map((el) => el.getBoundingClientRect())
						.filter((r) => r.width < 43.5 || r.height < 43.5).length
			);
		expect(small, 'year picker buttons smaller than 44 px').toBe(0);

		await page.keyboard.press('ArrowLeft');
		await page.keyboard.press('Enter');
		await expect(dialog).toBeHidden();
		await expect(page.getByLabel('To year', { exact: true })).toHaveValue(
			String(new Date().getFullYear() - 1)
		);
		await expect(page.getByRole('button', { name: 'Choose to year' })).toBeFocused();
	});
}

test('several genre chips filter with any-match, and Clear filters resets (S-702 AC7)', async ({
	page
}) => {
	await open(page, 1280);
	const titles = page.getByRole('list').last().getByRole('heading');
	await expect(titles).toHaveCount(3);
	await page.getByText('Drama', { exact: true }).click();
	await expect(titles).toHaveCount(1);
	await page.getByText('Sci-Fi', { exact: true }).click();
	await expect(titles).toHaveCount(2); // Drama or Sci-Fi
	const results = await new AxeBuilder({ page })
		.withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
		.analyze();
	expect(
		results.violations
			.filter((v) => v.impact === 'serious' || v.impact === 'critical')
			.map((v) => v.id)
	).toEqual([]);
	await page.getByRole('button', { name: 'Clear filters' }).click();
	await expect(titles).toHaveCount(3);
});
