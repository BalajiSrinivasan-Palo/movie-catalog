import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import SearchBox from './SearchBox.svelte';

const goto = vi.hoisted(() => vi.fn());
const current = vi.hoisted(() => ({ url: new URL('http://localhost/movies') }));
vi.mock('$app/navigation', () => ({ goto }));
vi.mock('$app/state', () => ({
	page: {
		get url() {
			return current.url;
		}
	}
}));

beforeEach(() => {
	vi.useFakeTimers({ shouldAdvanceTime: true });
	goto.mockReset();
	current.url = new URL('http://localhost/movies');
});
afterEach(() => vi.useRealTimers());

const setup = () => ({
	user: userEvent.setup({ advanceTimers: vi.advanceTimersByTime }),
	box: undefined as never
});

describe('SearchBox', () => {
	it('is a labelled search landmark', () => {
		render(SearchBox);
		expect(screen.getByRole('search')).toBeInTheDocument();
		expect(screen.getByRole('searchbox', { name: 'Search movies and people' })).toBeInTheDocument();
	});

	it('waits 300 ms after the last key and then navigates once (rapid typing)', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.type(screen.getByRole('searchbox'), 'nolan');
		expect(goto).not.toHaveBeenCalled();
		vi.advanceTimersByTime(299);
		expect(goto).not.toHaveBeenCalled();
		vi.advanceTimersByTime(2);
		expect(goto).toHaveBeenCalledOnce();
		expect(goto.mock.calls[0][0]).toBe('/search?q=nolan');
	});

	it('pushes history from another page but replaces it while already searching', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.type(screen.getByRole('searchbox'), 'ab');
		vi.advanceTimersByTime(400);
		expect(goto.mock.calls[0][1]).toEqual({ replace: false, reset: false });
		goto.mockReset();

		cleanup();
		current.url = new URL('http://localhost/search?q=ab');
		render(SearchBox);
		await user.type(screen.getByRole('searchbox'), 'c');
		vi.advanceTimersByTime(400);
		expect(goto.mock.calls[0][1]).toEqual({ replace: true, reset: false });
	});

	it('trims and encodes the text, and Enter searches at once', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.type(screen.getByRole('searchbox'), '  a&b c %  {Enter}');
		expect(goto).toHaveBeenCalledOnce(); // Enter cancelled the pending timer
		expect(goto.mock.calls[0][0]).toBe('/search?q=a%26b%20c%20%25');
		vi.advanceTimersByTime(1000);
		expect(goto).toHaveBeenCalledOnce();
	});

	it('shows the query from the address when it opens on the search page', () => {
		current.url = new URL('http://localhost/search?q=heat');
		render(SearchBox);
		expect(screen.getByRole('searchbox')).toHaveValue('heat');
	});

	it('clearing the box goes to the empty search page', async () => {
		const { user } = setup();
		current.url = new URL('http://localhost/search?q=heat');
		render(SearchBox);
		await user.clear(screen.getByRole('searchbox'));
		vi.advanceTimersByTime(400);
		expect(goto.mock.calls.at(-1)?.[0]).toBe('/search');
	});

	// ---- scope menu (S-702 AC1 to AC3) ----

	it('shows a scope button that opens a menu of three checked-state items', async () => {
		const { user } = setup();
		render(SearchBox);
		const toggle = screen.getByRole('button', { name: 'Search in: All' });
		expect(toggle).toHaveAttribute('aria-haspopup', 'menu');
		expect(toggle).toHaveAttribute('aria-expanded', 'false');
		await user.click(toggle);
		expect(toggle).toHaveAttribute('aria-expanded', 'true');
		const menu = screen.getByRole('menu', { name: 'Search in' });
		const items = within(menu).getAllByRole('menuitemradio');
		expect(items.map((i) => i.textContent?.trim())).toEqual([
			'All',
			'Titles',
			'People (cast and crew)'
		]);
		expect(items[0]).toHaveAttribute('aria-checked', 'true');
		expect(items[0]).toHaveFocus();
	});

	it('is keyboard-operable: Down opens, arrows move, Enter chooses, Esc closes', async () => {
		const { user } = setup();
		render(SearchBox);
		const toggle = screen.getByRole('button', { name: 'Search in: All' });
		toggle.focus();
		await user.keyboard('{ArrowDown}');
		expect(screen.getByRole('menuitemradio', { name: 'All' })).toHaveFocus();
		await user.keyboard('{ArrowDown}');
		expect(screen.getByRole('menuitemradio', { name: 'Titles' })).toHaveFocus();
		await user.keyboard('{End}');
		expect(screen.getByRole('menuitemradio', { name: /People/ })).toHaveFocus();
		await user.keyboard('{ArrowDown}');
		expect(screen.getByRole('menuitemradio', { name: 'All' })).toHaveFocus();
		await user.keyboard('{Escape}');
		expect(screen.queryByRole('menu')).not.toBeInTheDocument();
		expect(toggle).toHaveFocus();

		await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');
		expect(screen.queryByRole('menu')).not.toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Search in: Titles' })).toBeInTheDocument();
		expect(screen.getByRole('searchbox', { name: 'Search movies' })).toHaveAttribute(
			'placeholder',
			'Search movies'
		);
	});

	it('closes on a click outside without changing the scope', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.click(screen.getByRole('button', { name: 'Search in: All' }));
		await user.click(document.body);
		expect(screen.queryByRole('menu')).not.toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Search in: All' })).toBeInTheDocument();
	});

	it('changing the scope with text in the box searches again at once, with the scope in the address', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.type(screen.getByRole('searchbox'), 'nolan{Enter}');
		goto.mockReset();
		await user.click(screen.getByRole('button', { name: 'Search in: All' }));
		await user.click(screen.getByRole('menuitemradio', { name: /People/ }));
		expect(goto).toHaveBeenCalledOnce();
		expect(goto.mock.calls[0][0]).toBe('/search?q=nolan&scope=people');
		expect(screen.getByRole('button', { name: 'Search in: People' })).toHaveFocus();
	});

	it('typing keeps the chosen scope in the address', async () => {
		const { user } = setup();
		render(SearchBox);
		await user.click(screen.getByRole('button', { name: 'Search in: All' }));
		await user.click(screen.getByRole('menuitemradio', { name: 'Titles' }));
		expect(goto).not.toHaveBeenCalled(); // nothing to search yet
		await user.type(screen.getByRole('searchbox'), 'heat');
		vi.advanceTimersByTime(400);
		expect(goto.mock.calls.at(-1)?.[0]).toBe('/search?q=heat&scope=titles');
	});

	it('takes the scope from the address on the search page', () => {
		current.url = new URL('http://localhost/search?q=heat&scope=people');
		render(SearchBox);
		expect(screen.getByRole('button', { name: 'Search in: People' })).toBeInTheDocument();
		expect(screen.getByRole('searchbox', { name: 'Search people' })).toHaveValue('heat');
	});
});
