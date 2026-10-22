# S-701 part 7a report: tokens, header and list pages

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented; unit and component tests not yet run; not reviewed.**

## What changed
| File | Change |
|------|--------|
| `src/lib/styles/global.css` | Tokens `--color-accent-tint` (#e6eefc), `--radius-card`, `--radius-pill`; shared `.poster-grid` (2 / 3 / 5 columns at 600 and 960 px) |
| `src/lib/components/AppHeader.svelte` | White bar with hairline; current section is a tinted pill; search on its own row below 600 px (AC1) |
| `src/lib/components/GenreChips.svelte` (new) | Native radios in a `fieldset` with legend "Genre", styled as chips; single scrolling row below 600 px (AC3) |
| `src/lib/components/PosterCard.svelte` (new) | 2:3 poster, title link stretched over the card, meta line (AC2) |
| `src/lib/components/Skeleton.svelte` | `variant="posters"` for poster-shaped loading boxes (AC4) |
| `src/lib/initials.ts` (new) | Initials for people cards (AC5) |
| `src/lib/views/MovieListView.svelte` | Count in heading, genre chips (apply at once), poster grid (AC2 to AC4) |
| `src/lib/views/PeopleListView.svelte` | Count in heading, initials card grid (AC5) |
| Tests | `initials.test.ts` (new); `movies.test.ts`, `people.test.ts` (genre select → radio; three new tests); `design-rules.test.ts` (chip touch target, two contrast checks); `e2e/mocked/a11y.e2e.ts` (genre select → chip click); `e2e/mocked/screenshots.e2e.ts` (new, runs only with `SCREENSHOT_DIR`) |

No contract, validation, backend or dependency change.

## Evidence
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `7a-gates-final-20261022-1148.log` |
| `pnpm lint` | pass | same |
| `pnpm build` | pass | same |
| `pnpm exec playwright test --project=mocked` | 57 passed (axe on every route and state, no horizontal scroll and 44 px targets at 360, 768 and 1280 px), 6 skipped (screenshot spec) | `7a-e2e-mocked-20261022-1147-chips2.log` |
| Screenshots | movies and people at 360, 768, 1280 px | `screenshots-7a/` |
| `python3 -I scripts/check_traceability.py` | OK: no gaps | run in session |
| **`pnpm test` (Vitest)** | **NOT RUN successfully: 224 of 224 fail before any assertion, for an environment reason (below)** | `7a-baseline.log` (before any change: 209/209 fail), `7a-gates-final-20261022-1148.log` |

Earlier logs in this folder record problems found and fixed along the way: a lint error (`href` without `resolve()` in `PosterCard`), an E2E selector still using the old genre select, and a 360 px overflow caused by the hidden radios escaping the chip scroll box (fixed with `position: relative`; the responsive E2E caught it).

## Why the unit tests could not run (owner decision 2026-10-22: code now, test later)
`frontend/package.json` and `pnpm-lock.yaml` were changed at 10:01 today outside this session. The new lockfile resolves vite 8.3.5, rolldown 1.2.15, caniuse-lite and electron-to-chromium versions published on 2026-10-21, and pnpm's `minimumReleaseAge` policy rejects them (`ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION`). A partial install at 11:18 left two vitest copies in `node_modules`, so `@testing-library/jest-dom` registers against a different `expect` than the test runner and every test fails with "expected any number of assertion, but got none" (`requireAssertions: true`). No policy was relaxed and no dependency was changed.

**To finish 7a:** once the packages are old enough (rolldown 1.2.15 was published 2026-10-21 13:08 UTC, so after about 2026-10-22 13:10 UTC), run from `frontend/`:
```
pnpm install --frozen-lockfile
pnpm check && pnpm lint && pnpm test && pnpm build
```
and save the output here as `7a-unit-<timestamp>.log`. Tests written for 7a: `initials.test.ts` (8 cases), and in `movies.test.ts` "shows posters in a list where each card has one link named by the title", "applies a genre chip at once, keeps other applied filters and returns to page 1", "offers the genres as one keyboard-operable radio group"; in `people.test.ts` "shows each person as a card with initials and a single link"; in `design-rules.test.ts` the chip touch target and two contrast checks. These are unverified until that run.

## Acceptance criteria
| AC | Evidence |
|----|----------|
| AC1 header | `AppHeader.svelte.test.ts` (unrun); responsive E2E (passed); screenshots |
| AC2 poster grid | `movies.test.ts` (unrun); responsive E2E at 3 widths (passed); screenshots |
| AC3 genre chips | `movies.test.ts` two new tests (unrun); a11y E2E "filtered with no matches" via a chip click (passed) |
| AC4 heading, skeleton, states | `movies.test.ts` (unrun); a11y E2E empty and error states (passed) |
| AC5 people grid | `people.test.ts`, `initials.test.ts` (unrun); screenshots |

## Notes for the reviewer
- AC2 and AC3 wording in the story was aligned with the build: the card's one link is the title, stretched over the card; list state is component state, not URL state (the original AC text was wrong about the URL).
- The tint contrast is 5.74:1 (an earlier CSS comment said 6.5:1 and was corrected).
- Under D16 the independent review runs at the end of development, so 7a is not reviewed yet.
