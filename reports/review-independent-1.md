# Independent review 1 (static)

Reviewer: independent reviewer (did not write this code). Date: 2026-10-22. Read-only: no project file was edited.
Method: read every file in the uncompiled set (movie-service `artwork/`, `MovieService`, `MovieQueries.search`, `MovieGrpcService`, people-service search, gateway `Upload.kt`, `ArtworkEndpoints.kt`, `SearchController.kt`, `Scalars.kt`, `Downstream.kt`, `Errors.kt`, schema, proto, yml, Liquibase) and every new test, then compared each test call to the real signature and each code path to BRD, ADR-005/006/007/009, the stories and the proto/schema.
Nothing was compiled or run. CONFIRMED = certain from the code. PLAUSIBLE = depends on library behaviour I cannot check here.

## Summary

| Severity | Count |
|---|---|
| Blocker | 1 |
| Major | 5 |
| Minor | 18 |

Compile check result: I found **no definite compile error** in the new code, and **no test-vs-main signature mismatch** (every constructor, `PageSpec`, `MovieFields`, `Deadlines`, `Downstream`, `PreparedArtwork`, `ImageInfo`, `ArtworkLimits`, `GrpcErrors` call in the tests matches). The risks that remain are framework contracts I cannot verify (M4) and the one runtime blocker below. Nothing here should be called passing until the owner's first `./gradlew build` output is saved in `reports/`.

## Blocker

### B1. The multipart `operations` JSON is parsed with `BasicJsonParser`, which mangles escape sequences, so the real frontend upload mutation will not parse (PLAUSIBLE, high confidence)
- Where: `api-gateway/.../Upload.kt:4, 68-73` (parse), `:88` (re-serialise), `:131-170` (hand-written `Json` writer). Trigger: `frontend/src/lib/api/upload.ts:8-10, 36-39`.
- What goes wrong: Spring Boot's `BasicJsonParser` is a minimal splitter. From memory of its source, its tokenizer treats a backslash only as "append the next character literally", it does not decode `\n`, `\t` or `\uXXXX`, it returns `true`/`false`/`null` as plain strings, and it counts `{ } [ ]` inside string values. The frontend sends `JSON.stringify({ query: UPLOAD_MUTATION, ... })` where the query is a multi-line template literal with a newline and a tab. After this parser the text becomes `...{ntuploadArtwork(...)...}n}`, which GraphQL rejects (syntax error or unknown field). Every real upload would fail with a GraphQL error, and a query containing `\"` or an unbalanced brace in a string would be split wrongly.
- Why no test catches it: `ArtworkGatewayTest.kt:109, 124-125` uses a single-line query with no escapes and builds it with the product's own `Json.write`, so it is self-confirming. The 207 frontend tests mock XHR (`upload.test.ts`).
- Fix: parse with a real JSON parser (Boot 4: `tools.jackson.databind.json.JsonMapper`, or `JsonParserFactory.getJsonParser()` which picks Jackson) and write with the same mapper; delete the custom `Json` object. Add a gateway test that posts exactly `UPLOAD_MUTATION` from `upload.ts` (newline, tab, a `"` in a string argument, a non-ASCII character, a `null` variable). If the owner confirms `BasicJsonParser` does decode escapes, downgrade, but the test is still needed.

## Major

### M1. Per-movie lock can be evicted while a waiter is about to take it, and a unique-key clash is reported as `movie.not_found` (CONFIRMED)
- Where: `movie-service/.../artwork/ArtworkService.kt:44-52` (`locked`), `:84-87` (catch).
- Scenario (lock): thread A holds lock L for movie M. Thread B calls `computeIfAbsent`, gets L, and has not yet called `lock()`. A unlocks; the `finally` sees `!isLocked && !hasQueuedThreads()` and runs `locks.remove(M, L)`. B then locks L (free). Thread C arrives, finds no entry, creates L2, and runs at the same time as B. EC-09 / S-301.AC4 ("exactly one artwork") is no longer guaranteed. Effects: both read the same `existing` row; the second commit leaves the first commit's new files on disk (orphans), or both see no row and the second insert hits `uq_artwork_movie`.
- Scenario (mapping): any `DataIntegrityViolationException` is turned into `NOT_FOUND movie.not_found`. The unique-key clash above (or any other constraint) then tells the user the movie does not exist when it does.
- Fix: keep the entry alive with a reference count (`compute` to increment, `compute` to decrement and remove at zero), or use a fixed array of striped locks (`locks[abs(id.hashCode()) % 64]`). In the catch, re-check `movies.existsById(movieId)`: not found -> `movie.not_found`; otherwise retry once or rethrow as `INTERNAL`. Add a test that runs 20 uploads for one movie and asserts one row and exactly two files.

