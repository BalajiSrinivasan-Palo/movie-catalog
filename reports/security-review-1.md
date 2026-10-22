# Security review 1

Reviewer: security-reviewer agent (read-only, static). Date: 2026-10-22.
Scope: NFR-SEC-01..10 and architecture section 7. Gradle could not run, so nothing below was executed on the JVM; every Java/Kotlin finding is from reading the source. Status words: CONFIRMED means the code shows the defect directly. PLAUSIBLE means it depends on framework behaviour that was not verified at runtime.

## Release gate S-604.AC4

**PASS: no open critical or high finding.** There are 0 critical, 0 high, 4 medium, 9 low and 3 informational items. The gate applies to the static review only. The backend dependency audit (NFR-SEC-09) is not done, so the gate is conditional on that (see M4).

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 4 |
| Low | 9 |
| Info | 3 |

Tool results:
- `python3 -I scripts/secret_scan.py`: `OK: no secrets found`, exit 0.
- `pnpm audit --prod` and `pnpm audit` in `/home/claude/fe-work/frontend`: both "No known vulnerabilities found" (network was available). Output saved in `reports/pnpm-audit.txt`. `pnpm-lock.yaml` and `package.json` there are byte-identical to `frontend/` in the project.
- Gradle dependency audit: NOT RUN (see M4).

## Medium

### M1. No size limit on a normal JSON GraphQL request body
- Where: `api-gateway/src/main/resources/application.yml:13` (only `spring.servlet.multipart.*` limits exist); no limit anywhere for `application/json`. Architecture section 7 promises "request body size limit" and "max request size" for GraphQL abuse.
- Exploit: `POST /graphql` with `Content-Type: application/json` and a body of hundreds of MB (for example a huge `synopsis` variable or a huge query string). Spring reads and parses the whole body into memory before any validation. The 1 MiB gRPC limit only applies later and only to what reaches movie-service. The gateway can be driven to OOM by one unauthenticated request.
- Fix: cap the body (a servlet filter or `ContentCachingRequestWrapper`-style check that returns 413 over, say, 256 KiB for JSON; Tomcat `server.tomcat.max-swallow-size` does not do this), and reject long strings (title 200, synopsis 2000, bio 2000) at the gateway before building the gRPC message. Add a test.
- Status: CONFIRMED (limit absent). Memory-exhaustion impact PLAUSIBLE.

### M2. GraphQL complexity limit does not account for page size or per-row fan-out
- Where: `GraphQlLimits.kt:20` (`MAX_COMPLEXITY = 300`, default per-field cost of 1), with `CatalogControllers.kt:143` (`loadCredits`, 2 gRPC calls for every movie whose `credits` is selected) and `CatalogControllers.kt:76` (`filmography`, one gRPC call per person).
- Exploit: one request of about 40 aliases of `movies(page:{size:100}){ items { title credits { id person { name } } } }` is well under 300 fields but asks for 4,000 movies. That is about 8,000 sequential gRPC calls (each with a 3 s deadline) and thousands of rows of work in the services. Aliases are counted per field, so the limit holds the alias count down, but the cost per alias is about 100 times higher than the number says. Depth 10 does not help. Combined with no rate limiting (disclosed in README), a few such requests tie up the gateway threads and both services.
- Fix: use a cost function that multiplies by `page.size` (graphql-java `MaxQueryComplexityInstrumentation` accepts a `FieldComplexityCalculator`), or cap the number of root fields per operation to 3, and cap the sum of requested page sizes per request (for example 100). Batch credits with a DataLoader instead of one call per movie.
- Status: CONFIRMED by reading (cost model). Runtime impact PLAUSIBLE.

### M3. Concurrency and volume of uploads are unbounded
- Where: `application.yml:13-14` (6 MB per file, 7 MB per request, no limit on concurrent requests); `ArtworkIngest.kt:35` (a temp file per upload); `ArtworkEndpoints.kt:57` (a gRPC stream per upload); no quota on stored artwork.
- Exploit: unauthenticated clients send many 5 MiB uploads in parallel (Tomcat spools each to disk first, then the gateway streams it again, then movie-service decodes it). Each decode of a 6000 x 6000 image is CPU heavy; each upload to a different movie keeps a 5 MiB original plus a thumbnail on disk. With `POST createMovie` open, disk can be filled with movies each holding 5 MiB. Memory per decode is bounded by the subsampling (`ThumbnailMaker.kt:39`), but there is no cap on parallel decodes.
- Fix: a semaphore around ingest/decode in movie-service (for example 2 at a time, return `UNAVAILABLE` when full), a total artwork directory quota check, and gateway-level request concurrency limit. Rate limiting is a disclosed limitation, so this is Medium rather than High.
- Status: PLAUSIBLE.

