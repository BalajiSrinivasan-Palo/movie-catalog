# S-701 part 7b report: cinematic detail pages

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented; unit and component tests not yet run (same environment block as 7a); not reviewed.**

## What changed
| File | Change |
|------|--------|
| `src/lib/styles/global.css` | Hero tokens: `--color-hero-bg`, `-text` (14.9:1), `-muted` (9.8:1), `-pill` (text on it 10.3:1), `-focus` (7.9:1) |
| `src/lib/components/HeroBand.svelte` (new) | Dark rounded band with `back`, `media`, body and `actions` slots; picture stacks above text below 600 px; light focus ring inside the band |
| `src/lib/components/PosterCard.svelte` | `level` prop (2 or 3) so filmography titles sit under the section `h2` |
| `src/lib/views/MovieDetailView.svelte` | Hero: back link, poster, `h1` title, "year · runtime · N credits", genre pills (`list` named "Genres"), synopsis, Edit and Delete. Artwork uploader and Remove artwork directly below. The bottom "Back to movies" link moved into the hero |
| `src/lib/views/CreditsSection.svelte` | Credits are a card grid with an initials circle; unavailable or unknown people are dashed cards with "?" and keep their role, Edit and Remove. Add, edit and remove flows unchanged |
| `src/lib/views/PersonDetailView.svelte` | Hero: back link, initials circle, name, born date, bio, Edit and Delete. Filmography is a poster grid. Blocked-delete alert unchanged |
| Tests | `movies.test.ts` (2 new hero tests; dashed-card assertions added to the EC-15 test), `people.test.ts` (1 new hero/filmography test), `design-rules.test.ts` (hero contrast test; HeroBand touch target), `e2e/mocked/screenshots.e2e.ts` (detail pages added) |

No contract, validation, backend or dependency change.

## Evidence
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `7b-gates-final-recheck-*.log` (after the last edit, a doc comment) |
| `pnpm lint` | pass | same |
| `pnpm build` | pass | `7b-gates-final-20261022-1154.log` |
| `pnpm exec playwright test --project=mocked` | 57 passed, 15 skipped (screenshot spec) | same |
| Screenshots | movie with and without artwork, person, at 360, 768 and 1280 px | `screenshots-7b/` |
| `python3 -I scripts/check_traceability.py` | OK: no gaps | run in session |
| **`pnpm test` (Vitest)** | **NOT RUN successfully: 229 of 229 fail before any assertion (environment, see `7a-report.md`)** | `7b-gates-final-20261022-1154.log` |

Found and fixed during 7b: an unused-variable lint error (Prettier's Svelte plugin rewrote `this="h{level}"` to `"h"`; now `this={`h${level}`}`), and an axe **color-contrast (serious)** failure on `/movies/m2` and the uploader-error state: the "No image" placeholder kept its light background inside the dark band. Fixed by giving the placeholder a dark box in the hero (`7b-gates-20261022-1152.log` shows the failure, `7b-e2e-mocked-20261022-1153.log` the fix).

## Acceptance criteria
| AC | Evidence |
|----|----------|
| AC6 movie hero | `movies.test.ts` "opens with a hero band…", "leaves out a missing runtime…" (unrun); a11y and responsive E2E on `/movies/m1`, `m2`, `m3` (passed); screenshots |
| AC7 credit cards | `movies.test.ts` EC-15 test with dashed-card assertions, `credits.test.ts` unchanged flows (unrun); a11y E2E with credit dialogs (passed) |
| AC8 person hero and filmography | `people.test.ts` "shows a hero band and the filmography…" (unrun); a11y and responsive E2E on `/people/p1` (passed); screenshots |
| AC9 contrast | `design-rules.test.ts` hero contrast test (unrun); axe colour-contrast on every detail route and state (passed) |

## Notes for the reviewer
- Story wording aligned with the build: AC6 keeps the artwork uploader below the hero (7c turns it into the drop zone), and AC7 keeps the existing "Name unavailable" / "Unknown person" wording rather than a new "Person unavailable" text, so EC-15 messages stay as specified.
- To finish 7b, run the same command as in `7a-report.md` once the lockfile installs; the 7b unit tests are unverified until then.
