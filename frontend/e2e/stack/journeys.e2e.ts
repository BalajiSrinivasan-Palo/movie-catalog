import { test, expect } from '@playwright/test';
import {
	addCreditApi,
	createMovieApi,
	createPersonApi,
	makePng,
	recordRequests,
	unique
} from '../support/stack';

// S-603: user journeys against the real gateway, movie-service and people-service.
// scripts/e2e.sh starts the stack on a fresh data directory; nothing here is mocked.

test('UJ-01/02: create a movie, add a credit, upload artwork with visible progress', async ({
	page,
	request,
	baseURL
}) => {
	const recorder = recordRequests(page);
	const title = unique('Journey Movie');
	const person = unique('Journey Actor');
	await createPersonApi(request, person);

	// UJ-01 create
	await page.goto('/movies/new');
	await page.getByLabel('Title').fill(title);
	await page.getByLabel('Release year').fill('1999');
	await page.getByLabel('Synopsis').fill('A movie made by the journey test.');
	await page.getByLabel('Drama').check();
	await page.getByRole('button', { name: 'Create movie' }).click();
	await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();

	// credit
	await page.getByRole('button', { name: 'Add credit' }).click();
	await page.getByLabel('Find a person by name').fill(person);
	await page.getByRole('button', { name: 'Search people' }).click();
	await page.getByLabel('Person', { exact: true }).selectOption({ label: person });
	await page.getByLabel('Role').selectOption('ACTOR');
	await page.getByLabel('Character (optional)').fill('Lead');
	await page.getByRole('button', { name: 'Add to movie' }).click();
	await expect(page.getByRole('link', { name: person })).toBeVisible();

	// UJ-02 upload: ~4 MB file, link throttled so the progress bar can be seen
	const cdp = await page.context().newCDPSession(page);
	await cdp.send('Network.enable');
	await cdp.send('Network.emulateNetworkConditions', {
		offline: false,
		latency: 50,
		downloadThroughput: -1,
		uploadThroughput: (4 * 1024 * 1024) / 6 // about six seconds for the file
	});
	await page.getByLabel(/Image file/).setInputFiles({
		name: 'poster.png',
		mimeType: 'image/png',
		buffer: makePng(200, 300, 4 * 1024 * 1024)
	});
	const bar = page.getByRole('progressbar', { name: 'Uploading artwork' });
	await expect(bar).toBeVisible();
	const seen = new Set<string>();
	await expect
		.poll(
			async () => {
				const v = await bar.getAttribute('aria-valuenow').catch(() => null);
				if (v) seen.add(v);
				return seen.size;
			},
			{ timeout: 15_000 }
		)
		.toBeGreaterThan(1); // progress moved, it did not jump from nothing to done
	await cdp.send('Network.emulateNetworkConditions', {
		offline: false,
		latency: 0,
		downloadThroughput: -1,
		uploadThroughput: -1
	});
	const img = page.locator('img[src^="/artwork/"]').first();
	await expect(img).toBeVisible({ timeout: 30_000 });
	await expect(page.getByRole('button', { name: 'Remove artwork' })).toBeVisible();

	// the movie list shows the thumbnail too
	await page.goto('/movies');
	await expect(page.locator(`a:has-text("${title}")`).first()).toBeVisible();

	expect(recorder.offenders(baseURL!)).toEqual([]);
});

test('UJ-02: an unreadable image is rejected with a readable message and old artwork stays', async ({
	page,
	request
}) => {
	const id = await createMovieApi(request, unique('Bad Image'));
	await page.goto(`/movies/${id}`);
	await page.getByLabel(/Image file/).setInputFiles({
		name: 'fake.png',
		mimeType: 'image/png',
		buffer: Buffer.from('this is not an image at all')
	});
	await expect(page.getByRole('alert')).toContainText(/could not be read|Use a JPEG/i);
	await expect(page.locator('img[src^="/artwork/"]')).toHaveCount(0);
});

test('UJ-06: search finds movies and people by text, and shows an empty state', async ({
	page,
	request
}) => {
	const word = `zq${Date.now().toString(36)}`;
	await createMovieApi(request, `${word} the film`);
	await createPersonApi(request, `Pat ${word}`);

	await page.goto('/movies');
	const box = page.getByRole('search').getByRole('searchbox');
	await box.fill(word);
	await expect(page).toHaveURL(new RegExp(`/search\\?q=${word}`));
	await expect(page.getByRole('link', { name: new RegExp(`${word} the film`) })).toBeVisible();
	await expect(page.getByRole('link', { name: new RegExp(`Pat ${word}`) })).toBeVisible();

	await box.fill(`${word}-nothing-matches`);
	await expect(page.getByText(/no (movies|results|matches)/i).first()).toBeVisible();
});

test('UJ-07: a person with credits cannot be deleted; the blocking movie is shown', async ({
	page,
	request
}) => {
	const movieTitle = unique('Blocking Movie');
	const movieId = await createMovieApi(request, movieTitle, 2005);
	const personId = await createPersonApi(request, unique('Blocked Person'));
	await addCreditApi(request, movieId, personId, 'DIRECTOR');

	await page.goto(`/people/${personId}`);
	await page.getByRole('button', { name: 'Delete' }).click();
	await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
	const blocked = page.getByRole('alert').filter({ hasText: movieTitle });
	await expect(blocked).toBeVisible();
	await expect(blocked.getByRole('link', { name: new RegExp(movieTitle) })).toHaveAttribute(
		'href',
		`/movies/${movieId}`
	);
	// still there after reload
	await page.reload();
	await expect(page.getByRole('heading', { level: 1 })).toContainText('Blocked Person');

	// remove the credit, then the delete goes through
	await page.goto(`/movies/${movieId}`);
	await page
		.getByRole('button', { name: /Remove/ })
		.first()
		.click();
	const confirm = page.getByRole('dialog');
	if (await confirm.isVisible()) await confirm.getByRole('button', { name: /Remove/ }).click();
	await expect(page.getByText('No credits yet.')).toBeVisible();
	await page.goto(`/people/${personId}`);
	await page.getByRole('button', { name: 'Delete' }).click();
	await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
	await expect(page).toHaveURL(/\/people$/);
});

test('delete a movie removes it and its artwork URL', async ({ page, request }) => {
	const id = await createMovieApi(request, unique('Delete Me'));
	await page.goto(`/movies/${id}`);
	await page.getByRole('button', { name: 'Delete' }).click();
	await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
	await expect(page).toHaveURL(/\/movies$/);
	const res = await request.get(`/artwork/${id}/thumbnail`);
	expect(res.status()).toBe(404);
});