### M2. Deleting a movie can orphan artwork files, and the story's startup orphan log does not exist (CONFIRMED)
- Where: `MovieService.kt:60-66`, `ArtworkService.kt:126-136`; story S-406.AC2.
- Scenario: `delete` reads the artwork row inside its own transaction. A concurrent `replace` that has not committed yet is invisible, so no file-deletion is registered. If `replace` then commits and `delete` commits, the DB `ON DELETE CASCADE` removes the row and the new files stay on disk with no row pointing to them. (If delete commits first, the FK rejects the insert and the catch cleans up, which is fine.) Same for `replace` committing between delete's read and commit.
- S-406.AC2 says "Startup logs orphan files (log only)". `grep -ri orphan` finds nothing in `movie-service`; `ArtworkStorage.sweepTemp` only handles `tmp/*.part`.
- Fix: take the same per-movie lock in `MovieService.delete` (call into `ArtworkService` to run the delete under `locked(id)`), and add a startup pass that lists files under `ARTWORK_DIR` not referenced by any `artwork` row and logs a count (log only, as the story says).

### M3. Acceptance criteria of S-303 and S-301 are not met or not evidenced (CONFIRMED)
- S-303.AC2 ("client abort cancels the gRPC call, verified by a test observing cancellation in movie-service"): the gateway reads the whole multipart body to a container temp file before any GraphQL runs (`Upload.kt:48-53`), so an abort during the browser upload never reaches movie-service. That is safe for BR-11, but the AC describes a different design and no test exists (`ArtworkGatewayTest` has none).
- S-303.AC5 ("multipart resolver disabled for /graphql ... `-Xmx128m` test proves it is not buffered") and ADR-006: the implementation relies on Tomcat spooling (`application.yml`: `resolve-lazily: true`, `max-file-size 6MB`, `max-request-size 7MB`); there is no memory-bounded gateway test. Either amend the AC/ADR to "spooled to disk by the container, then streamed" with an approval note, or add the test.
- S-303.AC4 (oversize returns the standard error shape): `Upload.kt:50-51` maps every `IllegalStateException` from `multipartData()` to `artwork.too_large`, but no test sends more than 7 MB. PLAUSIBLE: when the container rejects, the rest of the body is unread, so a browser still sending sees a connection reset (XHR `onerror`, "Network request failed") rather than the JSON error.
- S-301.AC3 (deadline leaves no file) and S-301.AC7 ("movie deleted mid-upload leaves no files") have no test. Over-limit upload at the gRPC level (`artwork.too_large` through `ArtworkUploadObserver`) is only tested at the `ArtworkIngest` level.
- Fix: add the missing tests or amend the stories with owner approval (decisions log + ADR-006 note).

### M4. Framework contracts the multipart path depends on are unverified (PLAUSIBLE)
These cannot be checked without the jars; verify each on the first compile/run, in this order:
1. `GraphQlHttpHandler` is injectable as a bean in Boot 4.1 / Spring GraphQL 2.x (`Upload.kt:40`). If not, `StartupTest` fails for the whole gateway context.
2. `ServerRequest.from(request).headers{}.attribute().body(ByteArray).build()` then `handler.handleRequest(forwarded)` (`Upload.kt:60, 89-96`): needs `Builder.body(byte[])` and that the built request exposes the new content type and attributes.
3. The handler copies `ServerRequest.attributes()` into `WebGraphQlRequest.getAttributes()` (`Upload.kt:122`). If it does not, `UploadContextInterceptor` sees nothing and every upload fails with "Upload must be sent as a multipart request".
4. `ExecutionInput.Builder.graphQLContext(Map)` merges into the context that variable coercion later sees (`Upload.kt:124`, `Scalars.kt:46`). If it replaces the context, nothing else is lost today, but check that `Upload.parseValue` receives the same `GraphQLContext`.
5. Route order: the Boot GraphQL `RouterFunction` is `@Order(0)`; `MultipartGraphQlRoute.multipartGraphQl` has no `@Order`. If Boot's route (or a catch-all 415 for unsupported content types) matches `multipart/form-data` first, the custom route is never reached. Add `@Order(Ordered.HIGHEST_PRECEDENCE)` or `-1` to the bean to be safe.
6. `GraphQlArgumentBinder` accepts an already-built `UploadedFile` object for `@Argument file` (`ArtworkEndpoints.kt:208`).
7. graphql-java 25 `Coercing` overrides in `Scalars.kt:43-50, 57-70` (the old one-argument methods are deprecated defaults; confirm they are still defaults).
8. Kotlin 2.3 treats JSpecify `@Nullable` strictly: `TransactionTemplate.execute` returns `T?` (handled with `!!` in `ArtworkService.kt:81, 101`); check no other Spring 7 call returns a nullable that the new code dereferences (`WebGraphQlRequest.attributes`, `ServerRequest.headers().firstHeader` are used safely).
- Fix: none to write now; run the owner build and paste the output. The `StartupTest` of the gateway plus `ArtworkGatewayTest` exercise items 1-6.

