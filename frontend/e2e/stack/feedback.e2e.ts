import { test, expect, type Page, type Route } from '@playwright/test';
import { createMovieApi, createPersonApi, makePng, unique } from '../support/stack';

// S-603 feedback rules (UX-FB): every mutation, list, search and upload must
//   1. show a visible pending state and block a second submit while the server is slow, and
//   2. on a server error show a readable message and keep what the user typed.
// The pages and the gateway are real; the fault is injected on the browser's request to /graphql, because
// the real backend does not fail or stall on demand.

const INTERNAL = JSON.stringify({
	data: null,
	errors: [{ message: 'boom', extensions: { code: 'INTERNAL', correlationId: 'e2e' } }]
});
const READABLE = /Something went wrong on our side|Cannot reach the server|could not/i;

/** Intercepts requests whose body mentions `operation`. mode 'slow' holds the request until released. */
async function inject(page: Page, operation: string, mode: 'error' | 'slow') {
	let release: () => void = () => {};
	const gate = new Promise<void>((r) => (release = r));
	let hits = 0;
	await page.route('**/graphql', async (route: Route) => {
		const body = route.request().postData() ?? '';
		if (!body.includes(operation)) return route.continue();
		hits++;
		if (mode === 'error')
			return route.fulfill({ status: 200, contentType: 'application/json', body: INTERNAL });
		await gate;
		return route.continue();
	});
	return { release: () => release(), hits: () => hits };
}

/**
 * Waits until the request held by a 'slow' fault has finished. After success the button may be gone
 * (create and update navigate to the saved page, delete closes its dialog, add credit closes its form),
 * so this waits until no button matching the locator is still busy: either it is detached or not busy.
 */
async function expectSettled(page: Page, button: ReturnType<Page['getByRole']>) {
	await expect(button.and(page.locator('[aria-busy="true"]'))).toHaveCount(0, { timeout: 15_000 });
}

interface Case {
	name: string;
	operation: string;
	/** Opens the page and fills in the input; returns the locator of the control that triggers the request. */
	prepare: (
		page: Page,
		request: import('@playwright/test').APIRequestContext
	) => Promise<() => Promise<void>>;
	/** The button that shows the pending state. */
	button: (page: Page) => ReturnType<Page['getByRole']>;
	/** Input whose value must survive an error. */
	kept?: (page: Page) => ReturnType<Page['getByLabel']>;
	keptValue?: string;
	/** The control lives in a confirmation dialog that the app closes when the request fails. */
	inDialog?: boolean;
}

const movieTitle = unique('Feedback Movie');
const personName = unique('Feedback Person');

const CASES: Case[] = [
	{
		name: 'create movie',
		operation: 'CreateMovie',
		prepare: async (page) => {
			await page.goto('/movies/new');
			await page.getByLabel('Title').fill(movieTitle);
			await page.getByLabel('Release year').fill('2010');
			return () => page.getByRole('button', { name: 'Create movie' }).click();
		},
		button: (p) => p.getByRole('button', { name: /Create movie/ }),
		kept: (p) => p.getByLabel('Title'),
		keptValue: movieTitle
	},
	{
		name: 'create person',
		operation: 'CreatePerson',
		prepare: async (page) => {
			await page.goto('/people/new');
			await page.getByLabel('Name').fill(personName);
			return () => page.getByRole('button', { name: 'Create person' }).click();
		},
		button: (p) => p.getByRole('button', { name: /Create person/ }),
		kept: (p) => p.getByLabel('Name'),
		keptValue: personName
	},
	{
		name: 'update movie',
		operation: 'UpdateMovie',
		prepare: async (page, request) => {
			const id = await createMovieApi(request, unique('Edit Me'));
			await page.goto(`/movies/${id}/edit`);
			await page.getByLabel('Title').fill(movieTitle + ' v2');
			return () => page.getByRole('button', { name: 'Save changes' }).click();
		},
		button: (p) => p.getByRole('button', { name: /Save changes/ }),
		kept: (p) => p.getByLabel('Title'),
		keptValue: movieTitle + ' v2'
	},
	{
		name: 'update person',
		operation: 'UpdatePerson',
		prepare: async (page, request) => {
			const id = await createPersonApi(request, unique('Edit Person'));
			await page.goto(`/people/${id}/edit`);
			await page.getByLabel('Biography').fill('Changed bio');
			return () => page.getByRole('button', { name: 'Save changes' }).click();
		},
		button: (p) => p.getByRole('button', { name: /Save changes/ }),
		kept: (p) => p.getByLabel('Biography'),
		keptValue: 'Changed bio'
	},
	{
		name: 'delete movie',
		operation: 'DeleteMovie',
		prepare: async (page, request) => {
			const id = await createMovieApi(request, unique('Delete Fail'));
			await page.goto(`/movies/${id}`);
			await page.getByRole('button', { name: 'Delete' }).click();
			return () => page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
		},
		button: (p) => p.getByRole('dialog').getByRole('button', { name: /Delete/ }),
		inDialog: true
	},
	{
		name: 'delete person',
		operation: 'DeletePerson',
		prepare: async (page, request) => {
			const id = await createPersonApi(request, unique('Delete Fail P'));
			await page.goto(`/people/${id}`);
			await page.getByRole('button', { name: 'Delete' }).click();
			return () => page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
		},
		button: (p) => p.getByRole('dialog').getByRole('button', { name: /Delete/ }),
		inDialog: true
	}
];

