import { test, expect, type Page } from '@playwright/test';
import { mockApi } from '../support/mock-api';

// S-701 AC11 to AC13 in a real browser: drag events carry a real DataTransfer with a File.
// These are synthetic events, so they prove the handlers and the upload path, not the operating
// system's drag gesture; a manual check with a real file is still listed in the report.

async function open(page: Page) {
	await mockApi(page);
	await page.goto('/movies/m2');
	await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
	await page.waitForLoadState('networkidle');
}

/** A DataTransfer in the page holding one small PNG-typed file. */
const pngTransfer = (page: Page) =>
	page.evaluateHandle(() => {
		const dt = new DataTransfer();
		dt.items.add(
			new File([new Uint8Array([137, 80, 78, 71])], 'poster.png', { type: 'image/png' })
		);
		return dt;
	});

test('dragging a file over the zone highlights it, and dropping it uploads (AC11, AC12)', async ({
	page
}) => {
	await open(page);
	const zone = page.locator('label.zone');
	await expect(zone).toContainText('Drag an image here or choose a file');
	const dataTransfer = await pngTransfer(page);
	await zone.dispatchEvent('dragenter', { dataTransfer });
	await expect(zone).toContainText('Drop to upload');
	await zone.dispatchEvent('drop', { dataTransfer });
	await expect(page.getByText('Artwork saved.')).toBeVisible();
	await expect(zone).toContainText('Drag an image here');
});

test('a file dropped outside the zone is not opened by the browser (AC13)', async ({ page }) => {
	await open(page);
	const prevented = await page.evaluate(() => {
		const dt = new DataTransfer();
		dt.items.add(new File(['x'], 'poster.png', { type: 'image/png' }));
		const over = new DragEvent('dragover', { dataTransfer: dt, bubbles: true, cancelable: true });
		const drop = new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true });
		document.querySelector('h1')!.dispatchEvent(over);
		document.querySelector('h1')!.dispatchEvent(drop);
		return { over: over.defaultPrevented, drop: drop.defaultPrevented };
	});
	expect(prevented).toEqual({ over: true, drop: true });
	await expect(page).toHaveURL(/\/movies\/m2$/);
});

test('dragging text outside the zone keeps the browser default (AC13)', async ({ page }) => {
	await open(page);
	const prevented = await page.evaluate(() => {
		const dt = new DataTransfer();
		dt.setData('text/plain', 'hello');
		const drop = new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true });
		document.querySelector('h1')!.dispatchEvent(drop);
		return drop.defaultPrevented;
	});
	expect(prevented).toBe(false);
});