### M4. Backend dependencies are unaudited, and no audit is part of the gate
- Where: `gradle/libs.versions.toml`, `scripts/audit.sh` (prints "backend audit: NOT RUN"), `.github/workflows/ci.yml` and `scripts/verify.sh` (no audit step; only the secret scan at `verify.sh:9`).
- Impact: NFR-SEC-09 says dependencies are scanned before release. Only the frontend half is satisfied. The versions below were also chosen by hand and are marked unverified in the file itself.
- Fix: add the OWASP dependency-check Gradle plugin (or `osv-scanner` against the resolved dependency lock) to the release checklist and save output to `reports/audit/backend.txt`; run `pnpm audit --prod` from `scripts/audit.sh` in CI.
- Status: CONFIRMED (process gap). Vulnerability content UNKNOWN.

Backend versions from `gradle/libs.versions.toml` (UNAUDITED):

| Component | Version |
|---|---|
| Kotlin | 2.3.21 |
| Spring Boot (BOM; brings Spring Framework, Spring GraphQL, graphql-java, Tomcat, Hibernate, H2, Liquibase, Jackson) | 4.1.1 |
| io.spring.dependency-management plugin | 1.1.7 |
| protobuf Gradle plugin | 0.9.5 |
| grpc-java (stub, protobuf, netty-shaded, services, inprocess, api, protoc-gen) | 1.76.0 |
| protobuf-java / protoc | 4.33.0 |
| TwelveMonkeys imageio-webp | 3.12.0 |
| org.apache.tomcat:annotations-api | 6.0.53 |
| H2 (`runtimeOnly`, no explicit version) | managed by Spring Boot BOM |

The WebP decoder (TwelveMonkeys) and the JDK ImageIO JPEG/PNG readers parse untrusted files, so their advisories matter most.

## Low

### L1. Introspection is on by default (fail-open profile)
- Where: `api-gateway/src/main/resources/application.yml:4-5` (`spring.profiles.default: dev`) with `application-dev.yml` enabling introspection.
- Exploit: a deployment that starts without `SPRING_PROFILES_ACTIVE` gets the dev profile and a public schema. NFR-SEC-05 wants introspection in dev only.
- Fix: make the default profile empty or `prod`, and have `dev.sh` set `SPRING_PROFILES_ACTIVE=dev`. The test at `GatewayHttpTest.kt:95` already covers `prod`.
- Status: CONFIRMED.

### L2. Page offset overflows an Int
- Where: `service-support/.../Common.kt:37` (`val offset: Int get() = index * size`), used by `setFirstResult` in `Repositories.kt:98` and `PersonRepository.kt:47`.
- Exploit: `page: 21474837, size: 100` multiplies to a negative Int. Hibernate rejects a negative first result, the exception is not a `StatusRuntimeException`, and `reply` logs it with a stack trace and returns `INTERNAL`. Other values wrap to a harmless positive offset. An attacker can fill the error log and trigger `INTERNAL` responses at will; no data is exposed.
- Fix: compute as `Long`, reject `index * size > Int.MAX_VALUE` (or cap `index`) with `paging.invalid`.
- Status: CONFIRMED (overflow); exact Hibernate failure PLAUSIBLE.

### L3. No Content-Security-Policy, no Cache-Control on API responses
- Where: `Web.kt:44-52` (`SecurityHeadersFilter` sets `nosniff`, `X-Frame-Options`, `Referrer-Policy` only). The frontend host is not specified anywhere, and `frontend/src/app.html` has no CSP meta.
- Impact: defence in depth is missing. For `/artwork/**` a `Content-Security-Policy: default-src 'none'; sandbox` would neutralise any image that a browser mis-renders. GraphQL responses (which may later hold user data) get no `Cache-Control: no-store`.
- Fix: add those headers in the filter (path-specific for `/artwork`), and a CSP (`default-src 'self'; img-src 'self'; connect-src 'self' <gateway>`) for the static frontend host.
- Status: CONFIRMED.

### L4. Original image is stored and served byte for byte
- Where: `ArtworkIngest.kt:42-64`, `ImageInspector.kt:79` (`Kind.PNG -> true`; trailing data is not checked for PNG and WebP; JPEG allows only zero padding after the end marker).
- Impact: EXIF/GPS and arbitrary appended bytes (a ZIP or script after `IEND`) are kept and served. The type is fixed by the server (`image/jpeg|png|webp`) with `nosniff`, SVG and GIF are unreachable, and the store is outside any static root, so this is not exploitable as XSS. It is a privacy and polyglot-hygiene issue.
- Fix: re-encode the original (strip metadata), or at least truncate after `IEND`/RIFF length and strip EXIF.
- Status: CONFIRMED.

