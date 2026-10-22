# S-701 review (round 1): visual refresh, parts 7a, 7b and 7c

Reviewed by: reviewer (2026-10-22)

**Independence:** S-701 was planned and built in the main session before D26. The reviewer did not plan or implement it. There is no `reports/S-701/implementation.md`. The part reports `7a-report.md`, `7b-report.md` and `7c-report.md` serve as the implementation record and the list of changed files. Nothing is committed, so the files below were read as they are in the working tree on 2026-10-22, around 15:10.

**Important context:** S-702 (search scope, year picker, multi-select genres) was built on top of S-701 in the same uncommitted tree. It changed `GenreChips.svelte` (14:19), `MovieListView.svelte` and `PeopleListView.svelte` (14:57), `movies.test.ts` (14:19) and `design-rules.test.ts` (14:01). So the S-701 code cannot be reviewed apart from S-702. Where S-702 replaced S-701 behaviour, this review says so and does not judge the S-702 part.

## Files read
- Standard: `docs/backlog/stories/S-701.md`, `docs/plans/S-701.md`, `docs/assessment/decisions-log.md` (D15, D21 to D26), ADR-014, and `docs/backlog/stories/S-702.md` (for the overlap).
- Code (`frontend/src/`): `lib/styles/global.css`, `lib/components/AppHeader.svelte`, `GenreChips.svelte`, `PosterCard.svelte`, `Skeleton.svelte`, `HeroBand.svelte`, `ArtworkUploader.svelte`, `ArtworkImage.svelte`, `Button.svelte` (variants), `Pagination.svelte` (markup), `lib/initials.ts`, `lib/drag.ts`, `lib/views/MovieListView.svelte`, `PeopleListView.svelte`, `MovieDetailView.svelte`, `PersonDetailView.svelte`, `CreditsSection.svelte`, `routes/+layout.svelte`.
- Tests: `lib/initials.test.ts`, `lib/drag.test.ts`, `lib/components/design-rules.test.ts`, `ArtworkUploader.test.ts`, `AppHeader.svelte.test.ts`, `lib/views/movies.test.ts` and `people.test.ts` (the S-701 cases), `e2e/mocked/dropzone.e2e.ts`, `e2e/mocked/responsive.e2e.ts`.
- Evidence: `reports/S-701/7a-baseline.log`, `7c-gates-final-20261022-1336.log` (check, lint, build, Playwright, Vitest), the other 7a and 7b logs (from the part reports), `reports/filter-layout-gates-20261022-1458.log` (the latest gates run, after the 14:57 edits), and the screenshots `screenshots-7c/movies-360.png`, `movie-with-artwork-1280.png` and `dropzone-dragging-360.png`.
- Not run by the reviewer: any command. I did a standalone contrast calculation of the hero tokens, and it matches the CSS comments: hero text 14.94, muted 9.78, focus 7.89, text on pill 10.25, primary on tint 5.74. That calculation is a cross-check only. It is not test evidence.

