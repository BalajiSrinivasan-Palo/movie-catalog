# Security review 2

Reviewer: security-reviewer agent (read-only, static). Date: 2026-10-22.
Scope: changes made on 2026-10-22 for S-701 (drag-and-drop upload) and S-702 (search scope, multi-genre filter, the search complexity fix, the people-service date binding, seed posters), checked against NFR-SEC-01..10 and architecture section 7. Format and status words are the same as `reports/security-review-1.md`. CONFIRMED means the code shows the defect directly. PLAUSIBLE means it depends on framework or runtime behaviour that was not verified. Gradle was not run (D15), so every Kotlin finding comes from reading the source. Nothing was edited, and the running dev stack was not touched.

## Gate

**PASS: no open critical or high finding.** These changes add 1 medium (N1), 1 low (N3) and 4 informational items. N2 is an older medium that was not introduced on 2026-10-22: it is the part of M2 that is still open, and it is listed here because the new code comment says it is covered when it is not. M2 from review 1 is **partly fixed and still open**: page-size fan-out is now priced, but the `search` change opened a new gap (N1), and per-credit fan-out is still not priced (N2). The gate stays conditional on the backend dependency audit (M4 from review 1, unchanged).

| Severity | Count (this review) |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 2 (N1 new; N2 older, the open part of M2) |
| Low | 1 |
| Info | 4 |

## Tools run and not run
- `python3 -I scripts/secret_scan.py`: `OK: no secrets found`, exit 0.
- Grep of `frontend/src` (test files excluded) for `@html`, `innerHTML`, `eval(`, `localStorage`, `postMessage`: no matches.
- Read in full: `GraphQlLimits.kt`, `SearchController.kt`, `schema.graphqls`, the `MovieController` and `PeopleController` parts of `CatalogControllers.kt`, `Views.kt` inputs, the `JsonBodyLimitFilter` in `Web.kt`, `MovieValidator.validateGenreFilter`, `MovieService.list`, the `Repositories.kt` predicates, `MovieGrpcService.listMovies`, `Domain.kt` `Genre.from`, `people-service/src/main/resources/application.yml`, `ArtworkUploader.svelte`, `drag.ts`, `+layout.svelte`, `search-scope.ts`, `SearchView.svelte`, `routes/search/+page.svelte`, the SearchBox navigation, `scripts/seed/posters.mjs` and how `seed.mjs` calls it.
- NOT RUN: Gradle build and tests (D15); `pnpm audit` (the reports say S-701 and S-702 added no dependencies; I did not check that against the lockfile); the backend dependency audit (M4); every runtime check of the cost numbers below. The cost numbers are worked out by hand from `PAGE_AWARE`, assuming graphql-java's usual rule that a field's cost is computed from the sum of its children's costs.

## Medium

### N1. `search` fetches both groups at their requested size, but only the selected fields are priced (new, from the 14:40 complexity fix)
- Where: `api-gateway/src/main/kotlin/com/moviecatalog/gateway/GraphQlLimits.kt:42` (`if (name == "search") return 1`) and `:45` (a group is multiplied only when it appears in the selection). `SearchController.kt:37-38` always calls both services, with `moviePage` and `peoplePage` as sent, whatever the client selected.
- Exploit: one request (about 30 KB, under the 64 KiB body cap) with 375 aliases of
  `aN: search(query: "ab", movies: {size: 1}, people: {size: 100}) { movies { pageInfo { page } } }`.
  Cost per alias: `page` 1, `pageInfo` 2, `movies` (1+2)x1 = 3, `search` 1+3 = 4. Total 375 x 4 = 1500, exactly the limit, so it is accepted. The real work is 375 movie searches plus 375 people searches of 100 rows each, each with its count query. That is 750 gRPC calls, about 1,500 `LIKE '%..%'` queries and up to 37,500 people rows built, sent and thrown away. The fairly priced equivalent, `searchPeople(query:"ab", page:{size:100}) { pageInfo { page } }`, costs (1+2)x100 = 300, so only 5 copies fit. `search` lets about 75 times more work through for the same budget. No authentication and no rate limiting (disclosed), so a few such requests in parallel tie up the gateway and people-service.