### L5. Immutable year-long public caching regardless of the version parameter
- Where: `ArtworkEndpoints.kt:139-141`. The server ignores `?v=`; every successful response is `public, max-age=31536000, immutable`.
- Impact: a URL without or with a stale `v` (a bookmarked or third-party link) stays cached for a year after the artwork is replaced or removed (privacy: removed images remain in shared caches).
- Fix: send `immutable` only when `v` equals the current version, otherwise `Cache-Control: no-cache` (ETag revalidation still works). Consider `private` if this is ever not demo data.
- Status: CONFIRMED.

### L6. gRPC server hardening gaps
- Where: `service-support/.../GrpcServerLifecycle.kt:32-35`. Only the address (127.0.0.1) and `maxInboundMessageSize` (1 MiB) are set.
- Impact: no `maxConcurrentCallsPerConnection`, keep-alive/idle/connection-age limits, and no server-side idle timeout on the upload observer (`ArtworkGrpc.kt`). A client that stalls mid-upload holds a `.part` file until the channel dies; `ArtworkStorage.kt:30` sweeps temp files only at startup. Only the local gateway can connect, so exposure is low. Deadlines are present on every gateway call (`Downstream.kt:19-31`, including the stream and health calls), and message limits are set on both sides.
- Fix: set the missing NettyServerBuilder limits; sweep `.part` files older than N minutes on a schedule.
- Status: PLAUSIBLE.

### L7. Service HTTP (actuator) ports bind to all interfaces
- Where: `movie-service/src/main/resources/application.yml:15-16`, `people-service/src/main/resources/application.yml:15-16` (no `server.address`). The comments in those files say the gRPC ports are bound to 127.0.0.1, and the code does that; the HTTP ports are not.
- Impact: health-only endpoints, `show-details` is the default `never`, so the leak is minimal, but it breaks "gateway is the only public entry" (architecture section 7).
- Fix: `server.address: 127.0.0.1`.
- Status: CONFIRMED.

### L8. CORS origin is not validated
- Where: `Web.kt:58-61`; `api-gateway/src/main/resources/application.yml:25` (`FRONTEND_ORIGIN`).
- Impact: setting the env var to `*` or a wildcard pattern would silently open CORS (no credentials are used, so the effect is limited to cross-origin reads and uploads from any site, which are already possible for any non-browser client given no auth). The configured value is otherwise correct: one origin, methods `POST,OPTIONS` on `/graphql` and `GET,OPTIONS` on `/artwork/**`, headers `Content-Type`, `X-Correlation-Id`, `Apollo-Require-Preflight`, `maxAge` 600, `exposedHeaders` correlation id.
- Fix: fail startup if the value is `*`, empty, or not a plain origin.
- Status: PLAUSIBLE.

### L9. Missing test for the multipart CORS preflight
- Where: `GatewayHttpTest.kt:34-37` sends a preflight with `Access-Control-Request-Headers: content-type` only. No test sends a preflight that requests `Apollo-Require-Preflight` for a multipart POST, and the multipart route is a separate RouterFunction (`Upload.kt:43-46`). If Spring's CORS handling does not cover it, uploads from the browser would fail rather than be insecure.
- Fix: add that test (and a negative one for an evil origin with that header).
- Status: PLAUSIBLE.

## Info (no action required, recorded for the gate)

- I1. CSRF on upload (`Upload.kt:45`): the `Apollo-Require-Preflight` header is required on multipart POST and answered `403` otherwise (test at `ArtworkGatewayTest.kt:158`). A cross-site form cannot set a custom header, and a cross-origin `fetch`/XHR with it triggers a preflight that only the configured origin passes. The JSON route needs `application/json`, which also forces a preflight. The check tests only presence, which is the intended Apollo behaviour. There is no authentication or cookies, so classic CSRF has no session to abuse (disclosed).
- I2. Log injection: no user-controlled text is logged (searched every `log.` call; they log fixed strings, field names and exception class names). `X-Correlation-Id` is accepted only if it matches `^[A-Za-z0-9-]{8,64}$` (`Correlation.kt:20,23`), so header and log injection through it is closed. gRPC status messages are stable keys (`GrpcErrors.kt`), and unexpected failures return a bare `INTERNAL`.
- I3. NFR-SEC-10: README "Known limitations" states no login/roles and no rate limiting. Satisfied.

## Checks that passed (evidence)

