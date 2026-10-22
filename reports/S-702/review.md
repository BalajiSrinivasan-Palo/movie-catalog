# S-702 review: search scope menu, year picker and multi-select genres

Reviewed by: reviewer (2026-10-22)

**Independence:** S-702 was planned and implemented in the main session ("Implementer: Claude (main session)" in all three part reports), before D26. The reviewer did not plan or implement any part of it.

**Verdict: APPROVED pending unit-test run.** I found no blocking defect in the code. The story is **not Done**: the Vitest suite has not run, and three more Definition of Done items have no evidence yet (R1 to R3). Story and plan status lines are unchanged. The plan has no `Status:` line, and neither allowed value ("Done" or "Changes required") fits this verdict.

## What was read

- Standard: `docs/backlog/stories/S-702.md`, `docs/plans/S-702.md`, `docs/backlog/definition-of-done.md`, `docs/assessment/decisions-log.md` (D15, D22, D23, D26), `docs/adr/ADR-015-multi-genre-movie-filter-any-match.md`, `docs/plans/README.md`.
- Implementation record (in place of `implementation.md`): `reports/S-702/8a-report.md`, `8b-report.md`, `8c-report.md`.
- Frontend code: `frontend/src/lib/search-scope.ts`, `components/ScopeMenu.svelte`, `components/SearchBox.svelte`, `views/SearchView.svelte`, `routes/search/+page.svelte`, `stores/loader.svelte.ts`, `years.ts`, `components/YearPicker.svelte`, `components/GenreChips.svelte`, `views/MovieListView.svelte`, `components/StatusFooter.svelte`, `components/Button.svelte`, `api/catalog.ts` (filter and search functions).
- Frontend tests: `search-scope.test.ts`, `years.test.ts`, `components/YearPicker.test.ts`, test names in `SearchBox.test.ts` and `views/search.test.ts`, the genre tests in `views/movies.test.ts`, `api/catalog.test.ts` (genres), `design-rules.test.ts` (file list), `e2e/mocked/filters.e2e.ts`, `e2e/mocked/search.e2e.ts`, the scope test in `e2e/mocked/a11y.e2e.ts`, `e2e/mocked/screenshots.e2e.ts`, and the `genres`/search parts of `e2e/support/mock-api.ts`.
- Contracts and backend: `contracts/.../movie/v1/movie_service.proto` (`ListMoviesRequest`), `api-gateway/src/main/resources/graphql/schema.graphqls` (`MovieFilter`), `api-gateway/.../Views.kt` (`MovieFilter`), `CatalogControllers.kt` (`movies`), `GraphQlLimits.kt`, `movie-service/.../MovieValidator.kt`, `MovieService.kt` (`list`), `Repositories.kt` (`MovieQueries`), `MovieGrpcService.kt` (`listMovies`), `Domain.kt` (`Genre`).
- Backend tests: S-702 tests in `ValidatorTest.kt`, `MovieServiceTest.kt`, `MovieGrpcTest.kt`, and the S-702 and complexity tests in `CatalogGraphQlTest.kt`.
- Evidence: `8a-gates-final-20261022-1356.log`, `8b-gates-final-20261022-1404.log`, `8c-frontend-gates-final-20261022-1411.log`, `8c-fixes-gates-20261022-1419.log`, `footer-fix-gates-20261022-1440.log`, `8c-gradle-targeted-20261022-1414.log`, `8c-gradle-test-results-summary.txt`, `search-complexity-gateway-test-20261022-1434.log`, `reports/filter-layout-gates-20261022-1458.log`, `screenshots-8a/`, `screenshots-8b/` (viewed `year-picker-to-360.png`).
- The JUnit XML written by the owner's Gradle runs, read directly (I did not run Gradle): `movie-service/build/test-results/test/*.xml` (06:15Z, i.e. 14:15 local: 76 tests, 0 failures, 0 errors; the four S-702 tests are present) and `api-gateway/build/test-results/test/*.xml` (06:34Z, 14:34 local: 55 tests, 0 failures; the two S-702 tests and "the app's own grouped search is within the complexity limit…" are present).

