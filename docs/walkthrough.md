# Walkthrough: how this was built, and how to explain it

This is for the author (and any reviewer) to read before the assessment conversation. It covers the flow of the work, the parts that are easiest to get asked about, and the mistakes that were made on the way.

## 1. The process, phase by phase

| Phase | Output | Approval recorded as |
|-------|--------|----------------------|
| 1 Analysis | `docs/assessment/assessment-analysis.md`, questions and assumptions | D1 to D6, then D7 to D9 |
| 2 BRD and NFR | `docs/requirements/` with numbered requirements and business rules | with phase 3 |
| 3 Architecture and ADRs | `docs/architecture/`, `docs/adr/ADR-001..013`, contracts | D10 to D13 |
| 4 Epics and stories | `docs/backlog/`, about 35 stories with acceptance criteria | |
| 5 Refinement | `docs/backlog/refinement.md`, changes C-1 to C-8 | D14 |
| 6 Plans | `docs/plans/S-xxx.md` for the first stories | |
| 7 Implementation | the code, story by story | D15 to D17 |
| 8 Tests | unit, integration, contract, browser | |
| 9 Independent review | reviewer agent on code it did not write | D16 (moved to the end) |
| 10 Final audit | traceability and clean-clone run | |

The decisions log is the quickest way to see where I overruled or redirected the agent. D15 and D16 are the two that changed the process: the sandbox could not reach Maven Central, so Gradle runs happen on my computer in batches; and I asked for the reviewer to run once at the end rather than after each story.

## 2. Where to look for each assessment requirement

| Requirement | Where |
|-------------|-------|
| SvelteKit and TypeScript frontend | `frontend/`, strict TypeScript, ADR-010 |
| Kotlin and Spring Boot, at least two services | three services, ADR-001 |
| gRPC between services | `contracts/`, ADR-004; `scripts/check_no_http_between_services.sh` fails the build if services call each other over HTTP |
| GraphQL for the frontend | gateway, ADR-002; schema in `api-gateway/src/main/resources/graphql/` |
| Spec-driven process | `docs/` in numbered order; every story lists the requirements it covers |
| Evidence and review | `reports/`, `scripts/check_traceability.py`, the reviewer agent |

## 3. Things you will probably be asked

**Why does a credit live in movie-service and not people-service?** A credit is about a movie (who worked on it, in what role), and deleting a movie must delete its credits in one transaction. If credits lived with people, deleting a movie would need a cross-service call that can fail halfway. The cost is that deleting a *person* must ask movie-service whether credits exist. That is the one real service-to-service call (ADR-003) and it is also why BR-13 exists.

**Why is the person delete not atomic?** Two services, two databases, no distributed transaction. A credit created between "check" and "delete" would point at a missing person. The page shows it as "person not found" rather than failing. Doing better needs a saga or a shared database; both were judged too heavy for this size (BR-13).

**How does the upload stay within memory?** The container writes the multipart body to a temporary file; the gateway reads that file in 64 KiB chunks and sends them as gRPC messages with flow control; movie-service writes to a `.part` file, counting bytes as it goes, and stops at 5 MiB. Dimensions are read from the header before the image is decoded. The 128 MB heap `streamingTest` exists to prove this: a 5 MiB file passes, 5 MiB plus one byte is refused, and a 6000 by 6000 image is accepted without decoding the whole picture into memory.

**Why can a half-finished upload never replace good artwork?** New files are written under temporary names, then committed in a transaction; the old files are deleted only after the commit. A per-movie lock stops two uploads for the same movie from interleaving. If the browser cancels, the request is dropped before the commit and nothing changes. The page says so: "Your existing artwork is unchanged".

**Why a custom header on uploads?** A multipart POST is a "simple" cross-site request that a hostile page could submit from a visitor's browser without a CORS preflight. Requiring `Apollo-Require-Preflight` forces a preflight, and CORS only allows our own origin.

**How do searches avoid SQL injection and wildcard surprises?** Queries use the JPA Criteria API with bound parameters, and `%`, `_` and `\` in the user's text are escaped so they match themselves. The query is trimmed and limited to 2 to 100 characters on the server as well as in the browser.

**How are errors kept readable and safe?** Services never send exception text. Each failure becomes a gRPC status plus a typed detail with a message key. The gateway maps it to a GraphQL error with one of five codes and a correlation id; the frontend shows the text it has for that key. The same id is in the logs, so a user report can be traced.

**Why Svelte 5 runes and no UI library?** Fewer dependencies to audit and explain (`docs/frontend-dependencies.md` justifies each one), and the accessibility rules (focus, target size, labels) are easier to enforce on a small set of components that are tested directly.

## 4. Mistakes and corrections along the way

These are worth knowing because they show where the checks earned their keep.

- A test fixture was assumed to be a 16 by 8 WebP. Decoding its header showed 1 by 1, so the tests were corrected before they could mislead anyone.
- The first accessibility run found a movie "not found" page with no heading, and the responsive run found a checkbox hit area of 20 px and a file input 21 px high. All three were fixed in the components, not in the tests.
- A coverage gate failed because the new catalog API module had no test; the test was added rather than the threshold lowered.
- A shell command used during development killed its own session; the process-stop command was changed.
- Version numbers for gRPC, protobuf, the protobuf plugin and TwelveMonkeys were picked from memory, because the registry could not be reached. They are flagged as unverified until the first Gradle run.
- Several Spring Boot 4 and Spring for GraphQL class names used by the multipart route were written from memory too (see section 5).

## 5. What is still unverified, and the first things to check

Run `./gradlew build` once and expect the first failures to be about:

1. The dependency versions in `gradle/libs.versions.toml`.
2. In `api-gateway/.../Upload.kt`: the `GraphQlHttpHandler` bean and its `handleRequest`, `ServerRequest.from(...).body(...)`, `WebGraphQlInterceptor` and `configureExecutionInput`, and `ExecutionInput.Builder.graphQLContext`.
3. `org.springframework.boot.json.BasicJsonParser` still existing in Spring Boot 4 (the gateway avoids Jackson on purpose, because Boot 4 moved to Jackson 3).
4. The multipart size-limit exceptions raised by the servlet container, which the gateway maps to the "too large" message.
5. Hibernate with Kotlin entities (open classes, nullable ids).

After the build passes, run `./gradlew :movie-service:streamingTest`, then `scripts/e2e.sh`, then `node scripts/perf/perf.mjs`, and keep the outputs in `reports/`.
