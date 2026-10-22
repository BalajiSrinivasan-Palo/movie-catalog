# Non-Functional Requirements — Movie Catalog

Status: **APPROVED 2026-10-21** · Version 0.2 (perf dataset wording clarified in Phase 5) · 2026-10-21

Numeric targets below are **proposed**; the assessment gives none except where marked "Source". They are measured on a developer laptop against a generated performance dataset of 1,000 movies and 1,000 people (the demo seed is smaller: about 30 movies and 40 people) and will be verified, not assumed.

## 1. Technology and architecture

| ID | Requirement | Source | Verification |
|----|-------------|--------|--------------|
| NFR-TECH-01 | Frontend is Svelte (SvelteKit) written in TypeScript with `strict` enabled; no `any` without a justified comment | A-T1 | `svelte-check` and lint pass with zero errors |
| NFR-TECH-02 | Backend services are Kotlin on Spring Boot (JDK 21, Gradle) | A-T2 | Gradle build; dependency report |
| NFR-ARCH-01 | At least two Spring Boot services; planned: `movie-service`, `people-service`, `api-gateway` | A-T3, D1 | Architecture review; startup of all three |
| NFR-ARCH-02 | Service-to-service calls use gRPC with versioned `.proto` contracts; no service calls another over REST | A-T4 | Contract tests; code search for HTTP clients |
| NFR-ARCH-03 | The browser talks only to the gateway. All data operations, including artwork upload (GraphQL multipart request), use GraphQL; image bytes are delivered by cacheable `GET /artwork/...` endpoints on the gateway (amended by D10 on 2026-10-21; original wording: "only via GraphQL") | A-T5 | Network inspection in E2E: only `/graphql` and `/artwork/*` requests reach the gateway |
| NFR-ARCH-04 | Each service owns its data (H2 file database per service, Liquibase migrations); no cross-service database access | D5 | Review; migrations run on empty DB |
| NFR-ARCH-05 | Artwork bytes are streamed between gateway and `movie-service` (gRPC client streaming) with bounded memory | D5 | Test with a 5 MiB file under a constrained heap |

## 2. Quality of delivery

| ID | Requirement | Source | Verification |
|----|-------------|--------|--------------|
| NFR-TEST-01 | Unit tests cover business logic and edge cases in BRD section 9; every validation rule in BRD section 7 has at least one passing and one failing test | A-T6 | Test report mapped to AC IDs |
| NFR-TEST-02 | Line coverage gate of at least 80% on domain/service packages (Kotlin) and on frontend logic modules (stores, helpers, API clients); generated code excluded | Proposed | Kover and Vitest coverage reports |
| NFR-TEST-03 | Contract tests for each gRPC service and for the GraphQL schema (including error shapes) | D4 | Contract test suite |
| NFR-TEST-04 | A small set of end-to-end journeys (UJ-01, UJ-02, UJ-06, UJ-07) runs against the full stack | D4 | Playwright report |
| NFR-TEST-05 | Test results are reported from actual runs; no result is claimed without command output | Delivery brief | Final validation report |
| NFR-MNT-01 | Formatting and static analysis (ktlint, detekt, ESLint, Prettier) pass with zero errors | Proposed | CI/hook output |
| NFR-MNT-02 | Code is idiomatic and plain: no speculative abstractions; each non-obvious decision has an ADR or code comment explaining why | A-E1, A-E2 | Review checklist |
| NFR-MNT-03 | Dependencies are added only when needed and each is justified in the architecture document | Delivery brief | Dependency list review |

## 3. User experience

| ID | Requirement | Source | Verification |
|----|-------------|--------|--------------|
| NFR-UX-01 | Responsive layout works from 360 px to 1920 px wide without horizontal scrolling; tested at 360, 768 and 1280 px; touch targets at least 44×44 px | A-N1 | Viewport tests |
| NFR-UX-02 | Upload shows determinate progress, cancel, success, failure and retry (FR-ART-03) | A-N2 | Component and E2E tests |
| NFR-UX-03 | List and detail views show a skeleton or spinner within 100 ms of starting a request, and never a blank screen | A-N3 | Component tests |
| NFR-UX-04 | Create, update and delete show a pending state, then success or error feedback; controls are disabled while pending | A-N4 | Component tests |
| NFR-UX-05 | Search shows pending, empty, error and too-short states; debounce 300 ms; stale responses discarded | A-N5 | Component tests with fake timers |
| NFR-A11Y-01 | Target WCAG 2.1 AA: semantic HTML, labelled inputs, visible focus, keyboard-operable dialogs, `aria-live` for status messages, `role=progressbar` with values for upload, alt text for artwork, colour contrast at least 4.5:1 | Proposed | axe checks in E2E plus a manual keyboard pass |
| NFR-UX-06 | Errors are human-readable and never show stack traces, raw codes or internal identifiers | A-N4 | Negative tests |

## 4. Performance