## Evidence summary
| Gate | Status | Source |
|------|--------|--------|
| `pnpm check` | Pass (0 errors, 0 warnings) | `7c-gates-final-20261022-1336.log`; current tree `filter-layout-gates-20261022-1458.log` |
| `pnpm lint` | Pass | same |
| `pnpm build` | Pass | same |
| Playwright `--project=mocked` | Pass. 60 passed, 17 skipped at 13:36. 68 passed, 21 skipped at 14:58, including the 3 `dropzone.e2e.ts` tests and responsive checks at 360, 768 and 1280 px | same |
| `pnpm test` (Vitest) | **Not verified.** All 239 tests fail before any assertion (an environment problem: two vitest copies). The baseline before S-701 was already 209 of 209 failing | `7a-baseline.log`, `7c-gates-final-20261022-1336.log` |
| `pnpm install --frozen-lockfile` | **Failed** (`ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION`). Every gate above printed "node_modules may be out of sync with your lockfile" | every gates log |
| `check_traceability.py` | **Not verified**: the reports say "run in session" and saved no output | none |
| `./scripts/verify.sh` | **Not run** (it is in the plan's command list) | none |
| Stack E2E | Not run (the 7c report says so) | none |
| Screenshots at 360, 768 and 1280 px | Present for the list, detail and uploader (the uploader also appears inside the detail screenshots at 768 px). They were taken before S-702 changed the list page | `screenshots-7a/`, `-7b/`, `-7c/` |

## Acceptance criteria
| AC | Verdict | Proof / reason |
|----|---------|----------------|
| AC1 header | Met in code. The named test is not verified | `AppHeader.svelte:70-98`: tinted pill on `aria-current`, search on its own row below 600 px. `AppHeader.svelte.test.ts` "marks the current section…" was not run. Responsive E2E and the screenshot `movies-360.png` pass and back this up |
| AC2 poster grid | Met in code. The named test is not verified | `global.css:119-136` (2/3/5 columns, `minmax(0,1fr)`), `PosterCard.svelte` (one stretched title link). `movies.test.ts` "shows posters in a list where each card has one link…" was not run. Responsive E2E at 3 widths passed (no horizontal scroll) |
| AC3 single-choice radio chips with "All" | **Not met as written; superseded.** The tree now has S-702's multi-select checkboxes with no "All" chip (D22, D23, S-702.AC7) | `GenreChips.svelte:19-34`. The S-701 story was never amended (finding R1). What still holds: chips apply at once, go back to page 1 and keep the applied sort (`MovieListView.svelte:104-108`), and they scroll inside their own row below 600 px (`GenreChips.svelte:41-51`). `movies.test.ts:130` was not run |
| AC4 count, single Add, poster skeleton, states | Met with a wording deviation. Test not verified | The count is a separate `<p>` ("128 titles") beside the `h1`, not "Movies · 128 titles" inside the heading (finding O1). Skeleton `variant="posters"`. `movies.test.ts:50` was not run. The a11y E2E empty and error states passed |
| AC5 people grid | Met in code. Tests not verified | `PeopleListView.svelte:109-117`, `initials.ts`. `people.test.ts:47` and `initials.test.ts` were not run |
| AC6 movie hero | Met in code. Tests not verified | `MovieDetailView.svelte:72-120`. `movies.test.ts:279`, `:290` were not run. The a11y and responsive E2E on `/movies/m1..m3` passed. Screenshot matches |
| AC7 credit cards | Met with a wording deviation. Test not verified | `CreditsSection.svelte:215-247`. The meta line reads "Actor as Vincent", not "role · character" (finding O2). The dashed card keeps the EC-15 wording, the role, Edit and Remove. The EC-15 test in `movies.test.ts` was not run |
| AC8 person hero and filmography | Met in code. Test not verified | `PersonDetailView.svelte:73-129`. `people.test.ts:107` was not run. The E2E on `/people/p1` passed |
| AC9 hero contrast at least 4.5:1 | **Not verified** (the named test did not run) | `design-rules.test.ts:66-72` is the right test. Supporting evidence: axe colour-contrast passed on every detail route (E2E), and the reviewer's own calculation matches |
| AC10 drop zone, labelled input | Met in code. Unit test not verified | `ArtworkUploader.svelte:118-142`. The a11y E2E passed (`getByLabel(/Image file/)`, axe). The responsive 44 px check via the label passed |
| AC11 drag highlight | Met. Covered by E2E | `dropzone.e2e.ts:25` passed (synthetic DataTransfer). The unit tests were not run |
| AC12 same path, several files, busy, non-file | Met in code. **Mostly not verified** | One `accept()` path (`ArtworkUploader.svelte:68-77`). Only the single valid drop is proven in E2E. The several-files, busy, invalid-file and non-file cases rely on unit tests that did not run (`ArtworkUploader.test.ts:179-229`) |
| AC13 no navigation on a missed drop | Met. Covered by E2E | `+layout.svelte:14-19`. `dropzone.e2e.ts:39`, `:54` passed. No real OS drag gesture was tested (the report says so) |

## Plan tasks
| Task | Status |
|------|--------|
| Precondition: frontend gates green before starting | **Not met**. Vitest was already failing 209/209 (`7a-baseline.log`). This was recorded, and the owner chose "code now, test later" |
| 7a tokens (`--color-accent-tint`, hero tokens, `--radius-card`) | Done (plus `--radius-pill`, `--color-hero-pill`, `--color-hero-focus`). No hex colour outside `global.css` in the files read |
| 7a header pill | Done |
| 7a `PosterCard` | Done, with a deviation: the props are `movieId` and `level` instead of `href`. The title link is stretched rather than wrapping the whole card. Acceptable, and recorded in the story's AC2 |
| 7a movie grid 2/3/5 | Done |
| 7a `GenreChips` radios wired to the same `apply` path | Done in 7a, then **replaced by S-702** (checkboxes, its own `chooseGenres` path). The plan said "URL state"; the 7a report records that list state is component state |
| 7a people grid, `initials` with `Array.from` | Done |
| 7a poster skeleton | Done |
| 7b `HeroBand` with snippets, hero focus ring, 44 px | Done |
| 7b movie detail: Delete "stays where it is" | **Deviated**: Delete moved into the hero. This matches the amended story AC6, but the 7b report does not list it as a plan deviation (finding O3) |
| 7b credits card grid, dialogs unchanged | Done. The plan's "Person unavailable" text was dropped in favour of the existing EC-15 wording, as recorded |
| 7b person hero and filmography `PosterCard` | Done |
| 7b contrast tests and `HeroBand` touch target | Done (tests written, not run) |
| 7c `accept(file)` single path | Done |
| 7c label as the drop zone, input kept focusable | Done |
| 7c drag handlers, `isFileDrag`, busy guard, first-file note | Done |
| 7c window `dragover`/`drop` guard in `+layout.svelte` | Done |
| 7c copy strings | Done |
| Tests: `initials.test.ts`, view tests, `credits.test.ts`, uploader drop tests, design rules | Written. **None verified** (Vitest blocked). The unavailable-card assertions went into `movies.test.ts` instead of `credits.test.ts`, which is acceptable. The plan's "chips are radios in a named group" test was replaced by S-702's checkbox test |
| E2E: a11y and responsive rerun, drag-and-drop attempted | Done and passed. Drag-and-drop is covered with a synthetic DataTransfer, and the limitation is stated |
| Commands: `pnpm install --frozen-lockfile`, `pnpm test`, `verify.sh`, traceability with saved logs | **Missing**: the install failed, Vitest is blocked, `verify.sh` was not run, and no traceability log was saved |
| Completion: reviewer sign-off; owner accepts the look | This review is the sign-off. Owner acceptance of the screenshots is not recorded |

## Security and architecture
- No `{@html}` in the files read. Titles, names, bios, synopses, character names and initials are text nodes. The static guard is in `design-rules.test.ts:78` (not run).
- The window drop guard only calls `preventDefault()` for file drags and never reads the file. Dropped files go through the same `validateArtworkFile` check as chosen files, and the server stays the authority.
- Frontend only: no GraphQL, gRPC, protobuf or validation change from S-701. I found no new imports of third-party packages in the files read. `package.json` and the lockfile changed at 10:01, before S-701 (7a report), so I cannot diff that change.

## Findings

### Blocking
None in the S-701 code.

### Required (close before Done)
- **R1, story out of date with the code (AC3).** `docs/backlog/stories/S-701.md` AC3 still specifies single-choice native radios with an "All" chip, but `frontend/src/lib/components/GenreChips.svelte:19-34` now renders S-702's checkboxes with no "All". Failing scenario: a walkthrough or traceability check against S-701.AC3 finds no radio group, and the test still tagged "S-701 AC3" (`movies.test.ts:130`) clicks a checkbox. Fix: the owner amends S-701.AC3 to say "superseded by S-702.AC7 (D22, D23)", keeping the parts that still hold (apply at once, page 1, keep applied sort and years, in-row scroll below 600 px).
- **R2, the Vitest suite has not run.** Every unit and component test named for AC1 to AC12 is unverified, including the only checks for AC9 contrast (`design-rules.test.ts:66-72`) and the AC12 several-files, busy and non-file cases (`ArtworkUploader.test.ts:200-229`). Fix: after a clean `pnpm install --frozen-lockfile`, run `pnpm check && pnpm lint && pnpm test && pnpm build` and save the output as `reports/S-701/unit-<timestamp>.log`. Done is allowed only if the run is green and no assertion was weakened.
- **R3, the gates ran against a node_modules tree that does not match the lockfile.** Every log has the frozen install failing and pnpm warning "node_modules may be out of sync with your lockfile" (for example `7c-gates-final-20261022-1336.log:71`). So check, lint, build and E2E passed on a partially installed tree with two vitest copies. Failing scenario: once the lockfile installs cleanly with vite 8.3.5 and rolldown 1.2.15, the build or E2E behaves differently. Fix: rerun the mocked Playwright suite in the same clean session as R2 and save the log.
- **R4, traceability and `verify.sh` have no saved output.** The 7a, 7b and 7c reports claim "check_traceability: OK, run in session" with no log. `./scripts/verify.sh` (plan, Commands) was not run. Under CLAUDE.md rule 2 these are "not verified". Fix: run both and save the output under `reports/S-701/`.

### Optional
- **O1** `MovieListView.svelte:125-131`: the count is a sibling `<p>` and not part of the heading, and it has no "Movies ·" prefix, so screen-reader heading navigation announces only "Movies". Either amend the AC4 wording or put the count inside the `h1` (for example `<h1>Movies <span class="count">· 128 titles</span></h1>`).
- **O2** `CreditsSection.svelte:227-231`: the meta line is "Actor as Vincent", while the story says "role · character". Amend the wording in AC7 (the current text matches the filmography and existing tests).
- **O3** `MovieDetailView.svelte:102-105`: Delete was moved into the hero, against the plan's "Delete stays where it is". It matches the amended AC6, but it is not listed as a deviation in `7b-report.md`.
- **O4** `design-rules.test.ts:74-76`: the test "a selected genre chip (page colour on text colour)" checks `--color-bg` on `--color-text`, but the selected chip is white on `--color-primary` (`GenreChips.svelte:75-78`). The real pair is already covered at line 52, so this test guards nothing and its name is misleading. Rename or remove it.
- **O5** `ArtworkUploader.svelte:53-56`: Safari has historically given `relatedTarget = null` on `dragleave`. Moving over a child span then clears `dragging` until the next `dragover` sets it again, so the highlight may flicker. The state recovers on its own, and no test covers moving between children.
- **O6** `+layout.svelte:14-16`: the window handler cancels `dragover` for file drags without setting `dropEffect = 'none'`, so the cursor shows "copy" over the whole page, which suggests a drop will work anywhere. Consider setting `dropEffect = 'none'` outside the zone, and check that this still prevents navigation.
- **O7** `ArtworkUploader.svelte:28,64`: the note "Only the first file was used." stays after that upload succeeds, until the next choose or drop.
- **O8** `CreditsSection.svelte:340-343`: the person-name link in a credit card is a plain inline link, about 20 px tall (`movie-with-artwork-1280.png`). The responsive E2E excludes plain links (`responsive.e2e.ts:24`), so the NFR-UX-01 44 px rule is not checked for it.
- **O9** `PosterCard.svelte:23-26`: each card announces the image "Poster of X" and then the link "X". Consider `alt=""` on a grid thumbnail, since the title link already names the card. That would change existing assertions (`movies.test.ts` "Poster of Heat"), so the owner should decide.
- **O10** `movies.test.ts:130-151`: the test named "keeps other applied filters" asserts only the sort, not the years.
- **O11** Screenshots in `reports/S-701/` were taken before S-702 and the 14:57 list edits, so they no longer show the current list page ("All" radio chip, old filter row). Owner acceptance of the look should use current screenshots.

## Verdict
**APPROVED pending unit-test run.** No defect in the S-701 code blocks it. The design matches D21 and ADR-014, the architecture and security rules hold, and every AC is met in code, except AC3, which S-702 superseded by owner decision. The story is **not Done**: the Vitest suite (R2) has not run, the gates must be rerun on a clean install (R3), traceability and `verify.sh` need saved logs (R4), and the story text must record the AC3 supersession (R1). The plan's and the story's status lines are left unchanged, as instructed. Once R1 to R4 are closed with saved logs and the unit run is green, a short follow-up review (`review-2.md`) can confirm and set Done.
