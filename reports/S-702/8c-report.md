# S-702 part 8c report: multi-select genres (contracts, movie-service, gateway, frontend)

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented. Backend and contracts: targeted Gradle build and tests PASSED in the owner's terminal (2026-10-22 14:14). Full `./gradlew build` (step 2, includes people-service) not yet run. Frontend: check, lint, build and mocked E2E pass; unit tests not run (environment).** Not reviewed.

## What changed
| Layer | File | Change |
|-------|------|--------|
| Contract | `contracts/src/main/proto/moviecatalog/movie/v1/movie_service.proto` | `repeated Genre genres = 7` in `ListMoviesRequest` (additive; no renumbering); comments on `genre = 4` and on the `ListMovies` errors |
| Contract | `api-gateway/src/main/resources/graphql/schema.graphqls` | `MovieFilter.genres: [Genre!]`; `genre` marked `@deprecated` (kept) |
| movie-service | `MovieValidator.kt` | `validateGenreFilter(List<Genre?>)`: at most 15 (all genres), no duplicates, no unknown/unspecified → `INVALID_ARGUMENT` field `genres`, key `movie.genres.invalid` (service layer, as the Kotlin rules require) |
| movie-service | `MovieService.kt` | `list(…, genres: List<Genre?> = emptyList())`: validates, unions with the older `genre`, passes a `Set<Genre>` on. Default parameter, so existing callers and tests are unchanged |
| movie-service | `Repositories.kt` | `MovieQueries.list(genres: Set<Genre>, …)`: one `cb.or(isMember…)` predicate, used by both the count and the page query (subquery, no join, so no duplicate rows and a correct total) |
| movie-service | `MovieGrpcService.kt` | maps `request.genresList` with `Genre.from` (null for unknown, rejected by the service) |
| gateway | `Views.kt`, `CatalogControllers.kt` | `MovieFilter.genres` → `addAllGenres`; `genre` still mapped |
| frontend | `api/catalog.ts` | `MovieListParams.genres: Genre[]`; sends `filter.genres` (or null); no longer sends the deprecated `genre` |
| frontend | `GenreChips.svelte` | checkboxes (legend "Genre"); selected chips filled with ✕ (`aria-hidden`); summary "Comedy or Crime · 2 selected · Clear genres" in a polite live region; selection kept in the fixed genre order |
| frontend | `MovieListView.svelte` | `applied.genres`; chips apply at once and reset to page 1; Clear filters empties them |
| frontend | `e2e/support/mock-api.ts` | `filter.genres` any-match |

## Tests added or changed
- `ValidatorTest`: "S-702 AC9 genre filter allows up to all 15 known genres without duplicates" (empty, two, all 15; duplicate, unknown, 16).
- `MovieServiceTest`: "S-702 AC8 list with several genres matches any of them, once each, with the right total" (B has both genres and appears once; empty list = no filter; with a year bound; combined with the older `genre`; paging total 3 with page size 2); "S-702 AC9 list rejects duplicate or unknown genres with a keyed field error".
- `MovieGrpcTest`: "S-702 AC8 AC9 listMovies reads the genres list, combines it with genre, and rejects bad lists" (duplicate, `GENRE_UNSPECIFIED`, unknown wire value 999).
- `CatalogGraphQlTest`: "S-702 AC8 the genres filter reaches movie-service as a list, alongside the deprecated genre" (records the gRPC request); "S-702 AC9 a rejected genres list comes back as a keyed field error". The fake `listMovies` now records its last request and rejects duplicate genres.
- Frontend: `catalog.test.ts` (sends `genres`), `movies.test.ts` (radio → checkbox in the S-701 chip tests; new "selects several genres, shows ✕ and a summary, and Clear genres removes them all"), `e2e/mocked/filters.e2e.ts` (any-match in a real browser with axe).