Commands I ran: `python3 -I scripts/check_traceability.py`, which printed `OK: no gaps`. **Side effect:** the script rewrites `docs/backlog/coverage-report.md` (now timestamped 16:00). That file is generated, and the S-702 rows in it look as expected. I did not run Gradle (D15), pnpm or Playwright, and I did not touch the running dev stack.

## Acceptance criteria

"Code" is my own reading of the code. "Evidence" is a test that actually ran, with its saved log. The unit and component tests (Vitest) are written but **never ran**: all 269 fail before any assertion because of the pnpm minimum-release-age lockfile block (`8c-fixes-gates-20261022-1419.log`, lines 4302 to 4308). So no criterion can be "met" from a Vitest test.

| AC | Code | Status | Evidence that ran / reason |
|----|------|--------|----------------------------|
| AC1 scope button, menu, keyboard, roles | Meets it (`ScopeMenu.svelte`: `aria-haspopup="menu"`, `role="menu"`, `menuitemradio` + `aria-checked`, Down/Up/Home/End, Esc returns focus, Tab and outside click close) | **Not verified (partly evidenced)** | Passed: `a11y.e2e.ts:167` "search scope: menu open, then Titles…" (axe with the menu open, ArrowDown + Enter chooses, focus back on the button); `search.e2e.ts:23` (focus after a mouse choice). Esc, Up, Home/End and Space are covered only by `SearchBox.test.ts` "is keyboard-operable…" (not run). Log: `footer-fix-gates-20261022-1440.log` lines 461 and 521 |
| AC2 scope in the URL, per-scope API, states unchanged | Meets it (`parseScope`/`searchPath`; `SearchView` calls `searchMovies` / `searchPeople` / `searchAll`; stale guard compares query **and** scope) | **Not verified (partly evidenced)** | Passed: `a11y.e2e.ts:167` (`/search?q=the&scope=titles` shows only Movies). People scope, the empty, error and too-short states per scope, and stale answers per scope rely on `search.test.ts` lines 201 to 242 and `search-scope.test.ts` (not run) |
| AC3 label/placeholder per scope, focus | Meets it (`searchLabel`; `reset: false` keeps focus across `goto`) | **Not verified (partly evidenced)** | Passed: `a11y.e2e.ts:167` (searchbox named "Search movies", focus on the scope button); `search.e2e.ts:8` (focus kept while typing across navigations). "Search people" and the "(cast and crew)" item text are only in unit tests (not run) |
| AC4 year grid, 20 per page, bounds, header, Clear, This year, focus return | Meets it (`years.ts`, `YearPicker.svelte`; the header uses `first`/`last` clamped to min/max) | **Not verified (partly evidenced)** | Passed: `filters.e2e.ts:16` at 360 and 1280 px (opens, axe, inside the screen, keyboard pick, focus returns). `screenshots-8b/year-picker-to-360.png` shows the header "2020–2031" with 2032+ not offered. The 1888 edge, Clear and This year are only in `YearPicker.test.ts` / `years.test.ts` (not run) |
| AC5 keyboard grid, Esc, 44 px | Meets it (roving `tabindex`, `moveYear` ±1/±4/±20, clamping, Esc on the popover) | **Not verified (partly evidenced)** | Passed: `filters.e2e.ts:16` (every popover button ≥ 44 px, ArrowLeft + Enter picks, focus returns). Arrow-row, PageUp/PageDown and Esc only in `YearPicker.test.ts` "is keyboard-operable…" (not run) |
| AC6 picking fills the field; validation and Apply unchanged | Meets it (`bind:value` on the same `yearFrom`/`yearTo`; `apply()` is unchanged) | **Not verified (partly evidenced)** | Passed: `filters.e2e.ts:16` (the field holds the picked year). The `filter.year.invalid` and Apply behaviour after a pick are in `movies.test.ts` (not run) |
| AC7 checkbox chips, several, ✕, fixed order, no summary, no All, applies at once, page 1 | Meets it (`GenreChips.svelte` sorts into `GENRES` order; `chooseGenres` sets `page = 0`; ✕ is `aria-hidden`; legend "Genre") | **Not verified (partly evidenced)** | Passed: `filters.e2e.ts:60` "several genre chips filter with any-match, and Clear filters resets" (axe included). ✕, fixed order, page reset and the absence of the summary line are in `movies.test.ts:153` and `:130` (not run) |
| AC8 `genres` in GraphQL and protobuf, any-match, empty = none, `genre` kept and combined | Meets it (proto `repeated Genre genres = 7`, additive; GraphQL `genres: [Genre!]`, `genre @deprecated`; `cb.or(isMember…)` in both count and page queries) | **Met** | Passed (owner run 14:14, D15): `MovieServiceTest` "S-702 AC8 list with several genres matches any of them, once each, with the right total"; `MovieGrpcTest` "S-702 AC8 AC9 listMovies reads the genres list…"; `CatalogGraphQlTest` "S-702 AC8 the genres filter reaches movie-service as a list, alongside the deprecated genre". Logs: `8c-gradle-targeted-20261022-1414.log` (BUILD SUCCESSFUL), `8c-gradle-test-results-summary.txt`, XML checked |
| AC9 >15 / duplicate / unknown → `INVALID_ARGUMENT` `movie.genres.invalid`; gateway field error; no duplicates; correct total | Meets it (`MovieValidator.validateGenreFilter`; `Genre.from` gives null for `UNRECOGNIZED`) | **Met** | Passed: `ValidatorTest` "S-702 AC9 genre filter allows up to all 15…" (15 accepted, 16 / duplicate / null rejected); `MovieServiceTest` "S-702 AC9 list rejects duplicate or unknown genres…"; `MovieGrpcTest` (duplicate, `GENRE_UNSPECIFIED`, wire value 999); `CatalogGraphQlTest` "S-702 AC9 a rejected genres list comes back as a keyed field error". Same logs |