1. Artwork upload
   - Magic bytes: `ImageInspector.kt:43-55` sniffs JPEG, PNG and WebP from the first 12 bytes only; the client file name and content type are never read (the test sends `ignored.png`). SVG and GIF fall to `artwork.type.unsupported`.
   - Size while streaming: `ArtworkIngest.kt:42-49` adds each chunk and aborts past 5 MiB; the gateway also stops at 6 MB (`application.yml:13`) and honours gRPC flow control (`ArtworkEndpoints.kt:69-75`, 93-99). `declaredSizeBytes` is sent but never trusted.
   - Dimensions: read from the header before any decode (`ImageInspector.kt:23-33`), 6000 maximum per side. The thumbnail decode subsamples (`ThumbnailMaker.kt:39`) and reads with `ignoreMetadata`, so a pixel-flood PNG/JPEG within 5 MiB cannot force a full-size decode. Worst case memory per decode is about 30 MB (width just under 640 x 2 and height 6000). The only gap is concurrency (M3).
   - Path traversal and names: file names are `{uuid[0..2]}/{uuid}-original.{ext}` where the extension comes from a server enum (`ArtworkStorage.kt:70-71`); `resolve` normalises and requires `startsWith(root)` (`ArtworkStorage.kt:59-62`), tested with `../` names (`ArtworkStorageTest.kt:41-42`). The storage directory `./data/artwork` is outside any static root (`movie-service/src/main/resources/application.yml:34`).
   - Temp cleanup: `.part` files are removed on limit overflow, on any `finish()` failure, on cancel/`onError` (`ArtworkGrpc.kt:73-76`), and on startup sweep; committed files are deleted when the DB switch fails (`ArtworkService.kt:84-91`). Gateway temp files are removed by Tomcat when the request ends (PLAUSIBLE, framework behaviour).
   - Download: `Content-Type` comes from the stored enum value (`ArtworkEndpoints.kt:145`), `nosniff` from `SecurityHeadersFilter` (tested at `ArtworkGatewayTest.kt:216`), `Content-Disposition: inline`. The `movieId` path variable is parsed as a UUID and the variant is allow-listed (`ArtworkEndpoints.kt:121-126`). ETag is `"{version}-{variant}"` and a matching `If-None-Match` answers 304 before any byte is read.
2. Injection
   - SQL: all queries are JPA Criteria or JPQL with named parameters (`Repositories.kt`, `PersonRepository.kt`); `like` uses a bound pattern and an explicit escape char `'\\'`; `escapeLike` escapes `\`, `%`, `_` and runs after lowercasing (`Common.kt:26`). No string-built SQL or native query anywhere in `src/main`. Liquibase owns the schema; `ddl-auto: none`.
   - GraphQL: the frontend always sends variables (`catalog.ts`; `${...}` interpolation is limited to constant field lists). Enums and `Upload` are typed; the `Upload` scalar can only resolve tokens placed in the context by the multipart route (`Scalars.kt:102-107`), so a plain JSON request cannot reference a file. A multipart `operations` array (batching) fails to parse and is rejected as `artwork.protocol`; the JSON route does not accept batched arrays in this Spring GraphQL setup (PLAUSIBLE, not tested).
3. Response headers and errors: stack traces and internals are not returned (`Errors.kt` maps everything to five fixed messages, detail goes to the log only; `GrpcErrors.kt` status text is a key). Every failure carries `correlationId`. The correlation id is echoed in the `X-Correlation-Id` response header after validation.
4. GraphQL limits: depth 10 and complexity 300 beans (`GraphQlLimits.kt`), tested for depth; page size 1..100 enforced in both services (`Common.kt:44-49`); credits capped at 200; introspection off outside dev (tested). The standard full introspection query (depth about 11) is rejected by the depth limit even in dev; that is a usability note for GraphiQL, not a security problem.
5. gRPC: plaintext on `127.0.0.1` by design and recorded (NFR-SEC-08, `Downstream.kt:57`); 1 MiB message limit on both ends; 64 KiB chunks; deadlines 3 s default, 30 s upload/download, 2 s health.
6. Frontend: no `{@html}` anywhere in `src` (also asserted by `design-rules.test.ts:65`, and `svelte.configs.recommended` enables `svelte/no-at-html-tags`). The only query-string value reaching the DOM is `q` in `SearchView.svelte` (text interpolation and `<title>`, both escaped by Svelte). Navigation uses `resolve()` with fixed route templates and `encodeURIComponent`; there is no redirect to a user-supplied URL. Image `src` values are server-built relative paths (`Views.kt:124,134`). The fetch client sends `credentials: 'omit'` (`client.ts`). No `localStorage`, `eval`, `postMessage` or `window.open` use.
7. Secrets: scan clean; the services need no secrets (H2 `sa` with empty password is a local file DB with `AUTO_SERVER=FALSE`, no H2 console enabled).

## Recommended order of fixes
1. M1 (body size and string length at the gateway), M2 (cost model and DataLoader), L1 (default profile), L2 (offset overflow).
2. M4 (backend audit) before release, then M3, L3, L5, L7.
3. L4, L6, L8, L9 as hardening.