### M5. Process and gate gaps (CONFIRMED)
- Rule 1 ("No plan, no implementation"): `docs/plans/` has S-001 to S-004 only, yet S-101 to S-5xx code and tests exist. D16 only changes review timing, not plans.
- DoD item 2: `ktlint`, `detekt`, `kover` are not applied in any `build.gradle.kts` (`verify.sh` line 13 says so). The CLAUDE.md command `./gradlew ktlintCheck detekt koverVerify` cannot run. When ktlint is added, existing import-order and blank-line issues will fail it (m17).
- DoD item 1/10: no `reports/` evidence for any story after S-003, which is consistent with D16, so every backend story must stay "Implemented, unreviewed".

## Minor

Logic and robustness
- m1. `ArtworkController` sends `Cache-Control: public, max-age=31536000, immutable` for any `v` (`ArtworkEndpoints.kt:310`), including a missing or old `v` (S-303.AC6 says the token only busts caches). A direct visit to `/artwork/{id}/original` is then cached for a year and never shows a replacement. Fix: send `immutable` only when `v` equals the current `header.version`, else `no-cache`.
- m2. `streamArtwork` writes all chunks without checking `isReady` (`ArtworkGrpc.kt:92-112`) although its comment says it honours flow control. A slow gateway makes the server queue the whole file (up to 5 MiB) per download. Fix: use `ServerCallStreamObserver.setOnReadyHandler` and send while `isReady`.
- m3. Download race (`ArtworkGrpc.kt:113-117`): replace deletes the old file after commit, so a download that already read the old row returns `NOT_FOUND` for an artwork that exists. Fix: retry once on `NoSuchFileException` by reloading the row.
- m4. `ImageInspector.kt:60-71` requires `FFD9` in the last 64 bytes of a JPEG. A valid JPEG with trailing data (some camera and motion-photo files) is rejected as `artwork.corrupt` (FR-ART-02 "readable image"). Fix: scan back further (for example 64 KiB) or rely on a full decode with warnings turned into errors.
- m5. CMYK/YCCK JPEGs make the JDK reader throw, and are reported as `artwork.corrupt` (`ThumbnailMaker.kt:25-29`). Also EXIF orientation is ignored, so a phone portrait can have a sideways thumbnail while browsers rotate the original. Consider `artwork.type.unsupported` for CMYK and applying orientation.
- m6. PLAUSIBLE memory: TwelveMonkeys WebP decodes the whole bitmap before subsampling, and the JDK reads progressive JPEGs with full coefficient buffers; a 6000x6000 file under 5 MiB can need 150-220 MB. `ThumbnailMaker.decode` catches `RuntimeException` only, so an `OutOfMemoryError` surfaces as `INTERNAL` after `ArtworkIngest.finish` cleanup. The streaming test (`ArtworkStreamingTest`) covers PNG only. Fix: cap concurrent decodes (already noted in security-review M3) and add a WebP/progressive sample to the 128 MB test, or lower the dimension limit for those formats.
- m7. `ArtworkMutations.send` swallows `InterruptedException` into `INTERNAL` without restoring the interrupt flag (`ArtworkEndpoints.kt:254-258`). Fix: `Thread.currentThread().interrupt()` in that branch.
- m8. `ArtworkMutations.send` polls `isReady` every 2 ms (`:263-269`). Fine functionally; `ClientResponseObserver.setOnReadyHandler` would avoid the spin.
- m9. `ArtworkService.replace` overwrites `createdAt` on replace (`:78`). Probably intended as "uploaded at"; the column name suggests creation time. Harmless.

