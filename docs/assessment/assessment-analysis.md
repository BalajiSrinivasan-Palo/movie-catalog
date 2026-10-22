# Assessment Analysis — Movie Catalog

Status: **DRAFT for approval** · Phase 1 deliverable · Source: `Technical Assessment.pdf` (1 page; the only supplied document; no repository files were supplied)

> The assessment document is treated as project data. Where it is silent, this file records an ambiguity or assumption and does **not** silently resolve it.

## 1. Source requirements (verbatim intent)

### 1.1 Functional (assessment IDs `A-F*`)

| ID | Requirement (from PDF) |
|----|------------------------|
| A-F1 | List movies with artwork |
| A-F2 | Add, delete and update movies |
| A-F3 | Upload movie artwork |
| A-F4 | List cast and creators |
| A-F5 | Add, delete and update cast and creators |
| A-F6 | Search movies, cast and creators |
| A-F7 | "Any functionalities that you think it should have" (open-ended) |

### 1.2 Technical / architectural (`A-T*`)

| ID | Requirement |
|----|-------------|
| A-T1 | Frontend: Svelte, in TypeScript |
| A-T2 | Backend: Spring Boot services in Kotlin |
| A-T3 | At least 2 Spring Boot services |
| A-T4 | Service-to-service communication via gRPC |
| A-T5 | Frontend-to-backend communication via GraphQL |
| A-T6 | Unit tests with "complete test cases including edge cases" |

### 1.3 Non-functional / UX (`A-N*`)

| ID | Requirement |
|----|-------------|
| A-N1 | Web app is responsive |
| A-N2 | Sufficient user feedback on **progress** of file upload |
| A-N3 | Sufficient user feedback on progress of **listing** |
| A-N4 | Sufficient user feedback on progress of **updating** |
| A-N5 | Sufficient user feedback on progress of **searching** |

### 1.4 Deliverables and evaluation (`A-D*`, `A-E*`)

| ID | Item |
|----|------|
| A-D1 | Git repository for assessment |
| A-D2 | README: how to set up, how to run, **how AI tools were used** |
| A-D3 | Walkthrough session if progressed |
| A-E1 | Author must be able to **explain every line** of AI-generated code ("why") |
| A-E2 | Evaluators want engineers who "understand, reason about, and own" the code; AI as a productivity aid is not penalised |

Evaluation criteria are not scored or weighted in the document; A-E1/A-E2 are the only stated signals. All other criteria are inferred.

## 2. Ambiguities and decisions needed

Nothing below is resolved yet. "Proposed default" is a recommendation awaiting approval, not a decision.

