import { test, expect } from '@playwright/test';
import { mockApi } from '../support/mock-api';

// Focus must survive the navigations the search box makes (S-502 AC1, S-702 AC1). SvelteKit 3 resets
// focus to <body> on goto() unless `reset: false` is passed; unit tests mock goto, so this checks the
// real router.

test('typing keeps focus in the search box across the debounced navigations', async ({ page }) => {
	await mockApi(page);
	await page.goto('/movies');
	await page.waitForLoadState('networkidle');
	const box = page.getByRole('searchbox');
	await box.click();
	await page.keyboard.type('th');
	await expect(page).toHaveURL(/\/search\?q=th$/);
	await expect(box).toBeFocused();
	await page.keyboard.type('e');
	await expect(page).toHaveURL(/\/search\?q=the$/);
	await expect(box).toHaveValue('the');
	await expect(box).toBeFocused();
});

test('choosing a scope keeps focus on the scope button', async ({ page }) => {
	await mockApi(page);
	await page.goto('/search?q=the');
	await page.waitForLoadState('networkidle');
	await page.getByRole('button', { name: 'Search in: All' }).click();
	await page.getByRole('menuitemradio', { name: /People/ }).click();
	await expect(page).toHaveURL(/scope=people$/);
	await expect(page.getByRole('button', { name: 'Search in: People' })).toBeFocused();
});