## Evidence so far
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `8c-frontend-gates-final-20261022-1411.log` |
| `pnpm lint` | pass (after replacing a temporary `Set`, flagged by `svelte/prefer-svelte-reactivity`) | same |
| `pnpm build` | pass | same |
| `pnpm exec playwright test --project=mocked` | 66 passed, 21 skipped | same |
| `pnpm test` (Vitest) | **NOT RUN successfully: 269 of 269 fail before any assertion (environment)** | same |
| `protoc` check of the proto | Covered by `:contracts:generateProto` and `:contracts:compileJava` in the owner's run (no standalone `protoc` here) | `8c-gradle-targeted-20261022-1414.log` |
| `./gradlew :contracts:build :movie-service:test :api-gateway:test` (owner, D15) | **BUILD SUCCESSFUL in 52s**. movie-service 76 tests, api-gateway 54 tests, 0 failures, 0 errors, 0 skipped; all 6 new S-702 tests pass | `8c-gradle-targeted-20261022-1414.log`, `8c-gradle-test-results-summary.txt` (read from `build/test-results/test/*.xml`, 14:15) |
| `./gradlew build` (full, includes people-service) | **NOT RUN yet** (step 2 below) | — |

## Commands for the owner (run in your own terminal from the repository root)

Step 1: the contract and the two services this part touched, with the new tests:
```
./gradlew :contracts:build :movie-service:test :api-gateway:test --console=plain 2>&1 | tee reports/S-702/8c-gradle-targeted-$(date +%Y%m%d-%H%M).log
```

Step 2, if step 1 passes: the full build, to catch anything else the proto change touches (people-service also uses the contracts):
```
./gradlew build --console=plain 2>&1 | tee reports/S-702/8c-gradle-build-$(date +%Y%m%d-%H%M).log
```

Step 3: only the new tests by name, for a quick rerun after a fix:
```
./gradlew :movie-service:test --tests '*ValidatorTest*' --tests '*MovieServiceTest*' --tests '*MovieGrpcTest*' :api-gateway:test --tests '*CatalogGraphQlTest*' --console=plain
```

The `tee` lines save the output under `reports/S-702/`, which is the evidence D15 asks for. If anything fails, paste the failing part of the log and I'll fix it.

## Notes for the reviewer
- AC7 wording aligned: the button reads "Clear genres" (to differ from "Clear filters"), and there is no "All" chip.
- Both risks named before the run were cleared by it: the schema with `@deprecated` on an input field loaded (all gateway tests pass), and `addGenresValue(999)` was rejected as expected.
- Gradle reports deprecated features that will break under Gradle 10 (`--warning-mode all` lists them). This predates S-702 and is noted for S-002, not fixed here.

## Follow-up, 2026-10-22 14:19 (owner feedback)
1. **"Something went wrong on our side" with several genres selected.** Cause: the gateway and services running on ports 8080, 9091 and 9092 were started at 11:19, before the 8c contract change, so the running gateway does not know `MovieFilter.genres`. Confirmed with `curl`: a `genres` filter returns `ValidationError: field name 'genres' that is not defined for input object type 'MovieFilter'`, while the old `genre` filter returns 3 movies. **No code change; restart the stack** (`make dev-stop`, then `make dev`). The frontend shows the generic message because a GraphQL validation error carries no `extensions.code`.
2. **Genre summary line removed** (`GenreChips.svelte`); genres are removed with each chip's ✕ or with "Clear filters". Tests updated (`movies.test.ts`, `e2e/mocked/filters.e2e.ts`).
3. **"All services are running." removed everywhere** (`StatusFooter.svelte`): nothing is shown while checking or when all services are up; the down and unreachable warnings remain (D23, S-004.AC1 amended). `StatusFooter.svelte.test.ts` updated.

Evidence: `8c-fixes-gates-20261022-1419.log`: check 0 errors, lint pass, build pass, mocked E2E 66 passed; Vitest still blocked (269 of 269, environment; 18 lockfile entries still refused).

## Follow-up, 2026-10-22 (restart did not happen)
The owner's `make dev-stop` failed with `scripts/dev.sh: Permission denied`: all ten shell scripts under `scripts/` had lost their execute bit (only `gradlew` kept it), so the earlier stop and start had not run either and the 11:19 stack was still serving. Restored with `chmod +x` on `scripts/*.sh`, `scripts/tests/test_gates.sh`, `scripts/hooks/guard-bash.sh` and `scripts/hooks/pre-commit` (all have a `#!/usr/bin/env bash` line). The multi-genre filter against a restarted stack is **not yet confirmed**; still open: whether `dev.sh --stop`, which kills only the saved Gradle-client and pnpm PIDs, also ends the bootRun JVMs and Vite.