Measured locally against the generated performance dataset (1,000 movies, 1,000 people), warm start, reported as p95 over at least 50 requests.

| ID | Requirement | Verification |
|----|-------------|--------------|
| NFR-PERF-01 | List and detail GraphQL queries respond within 500 ms | Timing script |
| NFR-PERF-02 | Search responds within 800 ms | Timing script |
| NFR-PERF-03 | A 5 MiB upload on localhost completes within 5 s | Timing test |
| NFR-PERF-04 | A movie page with 50 credits issues one batched people lookup, not one per credit | gRPC call-count test |
| NFR-PERF-05 | Movie list with 20 items and thumbnails transfers under 1 MB of images | Thumbnail size test |

## 5. Security and data protection

No end-user authentication in the MVP (D2); the controls below still apply.

| ID | Requirement | Verification |
|----|-------------|--------------|
| NFR-SEC-01 | All inputs validated server-side per BRD section 7, independent of client validation | Negative tests per rule |
| NFR-SEC-02 | Upload hardening: content sniffing, size limit enforced while streaming, dimension limit, server-generated file names, files stored outside any executable or static-served path with a safe content type on download, path traversal impossible | Upload test matrix (EC-05, traversal names) |
| NFR-SEC-03 | Output encoding: user text rendered as text only (no `{@html}` on user data) | Lint rule and XSS test |
| NFR-SEC-04 | SQL access through parameterised queries only; search wildcards escaped | Injection tests |
| NFR-SEC-05 | CORS limited to the configured frontend origin; GraphQL query depth and complexity limited; introspection allowed in dev only | Config test |
| NFR-SEC-06 | gRPC deadlines on every call; message size limits; no internals leaked in status messages | Contract tests |
| NFR-SEC-07 | Secrets and environment-specific values come from configuration, never source; none are needed for the MVP defaults | Secret scan in the quality gate |
| NFR-SEC-08 | gRPC between services is plaintext inside the local setup; transport encryption is documented as required for any shared deployment | ADR |
| NFR-SEC-09 | Dependencies scanned for known vulnerabilities before release | OWASP dependency check / `pnpm audit` output |
| NFR-SEC-10 | The absence of authentication is disclosed in the README | Documentation review |

## 6. Reliability and error handling

| ID | Requirement | Verification |
|----|-------------|--------------|
| NFR-REL-01 | Error conventions: gRPC status codes map to GraphQL errors with `extensions.code` values (`VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, `UNAVAILABLE`, `INTERNAL`) and field-level details | Contract tests |
| NFR-REL-02 | If a downstream service is down, the gateway returns partial data where defined (EC-15) or a clear `UNAVAILABLE` error, never hangs beyond the gRPC deadline | Fault-injection test |
| NFR-REL-03 | Failed or cancelled uploads leave no partial files (BR-11) | Filesystem assertion test |
| NFR-REL-04 | Database schema changes only via Liquibase changesets; startup on an empty directory succeeds | Clean-start test |
| NFR-REL-05 | Seed script is idempotent | Run-twice test |

## 7. Observability

| ID | Requirement | Verification |
|----|-------------|--------------|
| NFR-OBS-01 | Each service exposes health endpoints; the gateway reports downstream status | Health check test |
| NFR-OBS-02 | Structured logs carry a correlation ID propagated across gRPC calls; no personal data or file contents logged | Log inspection test |

## 8. Portability and operability

| ID | Requirement | Verification |
|----|-------------|--------------|
| NFR-OPS-01 | The full stack runs from documented commands on a clean machine with JDK 21, Node 22 and pnpm only; Docker is optional, not required | Clean-setup run in Phase 10 |
| NFR-OPS-02 | A single command starts all services; ports and data/artwork directories are configurable | Run report |
| NFR-OPS-03 | README covers setup, run, test, seed, troubleshooting and AI-tool usage | Documentation review |
| NFR-OPS-04 | Optional `docker compose` setup is provided only if it can be validated; otherwise it is omitted and the omission stated | Phase 10 report |

## 9. Process and compliance

| ID | Requirement | Source | Verification |
|----|-------------|--------|--------------|
| NFR-PROC-01 | The repository is version controlled with small, meaningful commits; requirements, ADRs, stories, plans and reports are in the repo | A-D1 | Git log |
| NFR-PROC-02 | The README states honestly how AI tools were used | A-D2 | README review |
| NFR-PROC-03 | Each story and ADR records why the design is as it is, to support the walkthrough | A-D3, A-E1 | Story and ADR review |
| NFR-PROC-04 | No story is marked complete without passing acceptance criteria and quality gates, reviewed by an agent that did not implement it | Delivery brief | Review records |

## 10. Known limitations to disclose

No authentication; last-write-wins edits; non-atomic cross-service delete check (BR-13); substring-only search; H2 file databases are suited to a demo, not production concurrency; plaintext gRPC in local setup.