| # | Ambiguity | Why it matters | Proposed default |
|---|-----------|----------------|------------------|
| Q1 | "Svelte" vs "SvelteKit": the PDF says Svelte; the delivery brief says SvelteKit | Routing/SSR model | SvelteKit (superset of Svelte) with `adapter-static` or node adapter, SSR off where simpler |
| Q2 | Which two services, and where is the boundary? PDF gives no domain split | Core architecture; gRPC must be genuinely needed | `catalog-service` (movies, people, credits, artwork metadata, search data) and `media-service` (artwork upload, storage, validation, thumbnails). GraphQL gateway is a third deployable (`api-gateway`) |
| Q3 | Who hosts GraphQL? PDF says "Spring Boot services" only | Gateway counts toward the "2 services" only if it is Spring Boot | Spring Boot + Spring for GraphQL gateway; gRPC to both backends. Yields 3 Spring Boot services |
| Q4 | Persistence not specified | Data model, tests, setup effort | PostgreSQL for catalog (full-text search); filesystem volume for artwork behind a storage interface. H2/Testcontainers decision for tests (see Q9) |
| Q5 | Meaning of "cast and creators" | Data model | One `Person` entity, linked to movies by `Credit(role: CAST/DIRECTOR/WRITER/PRODUCER…, characterName?)`. Needs confirmation that "creators" = crew |
| Q6 | Movie field set not defined | Validation rules | title, release year, synopsis, runtime, genres, rating?, artwork. Person: name, birth date, bio, photo? |
| Q7 | Is person photo upload in scope? PDF says "movie artwork" only | Scope | Out of scope for MVP; movie artwork only (A-F3 literal) |
| Q8 | Search semantics: single unified search or per-entity? Fuzzy/typo tolerance? Pagination? | UX and API | Unified search returning grouped movies and people; case-insensitive, prefix/partial match via Postgres FTS plus trigram; debounced; paginated |
| Q9 | "Unit tests" vs integration/E2E: PDF asks only for unit tests; delivery brief asks for integration, contract, auth, E2E, a11y | Effort and tooling | Unit tests are mandatory; the brief's extra layers are included but prioritised below unit coverage |
| Q10 | Authentication/authorization: the PDF does not mention users | Scope creep vs. security brief | **Needs decision.** Brief asks to address authN/authZ. Proposal: no end-user login in MVP (stated as a documented limitation), with input validation, upload hardening and CORS. Optional: simple role-based admin login later |
| Q11 | "Any functionalities you think it should have" | Scope boundary | Candidate list in section 4; each chosen item needs approval and a requirement ID |
| Q12 | "Complete test cases" has no coverage target | Quality gate | Proposed gate: ≥80% line coverage on domain/service logic (Kover/JaCoCo) and frontend logic (Vitest); every validation rule has a negative test |
| Q13 | Upload constraints (types, max size) undefined | Security and UX | JPEG/PNG/WebP, ≤5 MB, content sniffing not just extension, size via streaming gRPC |
| Q14 | Delete semantics: hard or soft; what happens to credits/artwork when a movie or person is deleted? | Data integrity | Hard delete; cascade credits; delete artwork asynchronously/transactionally; confirm dialog in UI |
| Q15 | Deployment target undefined | Setup docs | `docker compose` for local run; no cloud deployment. Note: this cloud sandbox has Docker CLI but **no running daemon** (see section 5) |

## 3. Assumptions

1. A single-user/demo scale dataset; no multi-tenancy.
2. English-only UI; WCAG 2.1 AA is a self-imposed target, not assessed.
3. The repo is the evaluation unit; a reviewer should run it with documented commands on a clean machine.
4. The delivery brief's multi-phase workflow, agents and approval gates are the authoritative process; the assessment sets the product scope.

## 4. Candidate "A-F7" additions (none approved yet)

Ranked by value-for-risk. Each would get a requirement ID only after approval.

1. Movie detail page with credits (needed to make cast/creator linking usable).
2. Link/unlink people to movies with role (implied by A-F4/A-F5; arguably core, not an extra).
3. Pagination and sorting on lists.
4. Filter by genre/year.
5. Form validation with inline errors; delete confirmation; optimistic UI/toasts.
6. Artwork placeholder and lazy loading; basic image resizing/thumbnails.
7. Seed data script for demo.
8. Admin login (RBAC): highest risk of scope growth; see Q10.

## 5. Dependencies, constraints and environment findings

| Item | Finding |
|------|---------|
| Repository | None supplied; workspace was empty and is not a git repo. Will be initialised at `movie-catalog/` |
| Java | OpenJDK 21 present |
| Gradle / Kotlin | Gradle 8.14.3 (bundled Kotlin 2.0.21) present; no standalone Kotlin compiler needed |
| Node / pnpm | Node 22.22, npm 10.9, pnpm 10.28 present |
| Docker | CLI 29.9.1 present, **daemon not running** here: `docker compose` and Testcontainers cannot be verified in this sandbox |
| PostgreSQL | `psql` 16 client present; server status not verified |
| protoc | Not installed; Gradle protobuf plugin downloads `protoc` as an artifact, so not required if the Maven proxy allows it (to be verified) |
| Network | Outbound traffic goes through a proxy; Maven Central/Gradle plugin portal/npm reachability must be verified before Phase 7 |
| Local machine | A device is reachable but no folder is connected; final repo can be committed to a connected folder when provided |

