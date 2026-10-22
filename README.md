# Movie Catalog

A small movie catalog built for the PALO-IT technical assessment. You can add, edit and delete movies and people, attach people to movies as cast and crew, upload a poster for a movie, and search both movies and people.

It is made of a SvelteKit frontend and three Kotlin Spring Boot services. The browser talks GraphQL to a gateway, and the gateway talks gRPC to two backend services.

## Architecture

![Movie Catalog architecture: the user's browser loads the SvelteKit frontend on port 5173, which sends GraphQL and poster requests to the api-gateway on 8080; the gateway calls movie-service (gRPC 9091) and people-service (gRPC 9092), and people-service asks movie-service whether a person still has credits. Each service has its own H2 database, and movie-service also keeps the poster files.](docs/architecture/architecture.svg)

The diagram is `docs/architecture/architecture.svg` (hand-drawn SVG, edit it directly).

- **The browser talks only to the gateway** (NFR-ARCH-03): data goes through GraphQL at `/graphql`, and poster images come from plain, cacheable `GET /artwork/{movieId}/thumbnail` and `/original` requests. In development the Vite server on port 5173 forwards both paths to the gateway, so the app uses one origin.
- **The services talk only gRPC** (contracts in `contracts/`). The gateway calls both services. people-service calls movie-service once, before deleting a person, to ask whether that person still has credits.
- **Each service owns its data:** one H2 file database each, with Liquibase migrations, and movie-service also keeps the poster files. Nothing needs Docker or a database server; everything lives under `data/`, which git ignores.
- Each service also serves an HTTP Actuator health endpoint (ports 8080, 8081 and 8082), which `make dev-check` polls. The app's footer instead asks the gateway's GraphQL `health` query, which checks both services over gRPC, and shows a warning only when something is down.

## Run it with make

Prerequisites: JDK 21, Node 22, pnpm 10, and Python 3 (for the repository checks). The Gradle wrapper (`./gradlew`) is in the repository.

```bash
git clone <repository-url> movie-catalog && cd movie-catalog
make setup        # once: frontend packages, the Playwright browser and the git hooks
make dev          # start movie-service, people-service, api-gateway and the frontend in the background
make dev-check    # wait until all four answer ("all four are up"); fails after 120 s
make seed         # load the sample films, people, credits and posters (safe to run again)
```

Then open **http://localhost:5173**. Logs are in `data/logs/`.

```bash
make dev-stop     # stop everything that make dev started
```

`make dev` uses ports 5173, 8080, 8081, 8082, 9091 and 9092. It starts everything in the background, so a part that cannot get its port stops instead of moving to another one, and `make dev-check` reports a timeout; the reason is in `data/logs/`. Stop the old processes with `make dev-stop` and run `make dev` again.

## URLs

With `make dev` running:

| What | URL | Notes |
|------|-----|-------|
| **Frontend (the app)** | http://localhost:5173 | SvelteKit app served by Vite |
| GraphQL API | http://localhost:8080/graphql | `POST` only, JSON body `{"query": ...}`; also reachable as http://localhost:5173/graphql through the proxy. Introspection is on in the `dev` profile only. No GraphiQL page. |
| Poster images | http://localhost:8080/artwork/{movieId}/thumbnail and `.../original` | Plain `GET` with an ETag; the exact links (with a `?v=` version) come from a movie's `artwork { thumbnailUrl url }` field |
| api-gateway health | http://localhost:8080/actuator/health | |
| movie-service health | http://localhost:8081/actuator/health | movie-service's API is gRPC on `localhost:9091`, not HTTP |
| people-service health | http://localhost:8082/actuator/health | people-service's API is gRPC on `localhost:9092`, not HTTP |

Try the API from a terminal:

```bash
curl -s http://localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"{ movies { items { title releaseYear } } }"}'
```

## All make targets

`make` (or `make help`) prints this list. Each target runs only the commands shown in this README, so you can also run those directly.

| Target | What it does |
|--------|--------------|
| **Run** | |
| `make setup` | Install frontend packages, the Playwright browser and the git hooks |
| `make dev` | Start the three services and the Vite dev server in the background |
| `make dev-check` | Wait until all four parts answer |
| `make dev-stop` | Stop what `make dev` started |
| `make seed` | Load the sample data into the running stack (safe to run twice) |
| **Build and test** | |
| `make build` | Build backend and frontend (runs the backend tests too) |
| `make test` | All unit and integration tests (`backend-test` and `frontend-test`) |
| `make backend-test` | Backend tests, including the memory-bounded upload streaming test |
| `make streaming` | Only the artwork streaming test (128 MB heap) |
| `make frontend-check` | Type check and lint the frontend |
| `make frontend-test` | Frontend unit and component tests with the coverage gate |
| `make frontend-build` | Static frontend build |
| `make e2e-mocked` | Layout and accessibility browser tests against a mocked API (no backend needed) |
| `make e2e` | Browser journeys against the real stack on a fresh temporary data directory |
| `make perf` | Load 1,000 movies and people and time the API (stack must be running); writes `reports/perf.md` |
| **Quality gates** | |
| `make verify` | Every gate that applies; writes `reports/verify-*.log` |
| `make traceability` | Requirement to story to test check |
| `make secrets` | Scan the working tree for secrets |
| `make quality` | ktlint, detekt and the Kover coverage gate (opt-in, unverified; see below) |
| `make format` | Fix Kotlin and frontend formatting |
| **Other** | |
| `make wrapper` | Re-create the Gradle wrapper (needs a local Gradle 9.8) |
| `make docker-config`, `make docker-up`, `make docker-down` | Optional containers, app on http://localhost:8088 (unverified, see below) |
| `make clean` | Remove build output (keeps `data/` and `reports/`) |

## Status of this repository

Measured on 2026-10-22; the logs are in `reports/`.

- **Frontend:** type check, lint, production build and 68 browser tests (layout, accessibility, mocked API) pass. The 269 unit and component tests cannot run on this machine at the moment: `pnpm install` refuses packages in `pnpm-lock.yaml` that are younger than pnpm's minimum release age, which left two copies of Vitest in `node_modules`. They need one `pnpm install --frozen-lockfile` once those packages are old enough.
- **Backend:** built and tested in the owner's terminal: contracts, movie-service (76 tests), api-gateway (55) and people-service (27), all passing. A full `./gradlew build` has not been re-run since the last changes.
- **Review:** the `reviewer` agent rated S-701 (visual refresh) and S-702 (search scope, year picker, multi-genre filter) "APPROVED pending unit-test run", with no blocking findings (`reports/S-701/review.md`, `reports/S-702/review.md`). `security-review-2.md` passes with no critical or high finding and two medium ones about GraphQL query cost. Neither story is Done until the unit tests, the full Gradle build and `verify.sh` have run. Nothing has been committed yet.

## Run the pieces yourself

`make dev` runs `scripts/dev.sh`. To run each part in its own terminal instead:

```bash
./gradlew :movie-service:bootRun
./gradlew :people-service:bootRun
./gradlew :api-gateway:bootRun
cd frontend && pnpm dev
```

Configuration is by environment variables (defaults in each `application.yml`): `GATEWAY_PORT`, `MOVIE_SERVICE_GRPC_PORT`, `PEOPLE_SERVICE_GRPC_PORT`, `MOVIE_HTTP_PORT`, `PEOPLE_HTTP_PORT`, `MOVIE_DB_PATH`, `PEOPLE_DB_PATH`, `ARTWORK_DIR`, `FRONTEND_ORIGIN` (the one origin the gateway allows for CORS). The full table is in `docs/architecture/architecture.md`, section 10.

### Sample data

`make seed` (or `scripts/seed.sh`, or `cd frontend && pnpm seed`) loads twelve real films with their cast, crew and posters, through the public GraphQL API only. Running it again creates nothing new.

- Ten are public-domain classics (The General, The Cabinet of Dr. Caligari, Sherlock Jr., The Gold Rush, The Phantom of the Opera, Battleship Potemkin, His Girl Friday, Charade, Plan 9 from Outer Space, House on Haunted Hill). Their posters are in `scripts/seed/posters/`, with the source and licence of each in `CREDITS.md`.
- Two are recent films (Speed, Harry Potter and the Philosopher's Stone) whose posters are copyrighted, so they are not in the repository. The seed uploads them only if you put the files in the git-ignored `data/local-posters/` (names in `CREDITS.md`); otherwise those two films show a placeholder.

## Test

```bash
scripts/verify.sh              # every gate that applies: repository checks, Gradle build and tests, frontend check, lint, tests, coverage, build
./gradlew test                 # backend unit and integration tests
./gradlew :movie-service:streamingTest        # the memory-bounded upload tests (they run with a 128 MB heap)
cd frontend && pnpm test       # unit and component tests (add :coverage for the coverage gate)
cd frontend && pnpm exec playwright test --project=mocked   # layout and accessibility, mocked API
scripts/e2e.sh                 # journeys against the real stack on a fresh temporary data directory
node scripts/perf/perf.mjs     # loads 1,000 movies and people, writes reports/perf.md
```

`scripts/verify.sh` never hides a failure. A gate that cannot run is printed as NOT RUN, and the last line says so.

### Static analysis and coverage (`make quality`)

ktlint, detekt and a Kover line-coverage gate (80 percent, NFR-TEST-02) run through an opt-in Gradle init script, `gradle/quality.init.gradle.kts`, with settings in `.editorconfig` and `config/detekt/detekt.yml`. They are not part of `./gradlew build` yet because the plugin versions were chosen without being able to resolve them. After one green `make quality` on a real machine, move them into the normal build (ADR-013) and save the output in `reports/`. Until then this gate is **unverified**, and `scripts/verify.sh` does not run it.

### Containers (optional, unverified)

`docker compose up --build` builds and starts all four parts and serves the app at http://localhost:8088 (`compose.yaml`, `docker/`). Only `docker compose config` was run when this was written, because there was no Docker daemon; the images have never been built. The supported way to run the project is the one above, which needs no Docker (NFR-OPS-01). See D19 in the decisions log.

## What the app does

- Movies: a poster grid with paging, sort by title or year, filter by one or more genres (a movie matches any of them) and by a year range chosen by typing or from a year picker; a detail page with the poster, facts and cast and crew; create, edit, delete. Title, year, runtime, synopsis and genres are validated in the browser and again on the server.
- People: list with paging and name filter; detail page with filmography; create, edit, delete. A person who still has credits cannot be deleted. The page names the movies that block it.
- Credits: add a person to a movie as actor, director, writer or producer, edit the role or character, remove it.
- Artwork: drag a JPEG, PNG or WebP up to 5 MiB onto the drop zone, or choose the file, with a progress bar and a cancel button. The server checks the real file type from its first bytes, limits the size while reading, limits the dimensions, makes a 320 px thumbnail, and replaces the old image only after the new one is stored. Lists show thumbnails; the detail page shows the original.
- Search: one box in the header with a scope menu (All, Titles, or People for cast and crew); case-insensitive substring match on movie title, synopsis and genre, and on person name and biography. `%`, `_` and `\` are matched literally. Results are grouped, with "Show more".
- Every action shows what is happening (loading, saving, uploading) and what went wrong, in plain words, without losing what you typed. The pages work from 360 px wide, and the browser tests check keyboard use, target size and axe rules.

## Design in short

The reasons are in `docs/adr/`. The ones a reader usually asks about:

- **Three services (ADR-001).** The assessment asks for at least two. People and movies are separate domains, and the gateway keeps the browser away from both.
- **Credits live in movie-service (ADR-003).** The gateway fills in person names with one batched call to people-service per page. Deleting a person asks movie-service whether credits exist.
- **Errors (ADR-005).** Services answer with a gRPC status plus a typed detail. The gateway turns that into one of five GraphQL codes with a message key, field errors and a correlation id. The frontend turns keys into text; stack traces never reach the browser.
- **Uploads (ADR-006).** The browser sends a GraphQL multipart request, as in the GraphQL multipart request spec. The gateway streams the file to movie-service over gRPC in 64 KiB chunks. A custom header is required, which blocks cross-site form posts.
- **Images (ADR-007).** Image bytes are fetched with plain cacheable GET requests (`/artwork/{id}/thumbnail` and `/original`) with an ETag, so the browser can cache them. All other data goes through `/graphql`.
- **Search (ADR-009).** Each service searches its own data; the gateway runs both in parallel and merges. No ranking and no typo tolerance.

## Known limitations

These are choices, not accidents. They are also listed in `docs/requirements/BRD.md`.

- No login and no roles (D2). Anyone who can reach the gateway can change data. CORS allows one configured origin.
- Two people editing the same record: the last save wins.
- gRPC between services is plain text. Fine on one machine, not for a network you do not control.
- H2 files mean one instance per service. No clustering.
- No rate limiting.
- Deleting a person checks for credits and then deletes, as two steps in two services (BR-13). A credit added in that gap could be orphaned. The page shows such a credit as "person not found" instead of failing.
- Search is substring only and has no ranking.

## Repository map

| Path | What is there |
|------|---------------|
| `api-gateway/`, `movie-service/`, `people-service/` | The three Spring Boot services |
| `contracts/` | The `.proto` files shared by the services, with generated code |
| `service-support/` | Small shared code (error mapping, logging, correlation ids) |
| `frontend/` | SvelteKit app (`src/lib` holds API client, stores, components, views), unit tests and `e2e/` |
| `docs/` | Requirements, architecture, ADRs, backlog and stories, plans, decisions log |
| `Makefile`, `compose.yaml`, `docker/` | Task shortcuts; optional container setup |
| `scripts/` | `verify.sh`, `dev.sh`, `seed.sh`, `e2e.sh`, `perf/`, repository checks, git hooks |
| `reports/` | Evidence: command output and reports from the gates |
| `CLAUDE.md`, `.claude/` | Rules, skills and agents used while building |

## How AI was used

This project was built with Claude Code as a coding agent, inside a fixed process that I set and approved at each step. The full story, including what went wrong, is in `docs/walkthrough.md`.

![How the AI tooling was used: the Technical Assessment PDF goes through analysis, requirements, architecture, backlog and refinement, with the owner approving each document in the decisions log. Each story then runs a three-agent pipeline coordinated by the main session: story-planner writes the plan and the owner approves it; backend-implementer or frontend-implementer builds it and writes an implementation report, and the owner runs the backend Gradle commands; reviewer and security-reviewer check the code against the story and the plan and write a review, and the owner picks the findings to fix, which go back to the build step. An agent is started by an @-mention, by naming it, or automatically by its description. Guardrails are always on.](docs/ai-workflow.svg)

### From the assessment brief to code

1. **Input.** The only input was the one-page Technical Assessment PDF (not stored in this repository). It was treated as data, not instructions: `docs/assessment/assessment-analysis.md` restates its requirements (functional A-F1 to A-F7, non-functional A-N1 to A-N5) and lists every gap as a question for me.
2. **Specification, in order.** Analysis, then `docs/requirements/` (BRD, NFR, traceability matrix), then `docs/architecture/` and 15 ADRs in `docs/adr/`, then `docs/backlog/` (epics and 38 stories with acceptance criteria), then refinement. Each document waited for my approval before the next one started.
3. **One pipeline per story.** Each story passes through three agents, with my approval in between (next section).
4. **Decisions.** Every answer and change of direction I gave is numbered in `docs/assessment/decisions-log.md` (D1 to D26), with the reason. ADRs record the architecture-level ones.

### The story pipeline: one agent per step (D26)

| Step | Agent | Reads | Writes | Then |
|------|-------|-------|--------|------|
| 1 Plan | `story-planner` | the story, its requirements, the architecture and ADRs, the code it will change | `docs/plans/S-xxx.md` (status Draft) | I answer its open questions and approve the plan |
| 2 Build | `backend-implementer` and/or `frontend-implementer`, as the plan assigns | the approved plan and the story | code, tests, logs in `reports/S-xxx/`, and `reports/S-xxx/implementation.md` (files changed, AC-to-test map, commands) | I run the backend Gradle commands and save the output (D15) |
| 3 Review | `reviewer` (plus `security-reviewer` for uploads, validation, CORS or GraphQL limits) | the story, the plan, `implementation.md`, the changed files and the logs | `reports/S-xxx/review.md`: AC table, plan-task table, findings with file and line, verdict | I pick which findings go back to step 2; APPROVED with evidence marks the story Done |

- **The hand-offs are files, not memory:** nothing is committed yet, so there is no git diff. The implementation report's list of changed files is what the reviewer reads.
- **Every artefact names its author** (`Planned by:`, `Implemented by:`, `Reviewed by:`). The reviewer stops if it finds its own name as planner or implementer.
- **The plan's `Status:` line tracks the story:** Draft, Approved by owner, Implemented (awaiting review), Changes required, then Done. `docs/plans/README.md` lists who may set each one.
- **The main session only coordinates:** it starts each agent with the story ID, shows me the result and waits for my decision. It does not write a story's plan, code or review itself (`CLAUDE.md` rule 7).

This pipeline applies from the next story on. S-701 and S-702 were planned and built in the main session, before D26, and then went through step 3 only: the `reviewer` and `security-reviewer` agents reviewed them in a separate session (`reports/S-701/review.md`, `reports/S-702/review.md`, `reports/security-review-2.md`).

### How an agent is started

| Way | Example | How reliable |
|-----|---------|--------------|
| @-mention | `@agent-reviewer review S-701` | always runs that agent |
| Name it in the request | "Use the story-planner agent to plan S-703" | usually followed |
| Automatic | a task that matches an agent's `description`, such as "Use when a story has no plan yet" for `story-planner` | Claude decides; not guaranteed |
| Whole session as one agent (terminal) | `claude --agent reviewer` | the main session itself runs as that agent |

In the pipeline the main session starts each agent with only the story ID, for example "Review S-701", and the agent reads everything else from the files. Editing an agent file takes effect within seconds, without a restart (sub-agents docs: https://code.claude.com/docs/en/sub-agents.md).

### Where the AI configuration lives

```text
CLAUDE.md                       7 standing rules every session reads first
.claude/
├── settings.json               PreToolUse hook: runs scripts/hooks/guard-bash.sh before every shell command
├── agents/                     9 specialised subagents (name, description, allowed tools, instructions)
│   ├── story-planner.md        pipeline step 1: story → plan
│   ├── requirements-analyst.md
│   ├── architect.md
│   ├── grpc-graphql-specialist.md
│   ├── backend-implementer.md  pipeline step 2: plan → code (backend)
│   ├── frontend-implementer.md pipeline step 2: plan → code (frontend)
│   ├── test-engineer.md
│   ├── reviewer.md             pipeline step 3: code → review against story and plan
│   └── security-reviewer.md
└── skills/                     6 skills, each a SKILL.md with a name, a "use when" description and the rules
    ├── story-workflow/
    ├── quality-gates/
    ├── kotlin-spring-boot/
    ├── grpc-contracts/
    ├── graphql-schema/
    └── sveltekit-typescript/
claude-config/                  the same agents, skills and settings, staged for copying into .claude/
scripts/hooks/                  guard-bash.sh (the PreToolUse guard) and pre-commit (runs scripts/verify.sh)
```

Everything is plain Markdown or a short shell script, so it can be read and reviewed like the code.

### Which agent does which part

| Agent | Used for | Tools it is allowed | Skills it names | Evidence it ran |
|-------|----------|---------------------|-----------------|-----------------|
| `story-planner` | **Pipeline step 1:** turns one story into `docs/plans/S-xxx.md`; never writes code | Read, search, edit, shell (read-only commands) | names the plan's implementers | new in D26, not run yet |
| `requirements-analyst` | Analysis, BRD, NFR, stories; keeps the traceability matrix consistent; lists ambiguities for the owner instead of deciding them | Read, search, edit, shell | none (runs `scripts/check_traceability.py`) | no separate record |
| `architect` | Architecture, ADRs, service boundaries, contract changes | Read, search, edit (no shell) | none | no separate record |
| `grpc-graphql-specialist` | Protobuf and GraphQL contracts, error mapping (ADR-005), upload streaming (ADR-006) | Read, search, edit, shell | none (the contract skills apply by their description) | no separate record |
| `backend-implementer` | **Pipeline step 2:** builds the backend part of an approved plan, with tests, and writes `implementation.md` | Read, search, edit, shell | `kotlin-spring-boot`, `grpc-contracts`, `graphql-schema` | no separate record |
| `frontend-implementer` | **Pipeline step 2:** builds the frontend part of an approved plan, with loading, empty, error and success states, and writes `implementation.md` | Read, search, edit, shell | `sveltekit-typescript` | no separate record |
| `test-engineer` | A named test for every acceptance criterion and edge case | Read, search, edit, shell | none | no separate record |
| `reviewer` | **Pipeline step 3:** compares the code with the story and the plan, writes `review.md` with a verdict, and is the only one who sets Done | Read, search, shell, write (its instructions allow only the review report and status lines) | none | `reports/review-independent-1.md`, `reports/S-701/review.md`, `reports/S-702/review.md` |
| `security-reviewer` | Uploads, validation, injection, CORS, headers, GraphQL limits, dependencies | **Read-only** | none | `reports/security-review-1.md`, `reports/security-review-2.md` |

"No separate record" means no report in `reports/` names that agent: the work exists, but nothing shows whether a subagent or the main session did it. From D26 on, every plan, implementation report and review names its agent, so this column fills in as stories go through the pipeline. The `security-reviewer` cannot edit or write files at all. The `reviewer` can write, so it can save its own report, but cannot edit code; keeping it to the report is an instruction, not a hard limit. The later visual work (S-701, S-702) was planned and implemented in the main session, before D26, with the skills loaded directly; the two reviewers then reviewed it.

### How skills are picked up

| Skill | Picked up when (its description) | Used by |
|-------|----------------------------------|---------|
| `story-workflow` | starting, implementing or closing any story in `docs/backlog` | the main session, to run the pipeline |
| `quality-gates` | before declaring any work complete | the implementers, before they hand over |
| `kotlin-spring-boot` | writing or reviewing backend code | `backend-implementer` |
| `grpc-contracts` | touching `contracts/` or any gRPC call | `backend-implementer`, `grpc-graphql-specialist` |
| `graphql-schema` | editing `schema.graphqls` or gateway code | `backend-implementer`, `grpc-graphql-specialist` |
| `sveltekit-typescript` | writing or reviewing frontend code, components, routes or tests | `frontend-implementer` |

A skill reaches the model in one of three ways:

1. **By its description.** Claude Code shows the model each skill's name and "use when" description; when a task matches (for example, editing `schema.graphqls`), the model loads that skill's rules before it starts.
2. **By an agent's instructions.** An agent file can name the skills to follow. `backend-implementer` says "follow the kotlin-spring-boot and grpc-contracts skills", so every backend story uses them.
3. **By the story pipeline.** `story-workflow` tells the main session to start `story-planner`, then the implementer the plan names, then `reviewer`, by name and with only the story ID; each agent reads the rest from the files.

Outside the pipeline, the main session delegates to an agent whose description fits the task, or names it. An agent's `tools` line limits what it can do: `security-reviewer` has no edit or write tools at all.

### Guardrails that are always on

- **`CLAUDE.md`:** one story at a time, and no plan means no code; nothing is Done without command output; scope, architecture and contract changes need my approval and a decisions-log entry; no secrets, no skipped checks, no destructive commands without approval; the implementer never reviews its own work.
- **PreToolUse hook (`.claude/settings.json` runs `scripts/hooks/guard-bash.sh`):** checks every shell command before it runs and blocks skipping git hooks, force pushes, `git reset --hard`, recursive deletes, access to `.env` files and SSH keys, and Gradle commands whose failure is hidden with `|| true`. On 2026-10-22 it blocked two commands (one contained `|| true` next to `gradlew`, one contained the text of a git flag), and each was redone without the pattern rather than worked around.
- **git pre-commit hook:** runs `scripts/verify.sh`, the same gates as CI.
- **Traceability:** `scripts/check_traceability.py` fails if a requirement has no story, or a BRD acceptance criterion is not owned by exactly one story.
- **Evidence:** command output lives in `reports/`. When Gradle could not run in the AI's environment, backend results were marked unverified until I ran the commands myself (D15).

### My responsibility

I read the code and can explain each part. Where a choice looked odd, I asked for the reason and recorded it in the decisions log.
