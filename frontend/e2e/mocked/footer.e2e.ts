import { test, expect } from '@playwright/test';
import { mockApi } from '../support/mock-api';

// D23: the footer is silent when every service is up and speaks only when something is wrong. Unit tests
// cover the states too, but this checks the page a user actually sees.

test('the footer says nothing when every service is up', async ({ page }) => {
	await mockApi(page);
	await page.goto('/movies');
	await page.waitForLoadState('networkidle');
	const footer = page.getByRole('contentinfo', { name: 'Backend status' });
	await expect(footer.getByRole('status')).toHaveText('');
	await expect(page.getByText(/Cannot reach the server|services are running/)).toHaveCount(0);
});

test('the footer warns when the gateway cannot be reached', async ({ page }) => {
	await mockApi(page);
	await page.route('**/graphql', (route) => route.abort()); // added last, so it runs first
	await page.goto('/movies');
	await expect(page.getByRole('contentinfo', { name: 'Backend status' })).toContainText(
		'Cannot reach the server'
	);
});
