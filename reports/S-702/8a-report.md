# S-702 part 8a report: search scope menu

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented; unit and component tests not yet run (same environment block as S-701, see `reports/S-701/7a-report.md`); not reviewed.**

## What changed
| File | Change |
|------|--------|
| `src/lib/search-scope.ts` (new) | `Scope`, `SCOPES`, `parseScope` (unknown → All), `searchPath` (same `encodeURIComponent` encoding as before; `scope` left out for All), `searchLabel` |
| `src/lib/components/ScopeMenu.svelte` (new) | Menu button: `aria-haspopup="menu"`, `aria-expanded`; `role="menu"` of `menuitemradio` buttons with `aria-checked`; Down/Up open, arrows/Home/End move with wrap, Enter/Space choose, Esc closes, Tab and outside click close; focus returns to the button. Dark menu using the hero tokens (contrast already tested). ✓ via CSS with empty alt text so it is not read |
| `src/lib/components/SearchBox.svelte` | Scope button and field in one bordered box; label and placeholder follow the scope; changing scope searches again when there is text (or on the search page); **`goto` options fixed for SvelteKit 3** (below) |
| `src/lib/views/SearchView.svelte`, `src/routes/search/+page.svelte` | `scope` prop from `?scope=`; Titles uses `searchMovies`, People `searchPeople`, All `searchAll`; the stale-answer guard now compares query **and** scope; status text names only the searched groups |
| Tests | `search-scope.test.ts` (new); `SearchBox.test.ts` (6 new scope tests; option assertions updated); `search.test.ts` (5 new scope tests); `design-rules.test.ts` (ScopeMenu touch target); `e2e/mocked/a11y.e2e.ts` (Tab order includes the scope button; new scope test with axe scans); `e2e/mocked/search.e2e.ts` (new, focus regressions); screenshot spec |

No contract or backend change.

## Bug found and fixed: search box lost focus after every search (pre-existing)
The installed SvelteKit is **3.1.0**. Its `goto` ignores the old `keepFocus`, `noScroll` and `replaceState` options (they are removed; in dev they raise an error) and resets focus to `<body>` unless `reset: false` is passed. `SearchBox` still used the old options, so after the first debounced search the field lost focus and the next keystroke was dropped. This was confirmed in Chromium before the fix (typed "the", then "x": the value stayed "the" and focus was on `<body>`). The unit tests mock `goto` and only checked the option names, so they could not see it. Fixed with `{ replace: onSearchPage, reset: false }`; `e2e/mocked/search.e2e.ts` now checks focus against the real router. No other `goto` call in `src/` used the removed options. **Worth checking:** whether the SvelteKit 2 → 3 move came with the unexplained 10:01 change to `package.json` and `pnpm-lock.yaml`.

## Evidence
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `8a-gates-final-20261022-1356.log` |
| `pnpm lint` | pass (rerun after the screenshot spec edit) | same, plus session run |
| `pnpm build` | pass | same |
| `pnpm exec playwright test --project=mocked` | 63 passed (axe with the menu open and with Titles chosen; Tab order; two focus regressions), 17 skipped (screenshot spec) | same |
| Screenshots | menu open and Titles result at 360 and 1280 px | `screenshots-8a/` |
| **`pnpm test` (Vitest)** | **NOT RUN successfully (environment)**; 8a adds 3 + 6 + 5 tests, all unverified | — |

Earlier logs record a type error (`resolve()` needs `'/search' | '/search?…'`), a Svelte a11y warning (`<li>` with an interactive role; now `<li role="none">` around a `<button>`), and the focus failure that led to the bug above.

## Acceptance criteria
| AC | Evidence |
|----|----------|
| AC1 menu, keyboard, roles | `SearchBox.test.ts` scope tests (unrun); a11y E2E scope test with axe (passed); `search.e2e.ts` focus test (passed) |
| AC2 scope in address, per-scope search, states | `search.test.ts` scope tests, `search-scope.test.ts` (unrun); a11y E2E: `/search?q=the&scope=titles`, only Movies shown (passed) |
| AC3 label and placeholder, focus | `SearchBox.test.ts` (unrun); a11y E2E checks the "Search movies" box (passed); `search.e2e.ts` (passed) |

## Not covered
- The `stack` E2E project (real services) was not run.
- A screen-reader pass (VoiceOver) on the menu was not done.