- Why the test misses it: `CatalogGraphQlTest.kt:321-326` checks only the app's own selection (both groups selected) and 7 identical copies of it.
- Fix (any one closes the gap; the first two together are best):
  1. In `search`, call a service only when its group is selected (`DataFetchingFieldSelectionSet.contains("movies")` / `"people"`).
  2. Price `search` by its requested sizes whatever is selected. For example, a custom cost for `search` of `1 + childComplexity + sizeOf(movies) + sizeOf(people)` (20 each by default).
  3. Give every field that calls a downstream service a base cost (for example 10 to 25), or cap root fields or aliases per operation (review 1 suggested 3). This also stops cheap `page:{size:1}` aliasing of `movies`, `people`, `searchMovies` and `searchPeople` (each costs about 3, so 500 aliases fit).

  Add a test that 20 aliases of the query above are refused.
- Status: CONFIRMED (cost model and unconditional fetch, from reading). Load impact PLAUSIBLE.

### N2. Credit fan-out is not priced, though the new comment says it is (older code; the open part of M2)
- Where: `GraphQlLimits.kt:24` (`PAGED_FIELDS` has no `credits`, so `Movie.credits` gets factor 1 even though it can return 200 items, `CatalogControllers.kt:203`). The comment at `GraphQlLimits.kt:27-29` says "each credit also triggers downstream work" and that this is counted. The downstream work is `Person.filmography` (`CatalogControllers.kt:75-87`), which makes one `listCreditsByPerson` gRPC call for each person it resolves, reached through `Credit.person` (schema line 194, `Views.kt:90`).
- Exploit: create one movie with 200 credits (open `createPerson` and `addCredit`, about 400 mutations). Then send 250 aliases of
  `aN: movie(id: "<that id>") { credits { person { filmography(page: {size: 1}) { pageInfo { page } } } } }`.
  Cost per alias: `filmography` 3, `person` 4, `credits` 5, `movie` 6. Total 1500, accepted. Work: 250 x (1 `getMovie` + 1 `listCreditsByMovie` + 1 `batchGetPeople` + 200 `listCreditsByPerson`), about 50,000 sequential gRPC calls (3 s deadline each) from a single 64 KiB request. There is no DataLoader or per-request cache, so the same movie id is loaded again for every alias. A variant without the setup: `movies(page:{size:100}) { items { credits { person { filmography(page:{size:1}) { pageInfo { page } } } } } }` costs 700, so two aliases fit, giving 200 movies x up to 200 credits.
- Fix: multiply `credits` by `CREDIT_LIMIT` (200), or by a realistic cap, in `itemsPerPage`. Batch `filmography` with a DataLoader, or refuse `filmography` below `credits.person` (depth or path rule). Add a base cost for each downstream call as in N1 item 3. Add a test with the query above.
- Status: CONFIRMED (cost model, from reading). Load impact PLAUSIBLE. Not introduced on 2026-10-22, but the D18/S-604 fix for M2 did not cover it.

## Low