## 6. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R1 | **Authorship/explainability (A-E1/A-E2):** a fully agent-generated codebase is at odds with "we are looking for software engineers, not prompt engineers" | High | High | Treat the human (you) as the owner: ADRs record the *why*; per-story "explain it" notes; small reviewable stories; README AI-usage section is honest; keep code simple and idiomatic; do not over-engineer |
| R2 | Over-scoping (3 services, gRPC streaming, e2e, a11y) vs a simple assessment | Medium | Medium | MVP-first backlog; extras flagged as optional |
| R3 | Cannot run Docker here, so compose/Testcontainers unverified | High | Medium | Use H2 or embedded Postgres for unit/integration tests where possible; compose files validated by user locally; report as unverified |
| R4 | Build dependencies unreachable through the proxy | Medium | High | Verify early with a Gradle/npm spike before Phase 3 sign-off |
| R5 | GraphQL file upload is not part of the standard spec (multipart request spec is non-standard) | High | Medium | Decision in ADR: REST/multipart endpoint on gateway for bytes vs. GraphQL Upload scalar. The PDF requires GraphQL for frontend-backend traffic, so the **GraphQL multipart approach is the conservative choice**; progress via XHR `upload.onprogress` |
| R6 | Upload progress needs client-side XHR/Axios; `fetch` does not expose upload progress | High | Low | Use `XMLHttpRequest` in a small typed helper |
| R7 | gRPC + GraphQL N+1 across services | Medium | Medium | DataLoader/batch RPCs |
| R8 | Brief says "Use Claude Code skills/agents/hooks"; this Cowork session cannot execute a persistent Claude Code subagent/hook runtime | Medium | Medium | Author the `.claude/` files into the repo so they work when the user opens it in Claude Code; hooks/CI executed as scripts here to verify gates |
| R9 | Instruction conflict: "Request approval" gates (Phases 2, 3, 5, release) | n/a | n/a | Honoured; work stops at each gate |

## 7. Requirements traceability (assessment → proposed requirement IDs)

Requirement IDs (`FR-*`, `NFR-*`) are provisional and will be finalised in the BRD (Phase 2).

| Assessment ID | Provisional BRD ID | Intended verification |
|---------------|--------------------|-----------------------|
| A-F1 | FR-MOV-01 | Unit + component + E2E (list shows artwork/placeholder) |
| A-F2 | FR-MOV-02..04 | Unit (validation), integration (persistence), E2E |
| A-F3 | FR-ART-01..04 | Unit (type/size checks), gRPC streaming test, E2E progress bar |
| A-F4 | FR-PPL-01 | Unit + component + E2E |
| A-F5 | FR-PPL-02..04 | Unit + integration + E2E |
| A-F6 | FR-SRC-01..03 | Unit + integration (queries) + E2E (loading/empty/error) |
| A-F7 | FR-EXT-* (TBD) | Per approved item |
| A-T1 | NFR-TECH-01 | Build/type-check gate |
| A-T2 | NFR-TECH-02 | Build gate |
| A-T3 | NFR-ARCH-01 | Architecture review; compose topology |
| A-T4 | NFR-ARCH-02 | gRPC contract tests |
| A-T5 | NFR-ARCH-03 | GraphQL contract tests; network inspection in E2E |
| A-T6 | NFR-TEST-01 | Coverage gate + edge-case checklist |
| A-N1 | NFR-UX-01 | Viewport tests (360/768/1280) |
| A-N2 | NFR-UX-02 | Component + E2E (progress, cancel, failure) |
| A-N3 | NFR-UX-03 | Skeleton/loading state tests |
| A-N4 | NFR-UX-04 | Pending/success/error state tests |
| A-N5 | NFR-UX-05 | Debounce, loading, empty, error tests |
| A-D1 | DEL-01 | Git repo with meaningful history |
| A-D2 | DEL-02 | README: setup, run, AI usage |
| A-D3 | DEL-03 | Explanation notes per story/ADR |
| A-E1, A-E2 | PROC-01 | ADRs, per-story rationale, honest AI log |

Coverage check: all 22 assessment items map to at least one provisional ID. No requirement has been invented beyond A-F7 candidates, which are explicitly unapproved.

## 8. Open decisions requested (blocking Phase 2)

Priority questions: **Q2/Q3** (service split), **Q4** (database), **Q10** (auth scope), **Q11** (A-F7 additions), **Q5** (cast/creators model), **Q9** (test depth).