Error mapping and docs
- m10. `GraphQlErrorResolver.resolveToSingleError` returns `null` for `CANCELLED` (`Errors.kt:225`). In Spring GraphQL, `null` means "unresolved", so the default handler still adds an `INTERNAL_ERROR`; the architecture says "not reported". Fix: return an empty list from `resolveToMultipleErrors` for that case. PLAUSIBLE.
- m11. EC-16: `PersonService.kt:56` throws a bare `UNAVAILABLE` with a description and no `ErrorDetail`, which breaks ADR-005 ("every failure carries ErrorDetail") and the gateway drops the description (`Errors.kt:213`), so the UI shows the generic "service not available" instead of "cannot verify credits". Fix: `GrpcErrors.unavailable("person.delete.check_unavailable")`, map the key through to `extensions.messageKey`, add the message to `frontend/src/lib/messages.ts`.
- m12. ADR-005/architecture §6: `ErrorDetail.metadata` (`creditCount`) is set by `PersonService.kt:60` but never exposed (`Errors.kt:207-212` has no field for it; the frontend type comment at `client.ts:27` expects it). Either expose it or stop sending it.
- m13. `BatchGetPeople`: the proto says an empty list fails with `INVALID_ARGUMENT` (`people_service.proto:33-34`); the code returns an empty result (`PeopleGrpcService.kt:225-227`; `PersonServiceTest.kt:157` asserts the empty result at the service level). Align the proto comment or the code.
- m14. Credit ordering: proto comment (`movie_service.proto:50`) and `CreditRepository.findByMovie` (`Repositories.kt:34`, `order by c.role`) disagree. `role` is stored as a string, so the order is ACTOR, DIRECTOR, PRODUCER, WRITER, not the documented ACTOR, DIRECTOR, WRITER, PRODUCER. Filmography order (`Repositories.kt:37-41`: year desc, title, role) also differs from the comment at `movie_service.proto:53`. Fix: order by a `case` expression or sort in code, and update the comments.
- m15. Architecture §7 says downloads carry `Content-Disposition: inline; filename="artwork"`; the code sends `inline` (`ArtworkEndpoints.kt:317`). Align one of them.
- m16. Synopsis search lowercases in the database (`MovieQueries.kt:84`) but the needle in Java; the two can disagree for some non-ASCII letters (H2 `LOWER` uses the default locale). Title and name use the stored `titleKey`/`nameKey`, which is consistent. Fix: store a lower-cased synopsis key or lower-case with `Locale.ROOT` on both sides in one place.

Style, tests and frontend
- m17. ktlint-style issues that will fail once ktlint is wired: import order in `MovieGrpcService.kt`, `ArtworkEndpoints.kt`, `Upload.kt`, `Domain.kt`, `CatalogControllers.kt`, `Views.kt`, `CatalogGraphQlTest.kt`, `MovieGrpcTest.kt`, `HealthTest.kt`; blank line before the closing brace at `MovieService.kt:100`. No unused imports found.
- m18. Frontend (not covered by its tests): the Cancel button stays visible during "Processing" (`ArtworkUploader.svelte:62-65, 96-98`). By then the gateway has the whole file and will still forward it, so the message "Upload cancelled. The existing artwork is unchanged." (`:78`) can be wrong (the page reloads via `onsettled`, so the screen self-corrects, but the text misleads, EC-08). Hide Cancel once `phase === 'processing'`, or change the message. Also: the SPA calls relative `/graphql` and `/artwork/...` and the artwork URLs are relative (`Views.kt:328, 338`); this only works behind the Vite proxy or a reverse proxy, so the `catalog.frontend-origin` CORS setting suggests a cross-origin setup that the client code does not support. State the deployment assumption in the README.

## Checked and found correct (for the record)
- `MovieQueries.search` / `PersonQueries.page`: Criteria types (`like(Expression<String>, String, char)`, `isMember`, `where(Predicate)`), escaping of `%`, `_`, `\`, genre matching in code, trimming and the 2..100 rule are consistent with ADR-009 and EC-12; every search test expectation traces correctly through the code (`found("sci_fi")` empty, `"0% w"`, `"a\\b"`, `"%%"`, accents, paging by title with true total).
- `Artwork` entity vs `003-artwork.yaml`: column names, types, lengths, unique and FK cascade match; `findByMovieId`, `findByMovieIdIn` and `saveAndFlush` (merge for an assigned id) behave as the service expects.
- `TransactionSynchronization.afterCommit` registration inside the `@Transactional` delete, the `TransactionTemplate` usage and the file-after-commit ordering in replace/remove are sound.
- Streaming upload path: size counted per chunk with `>` (exact 5 MiB accepted, +1 byte rejected), temp file then atomic move, every failure path (protocol, too large, invalid content, thumbnail failure, missing movie, cancel) removes files; `finished` guards later messages.
- gRPC <-> GraphQL: every field in `schema.graphqls` has a source in `Views.kt`/controllers; enum names map; `MovieSummary.thumbnailUrl`, `Artwork` fields and `uploadArtwork -> Movie!` match the frontend queries.
- WebP sample in `TestImages` decodes as a valid 1x1 VP8L file (header fields checked by hand); truncated-file expectations (JPEG minus 10 bytes, WebP minus 4) hold with the code.
- Prior security-review items (JSON body size M1, page offset overflow L2, default dev profile L1) are not repeated here.

## Suggested order of work
1. B1 (replace `BasicJsonParser`, add the realistic-mutation test).
2. Run the owner build, paste output to `reports/`, then work through M4 item by item.
3. M1 and M2 (lock, unique-key mapping, delete under lock, orphan log).
4. M3 (tests or approved amendments), then the minors.