## Confirmed on the running stack, 2026-10-22 14:31
After the permission fix the stack was restarted (`data/dev.pids` rewritten 14:30; gateway started 14:30:25, people-service 14:30:36, movie-service 14:30:37, Vite 14:30:19). Against `http://localhost:8080/graphql`:
- `filter: { genres: [COMEDY, CRIME] }` → 6 movies, each with Comedy or Crime; "The Golden Horizon" (Comedy and Crime) appears once; `totalElements` 6.
- `filter: { genres: [WAR, WAR] }` → `VALIDATION_FAILED` with `fieldErrors: [{ field: "genres", messageKey: "movie.genres.invalid" }]`.
- No filter → the full list, as before.
The owner confirmed the multi-genre filter works in the browser. Still unverified: whether `dev.sh --stop` ends the bootRun JVMs and the Vite child (the old processes were gone by the time it could be observed, so this could not be told apart from another stop run in a different shell).

## Bug found 2026-10-22 14:40: grouped search refused by the gateway (pre-existing)
Owner saw "Something went wrong on our side" on `/search?q=test`. Reproduced in the browser on `http://localhost:5173`: the `Search` request returned `maximum query complexity exceeded 4410 > 1500` (classification `ExecutionAborted`, no `extensions.code`, so the UI shows the generic message). The footer's "Cannot reach the server" in the owner's tab is from the restart window; health answers UP now.

Cause: `GraphQlLimits.PAGE_AWARE` (S-604, D18) multiplied `search` by its `movies`/`people` page size (10) and then each group again by the default page size (20), so the app's own search cost (1 + 220 + 220) × 10 = 4410. Not caused by S-701 or S-702; the mocked E2E suite never reaches the real gateway, so it could not catch it.

Fix (`api-gateway/.../GraphQlLimits.kt`): `search` counts once; each of its groups is multiplied by its own requested size (or 20 when not given). The app's search now costs 221; seven aliased copies (1547) are still refused; other fields are unchanged. New test in `CatalogGraphQlTest`: "the app's own grouped search is within the complexity limit, and wide copies of it are still refused", using the frontend's exact selection.

**Not yet compiled or tested** (D15). Owner command:
```
./gradlew :api-gateway:test --console=plain 2>&1 | tee reports/S-702/search-complexity-gateway-test-$(date +%Y%m%d-%H%M).log
```
then restart the stack (`make dev-stop`, `make dev`) so the running gateway picks it up.

Owner ran `./gradlew :api-gateway:test` at 14:34: **BUILD SUCCESSFUL**, api-gateway 55 tests, 0 failed; the new test and the existing "aliased copies are refused" test both pass (results 14:34:55). Log: `search-complexity-gateway-test-20261022-1434.log`.

## Search fix confirmed, and three more defects fixed, 2026-10-22 14:35 to 14:41
- **Search:** after the gateway restart, `/search?q=the` shows 30 movies and 40 people and `/search?q=test` shows "No results" (checked in the browser on `http://localhost:5173`).
- **`scripts/dev.sh --stop` left the Vite server running** (pre-existing, S-004): it killed only the saved `pnpm` subshell, so each restart left another Vite on 5173 and the new one moved to 5174. Fixed: `set -m` gives each started part its own process group, `--stop` kills the group (falling back to the plain pid for older pid files), and Vite runs with `--strictPort` so it fails rather than moving to another port. Written without `|| true`, which the project guard blocks. Verified in the owner's Terminal: `make dev && make dev-check && make dev-stop`, then `lsof` on 8080, 9091, 9092, 5173 and 5174 found nothing listening, then `make dev && make dev-check` gave "all four are up".
- **`make dev-check` never saw the frontend** (pre-existing, S-004): it polled `127.0.0.1:5173`, but Vite listens on `localhost`, which is `::1` on macOS, so the check always timed out after 120 s. Fixed by polling `http://localhost:5173/`.
- **Footer said "Cannot reach the server" while everything was up**: introduced by my D23 change (the last branch was a bare `{:else}`). Fixed with `{:else if view.kind === 'unreachable'}`. New `e2e/mocked/footer.e2e.ts`: silent when healthy, warns when `/graphql` is unreachable. The unit test that covers this cannot run until the lockfile installs.
Evidence: `footer-fix-gates-20261022-1440.log` (check 0 errors, lint pass, build pass, mocked E2E 68 passed); browser check of the footer text on the running stack.