### N3. Refusals from the complexity and validation rules leak numbers and break the error contract
- Where: `GraphQlLimits.kt:18` (`MaxQueryComplexityInstrumentation` abort), and `Errors.kt:85` handles only exceptions thrown inside data fetchers. `reports/S-702/8c-report.md:81` records the observed answer: `maximum query complexity exceeded 4410 > 1500`, classification `ExecutionAborted`, no `extensions.code`, no `correlationId`. Line 64 of the same report shows a schema validation error naming a field and input type (`field name 'genres' that is not defined for input object type 'MovieFilter'`) while introspection is off.
- Exploit: an attacker reads the exact limit and the cost the server computed from one refused request, and uses them to tune aliases to sit just under 1500 (as in N1 and N2). Schema validation messages also reveal type and field names without introspection (minor, because the frontend bundle shows the operations anyway). The frontend can only show the generic message (the owner's "Something went wrong" reports), and these failures cannot be traced by correlation id, against `schema.graphqls:5` ("Every failure has extensions.code ... extensions.correlationId").
- Fix: add a `GraphQlSourceBuilderCustomizer` / `WebGraphQlInterceptor` that maps pre-execution errors (`ExecutionAborted`, `ValidationError`) to a fixed message with `extensions.code` (for example `VALIDATION_FAILED` or `QUERY_TOO_COMPLEX`) and the correlation id, and drops the numbers from the message.
- Status: CONFIRMED (observed on the running stack, as recorded in the 8c report).

## Info (no action required)

- I1. Multi-genre filter (NFR-SEC-01, -04). `MovieValidator.validateGenreFilter` (`MovieValidator.kt:51-57`) rejects more than 15 entries, nulls (unknown or `GENRE_UNSPECIFIED`, through `Genre.from` at `Domain.kt:40`, mapped at `MovieGrpcService.kt:117`) and duplicates, in the owning service. The union with the deprecated `genre` (`MovieService.kt:91`) can only reach the 15 real values. The predicate (`Repositories.kt:117`) is a JPA Criteria `or` of at most 15 bound `isMember` checks, with no string-built SQL. The GraphQL list has no length limit at the gateway (`CatalogControllers.kt:112`), but the 64 KiB JSON body cap (`Web.kt:72-108`) bounds it to a few thousand enums (about 10 KB of protobuf, well under 1 MiB), and movie-service rejects anything over 15. A list-size check at the gateway would be a nice extra, not a defect.
- I2. Drag-and-drop upload (NFR-SEC-02). `ArtworkUploader.svelte:58-66` sends a dropped file through the same `accept()`, `validateArtworkFile()` and `uploadArtwork()` path as a chosen file, and uses only `files[0]`. Drops while busy and non-file drags are ignored. The window guard (`+layout.svelte:14-19`) only calls `preventDefault()` for drags whose `types` include `Files` (`drag.ts:3`) and never reads the file, so a missed drop cannot make the browser open a local file in the app's origin. The client check uses the browser-reported `type` for UX only; the server's magic-byte check is still the authority. No new sink and no `{@html}`. M3 (upload concurrency) is not affected: the request path did not change.
- I3. Search scope (NFR-SEC-03). `parseScope` (`search-scope.ts:11-13`) allow-lists `titles` and `people`, and everything else becomes `all`. `searchPath` encodes `q` with `encodeURIComponent` and adds `scope` only from the fixed union. Navigation goes through `goto(resolve(...))` to `/search` only, so there is no open redirect. `q` reaches the DOM only through Svelte text interpolation and `<title>` (`SearchView.svelte:79-84`).
- I4. people-service `hibernate.type.java_time_use_direct_jdbc: true` (`application.yml:16`) changes only how `LocalDate` is bound to the column. It has no security effect. Seed posters (`scripts/seed/posters.mjs:12-17`): file names come from committed constant data (`films.mjs`), not user input. Local copyrighted posters are read from the git-ignored `data/` folder (`.gitignore:1`), and the server ignores the sent `filename`. `new URL(movie.poster, base)` would follow `../` if someone put it in the seed data, but that data is trusted source code.

## Prior findings (security-review-1) affected
| Finding | Status after these changes |
|---|---|
| M1 JSON body size | Appears fixed (outside today's scope; read only, not tested by me): `JsonBodyLimitFilter`, `Web.kt:65-108`, 64 KiB cap with 413, and chunked bodies counted. Possible gap, not verified: only `getInputStream()` is wrapped, not `getReader()`, so a reader-based body path would not be counted. Spring MVC normally uses the input stream. The long-string checks at the gateway that M1 also asked for were not looked at |
| M2 complexity cost model | **Partly fixed, still open (Medium).** Page-size fan-out is priced (D18). The 2026-10-22 `search` change fixed a false refusal of the app's own search but leaves unselected groups unpriced (N1). Per-credit fan-out and cheap `size:1` aliasing are still unpriced (N2, and N1 item 3). The DataLoader recommendation is not done |
| M3 upload concurrency | Unchanged (S-701 does not change the request path) |
| M4 backend audit | Unchanged, still NOT RUN; the gate stays conditional on it |
| L1 default `dev` profile | Unchanged (`api-gateway/src/main/resources/application.yml:4-5` still `default: dev`) |
| L7 service HTTP ports on all interfaces | Unchanged for people-service (`people-service/src/main/resources/application.yml:20-21`, no `server.address`); this file was edited today but the setting was not added |
| Others (L2-L6, L8, L9, I1-I3) | Not touched by these changes; not re-checked |

## Recommended order
1. N1 and N2 together: base cost per downstream call or a root-field/alias cap, a fixed price for `search`, a multiplier on `credits`, and selection-aware `search`, with tests for both exploit queries.
2. N3 (stable error code for refused queries).
3. Carry-over from review 1: M4 before release, then L1 and L7.