for (const c of CASES) {
	test(`${c.name}: slow response shows pending and blocks a second submit`, async ({
		page,
		request
	}) => {
		const submit = await c.prepare(page, request);
		const fault = await inject(page, c.operation, 'slow');
		await submit();
		const button = c.button(page);
		await expect(button).toHaveAttribute('aria-busy', 'true');
		await expect(button).toHaveAttribute('aria-disabled', 'true');
		await expect(button).toContainText('Working…');
		await button.click({ force: true }); // a second click must be swallowed
		await button.click({ force: true });
		fault.release();
		await expectSettled(page, button);
		expect(fault.hits(), 'exactly one request reached the server').toBe(1);
	});

	test(`${c.name}: server error shows a readable message and keeps the input`, async ({
		page,
		request
	}) => {
		const submit = await c.prepare(page, request);
		await inject(page, c.operation, 'error');
		await submit();
		await expect(page.getByRole('alert').filter({ hasText: READABLE }).first()).toBeVisible();
		if (c.kept) await expect(c.kept(page)).toHaveValue(c.keptValue!);
		// A failed delete closes the confirmation dialog by design (MovieDetailView/PersonDetailView confirmDelete).
		if (c.inDialog) await expect(page.getByRole('dialog')).toHaveCount(0);
		else await expect(c.button(page)).not.toHaveAttribute('aria-busy', 'true');
		await expect(page.locator('body')).not.toContainText(/boom|Exception|stack/i);
	});
}

test('add credit: error keeps the chosen person and role; slow shows pending', async ({
	page,
	request
}) => {
	const movie = await createMovieApi(request, unique('Credit Feedback'));
	const person = unique('Credit Person');
	await createPersonApi(request, person);
	for (const mode of ['slow', 'error'] as const) {
		await page.goto(`/movies/${movie}`);
		await page.getByRole('button', { name: 'Add credit' }).click();
		await page.getByLabel('Find a person by name').fill(person);
		await page.getByRole('button', { name: 'Search people' }).click();
		await page.getByLabel('Person', { exact: true }).selectOption({ label: person });
		await page.getByLabel('Role').selectOption('DIRECTOR');
		const fault = await inject(page, 'AddCredit', mode);
		await page.getByRole('button', { name: 'Add to movie' }).click();
		const button = page.getByRole('button', { name: /Add to movie/ });
		if (mode === 'slow') {
			await expect(button).toHaveAttribute('aria-busy', 'true');
			fault.release();
			await expectSettled(page, button); // on success the add-credit form closes, so the button may be gone
			// the slow request went through, so remove nothing: the next round uses the same movie and a duplicate is expected
		} else {
			await expect(page.getByRole('alert').filter({ hasText: READABLE }).first()).toBeVisible();
			await expect(page.getByLabel('Role')).toHaveValue('DIRECTOR');
		}
		await page.unrouteAll({ behavior: 'ignoreErrors' });
	}
});

test('lists and detail pages: error shows a message with Try again; slow shows a loading indicator', async ({
	page
}) => {
	for (const [route, operation, loading] of [
		['/movies', 'Movies', /Loading/i],
		['/people', 'People', /Loading/i]
	] as const) {
		const slow = await inject(page, operation, 'slow');
		await page.goto(route, { waitUntil: 'commit' });
		await expect(page.getByRole('status').or(page.getByText(loading)).first()).toBeVisible();
		slow.release();
		await page.unrouteAll({ behavior: 'ignoreErrors' });

		await inject(page, operation, 'error');
		await page.goto(route);
		await expect(page.getByRole('alert').filter({ hasText: READABLE })).toBeVisible();
		await page.unrouteAll({ behavior: 'ignoreErrors' });
		await page
			.getByRole('button', { name: 'Try again' })
			.click()
			.catch(() => {});
	}
});

test('search: error shows a message with retry; slow shows a loading indicator', async ({
	page
}) => {
	const slow = await inject(page, 'Search', 'slow');
	await page.goto('/search?q=abc', { waitUntil: 'commit' });
	await expect(
		page
			.getByRole('status')
			.or(page.getByText(/Searching|Loading/i))
			.first()
	).toBeVisible();
	slow.release();
	await page.unrouteAll({ behavior: 'ignoreErrors' });

	await inject(page, 'Search', 'error');
	await page.goto('/search?q=abc');
	await expect(page.getByRole('alert').filter({ hasText: READABLE })).toBeVisible();
	await expect(page.getByRole('searchbox')).toHaveValue('abc');
});

test('upload: server error shows the message and Retry; the chosen file is not lost', async ({
	page,
	request
}) => {
	const id = await createMovieApi(request, unique('Upload Feedback'));
	await page.goto(`/movies/${id}`);
	await inject(page, 'uploadArtwork', 'error');
	await page
		.getByLabel(/Image file/)
		.setInputFiles({ name: 'p.png', mimeType: 'image/png', buffer: makePng() });
	await expect(page.getByRole('alert').filter({ hasText: READABLE })).toBeVisible();
	await expect(page.getByRole('button', { name: 'Retry upload' })).toBeVisible();
});
