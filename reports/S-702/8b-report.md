# S-702 part 8b report: year picker

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented; unit and component tests not yet run (same environment block as S-701); not reviewed.**

## What changed
| File | Change |
|------|--------|
| `src/lib/years.ts` (new) | Page maths (20 years, aligned to multiples of 20), gaps outside the allowed range, clamping, key moves (±1, ±4 rows, ±20 pages) |
| `src/lib/components/YearPicker.svelte` (new) | Labelled text field ("From year" / "To year", `inputmode="numeric"`, placeholder "yyyy") plus a calendar button ("Choose from year", `aria-haspopup="dialog"`, `aria-expanded`). The popover is a non-modal `dialog` with Earlier/Later years (`aria-disabled` at the ends, so focus is never dropped), the range in a polite live region, a `group` of year buttons with roving `tabindex` and `aria-pressed` for the selected year, Clear and This year. Esc and outside click close; picking or Esc returns focus to the calendar button. Opens to the left when there is no room on the right. Calendar icon drawn in CSS (no icon font) |
| `src/lib/views/MovieListView.svelte` | The two year `TextField`s replaced by `YearPicker`s bound to the same state; validation and Apply unchanged |
| Tests | `years.test.ts` (new), `YearPicker.test.ts` (new, 9 tests), `movies.test.ts` (1 new test: pick via the grid, then Apply), `design-rules.test.ts` (YearPicker touch target), `e2e/mocked/filters.e2e.ts` (new: axe with the picker open, no sideways scroll, popover inside the screen, every button ≥ 44 px, keyboard pick and focus return, at 360 and 1280 px), screenshot spec |

No contract or backend change.

## Evidence
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `8b-gates-final-20261022-1404.log` |
| `pnpm lint` | pass | same |
| `pnpm build` | pass | same |
| `pnpm exec playwright test --project=mocked` | 65 passed, 21 skipped (screenshot spec) | same |
| Screenshots | From picker (1995 selected) and To picker (last page, opens left) at 360 and 1280 px | `screenshots-8b/` |
| **`pnpm test` (Vitest)** | **NOT RUN successfully: 269 of 269 fail before any assertion (environment)** | same |

Found and fixed: a `svelte-ignore` that lint flagged as unused; an E2E lookup that matched both the field and "Choose to year" (Playwright label matching is a substring; now `exact: true`); the header of edge pages showed years that are not offered ("2020–2039"), now "2020–2031".

## Acceptance criteria
| AC | Evidence |
|----|----------|
| AC4 grid, paging, Clear, This year, bounds | `YearPicker.test.ts`, `years.test.ts` (unrun); `filters.e2e.ts` (passed); screenshots |
| AC5 keyboard, focus, 44 px | `YearPicker.test.ts` keyboard test (unrun); `filters.e2e.ts` keyboard pick, focus return and 44 px check (passed) |
| AC6 fills the field; validation and Apply unchanged | `movies.test.ts` new test plus the existing year-range tests (unrun); a11y E2E filter states (passed) |

## Notes
- The lockfile check now reports 18 blocked entries instead of 19, so the packages are starting to pass the release-age policy.
- Not covered: a screen-reader pass on the picker; the `stack` E2E project.