## Plan tasks

| Part | Task | Result |
|------|------|--------|
| 8a | `search-scope.ts` | Done. Deviation: named `parseScope`, `searchPath`, `searchLabel` instead of `scopeFromUrl`, `searchHref`. Recorded in the 8a report table; acceptable |
| 8a | `ScopeMenu.svelte` menu-button pattern | Done as planned (`<li role="none">` around a `button`, recorded) |
| 8a | `SearchBox` scope button in the same box, re-search on change | Done. Also fixes the SvelteKit 3 `goto` options (`reset: false`). This is outside the plan but recorded, and covered by `search.e2e.ts` |
| 8a | `SearchView` per-scope fetch, "stale guard reused unchanged" | Done. Deviation: the guard now also compares scope. Needed, and recorded |
| 8b | `years.ts` | Done (`pageStart`, `yearsOnPage`, `clampYear`, `moveYear` instead of `pageOf`). Acceptable |
| 8b | `YearPicker` "wraps the existing `TextField`" | Done, with a deviation: it has its own `<input>`, label and error markup instead of reusing `TextField`. **Not recorded** (O1) |
| 8b | `MovieListView` uses two pickers; validation and Apply unchanged | Done |
| 8c | proto field 7 | Done, additive, no renumbering |
| 8c | GraphQL `genres`, `genre` deprecated | Done |
| 8c | gateway mapping (`addAllGenres`, keep `genre`) | Done (`CatalogControllers.kt:111-112`) |
| 8c | validation "in `MovieGrpcService`" | Deviation: in `MovieValidator`, called from `MovieService.list`. Recorded. This is better under rule 5 (business validation in the owning service's service layer). The gRPC layer only maps unknown values to null |
| 8c | `MovieService`/`Repositories` `list(genres: Set<Genre>)` | Done. `MovieService.list` takes `genres: List<Genre?> = emptyList()` as a trailing default parameter so that existing callers stay unchanged; `MovieQueries.list` takes the `Set`. Recorded |
| 8c | frontend `catalog.ts`, `GenreChips`, `MovieListView`, mock API | Done. The summary line from the plan was removed by owner decision D23 (story AC7 amended). Recorded |
| Tests | frontend unit/component, `design-rules` touch targets | Written (`ScopeMenu`, `YearPicker` and `GenreChips` are in `design-rules.test.ts`); **not run** (R1) |
| Tests | movie-service and gateway tests | Done and passed (owner run) |
| Tests | E2E mocked (axe, responsive, any-match) | Done and passed (`footer-fix-gates-20261022-1440.log`: 68 passed, 21 skipped) |
| Tests | screenshots of the three controls at 360 and 1280 px | Scope menu (8a) and year picker (8b) saved. **Genre chips (8c) missing** (R3) |
| Commands | `pnpm check`, `lint`, `build`, `playwright --project=mocked` | Ran, pass (logs above; latest `reports/filter-layout-gates-20261022-1458.log`) |
| Commands | `pnpm test` | **Not run successfully** (environment) |
| Commands | `./gradlew :contracts:build :movie-service:test :api-gateway:test` (owner) | Ran, BUILD SUCCESSFUL (`8c-gradle-targeted-20261022-1414.log`) |
| Commands | `./gradlew build` (owner), `./scripts/verify.sh` | **Not run** (R2) |
| Commands | `check_traceability.py` | Ran by the reviewer: `OK: no gaps` |

Architecture: unchanged and respected. The browser uses only `/graphql`; the gateway reaches movie-service over gRPC. No new dependency. No `{@html}` (the `design-rules.test.ts` guard exists, but it has not run). The contract change is additive. The `genre` field number 4 is kept, so older clients keep working, as ADR-015 states.

## Findings

### Blocking
None.

### Required (before the story can be Done)

- **R1: The frontend unit and component suite has never run.** Evidence: `reports/S-702/8c-fixes-gates-20261022-1419.log`, lines 4302 to 4308 (`Test Files 27 failed (27)`, `Tests 269 failed (269)`, `ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION`). Failing scenario: any mistake in the 25 or more new or changed tests (for example the AC1 Esc path, the AC2 People scope, the AC4 1888 page, or the AC7 fixed order) is unknown today. The same applies to the S-701 chip tests that moved from radio to checkbox (plan, "Regression risk"). Fix: once the lockfile installs, run `cd frontend && pnpm test` and save the log under `reports/S-702/`. Any failure goes back to the implementer.
- **R2: The full backend build and the project gate have not run.** The plan's Commands and DoD item 2 require `./gradlew build` (owner runs) and `./scripts/verify.sh`. Only the targeted build ran. Failing scenario: people-service also compiles against `contracts`, and its tests, ktlint, detekt and coverage are not covered by `8c-gradle-targeted-20261022-1414.log`. Note that people-service sources changed at 15:00 for other work (`reports/birthdate-fix.md`), so a fresh full build is needed anyway. Fix: the owner runs `./gradlew build --console=plain 2>&1 | tee reports/S-702/8c-gradle-build-<time>.log` and then `./scripts/verify.sh`.
- **R3: Genre-chip screenshots are missing.** The story DoD asks for screenshots of the three controls at 360 and 1280 px. `screenshots-8a/` (scope) and `screenshots-8b/` (year picker) exist, but nothing shows several selected chips with ✕. `e2e/mocked/screenshots.e2e.ts` has no genre-chip case (lines 63 to 94 cover only scope and year). Also, the 8b screenshots predate 8c and D23: `year-picker-to-360.png` still shows the old "All" chip. Fix: add a screenshot of two selected chips at 360 and 1280 px and get the owner's acceptance.
- **R4: The reviewed code is newer than the S-702 record.** `frontend/src/lib/views/MovieListView.svelte` (and `PeopleListView.svelte`) were modified at 14:57, after the last S-702 report entry (14:41). No S-702 report says what changed. The only matching evidence is `reports/filter-layout-gates-20261022-1458.log` (check 0 errors, lint and build pass, mocked E2E 68 passed), which sits outside `reports/S-702/`. Failing scenario: a reviewer or the owner reading the S-702 record cannot tell which version of the movie filter form was approved, or whether the layout change touched the AC6 and AC7 wiring. I reviewed the file as it is now, and the wiring is correct. Fix: add a line to `8c-report.md` (or a separate note) describing the 14:57 change and citing that log.

### Optional

- **O1:** `frontend/src/lib/components/YearPicker.svelte:107-132` rebuilds the label, input, `aria-invalid`/`aria-describedby` and the error paragraph instead of wrapping `TextField` as the plan says. This is an unrecorded deviation, and it means two copies of the field markup to keep consistent. Either record the reason (the calendar button must sit inside the field's border) or reuse `TextField`.
- **O2:** `YearPicker.svelte:100-102` closes only on Esc, outside pointer-down or a pick, not when focus leaves by Tab. `ScopeMenu.svelte:66-67` does close on Tab. Scenario: open "From year", Tab past "This year" to the To field and open "To year". Both popovers are now open, and at 360 px they overlap the form. Consider closing on `focusout` when the new focus target is outside `root`.
- **O3:** `MovieListView.svelte:136-137`: the range error is passed only to the From picker, so when the bad value is in "To year" that input gets no `aria-invalid` and no description. This carries over unchanged from before (AC6 says "behave as before"), so it is not a regression. Worth a later story.
- **O4:** `movie-service/src/test/kotlin/com/moviecatalog/movie/ValidatorTest.kt:76`: the "16 entries" case (`Genre.entries + Genre.ACTION`) also contains a duplicate, so it cannot tell the size cap from the duplicate rule. With only 15 genre values, more than 15 entries always contain a duplicate or an unknown value, so the cap (`MovieValidator.kt:53`) is in practice a cheap early exit. That is fine, but say so in a comment so nobody expects a separate test.
- **O5:** Some claims have no saved log in `reports/S-702/`: the 14:31 `curl` checks against the restarted stack (6 movies for `[COMEDY, CRIME]`, `VALIDATION_FAILED` for `[WAR, WAR]`), and the per-test counts for the 14:34 gateway run (55 tests). The 14:34 console log shows only `BUILD SUCCESSFUL`. I confirmed the 14:34 counts from the JUnit XML in `api-gateway/build/test-results/test/` (55 tests, 0 failures, timestamp 06:34Z), but that directory is overwritten by the next build. Save a results summary for that run, as was done for 14:14, and save the curl output if it is to count as evidence.
- **O6:** Scope additions made under S-702: `api-gateway/.../GraphQlLimits.kt:36-47` (it changes a security limit from S-604 / NFR-SEC-05), `scripts/dev.sh` (S-004) and `StatusFooter.svelte` (D23 and S-004.AC1). Each is recorded in `8c-report.md` with a test or a manual check. The complexity change looks correct: the app's search costs 221, seven aliased copies (1547) are still refused, and `search` group sizes still multiply their children. Still, the S-604 security record (`reports/security-review-1.md`) should get a note so the limit's history is in one place.

### Outside this story (for the owner)
`frontend/package.json` and `pnpm-lock.yaml` (10:01, before S-702) moved to `@sveltejs/kit ^3.0.0`. The 8a report flags that this change has no explanation. It is not an S-702 change, but DoD item 9 (justified dependencies) applies to whichever story made it.

## Verdict

**APPROVED pending unit-test run.** The code meets AC1 to AC9 as written. The contract change is additive. Validation lives in movie-service. AC8 and AC9 are proven by backend tests the owner ran. AC1 to AC7 are only partly proven by the mocked E2E suite until Vitest runs.

S-702 may be set to Done only after R1 (a green `pnpm test` log), R2 (`./gradlew build` and `./scripts/verify.sh` logs), R3 (genre-chip screenshots accepted by the owner) and R4 (the 14:57 change recorded) are closed and checked. Any Vitest failure turns this verdict into CHANGES REQUIRED. I did not change the story or plan status lines.
