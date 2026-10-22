# Business Requirements Document — Movie Catalog

Status: **APPROVED 2026-10-21** · Version 0.2 (adds message keys `sort.invalid`, `filter.year.invalid`, `credit.role.invalid`, `id.invalid` found in Phase 5 review) · 2026-10-21
Inputs: `docs/assessment/assessment-analysis.md`, `docs/assessment/decisions-log.md` (D1–D9)
Companion documents: `NFR.md`, `traceability-matrix.md`

## 1. Objectives

1. Provide a web application to browse and maintain a catalog of movies, with artwork, and of the people (cast and creators) who worked on them.
2. Let users find movies and people quickly through search.
3. Give clear feedback during every operation that takes time: listing, searching, updating and artwork upload.
4. Satisfy the technical assessment: Svelte/TypeScript frontend, at least two Kotlin Spring Boot services, gRPC between services, GraphQL to the frontend, and unit tests that include edge cases.

Success is measured by: every requirement below has passing test evidence (Phase 10 report), the app runs from the README on a clean machine, and the owner can explain every part of the code.

## 2. Scope

**In scope:** movie CRUD; movie artwork upload, replace and removal; person CRUD; credits linking people to movies with a role; unified search; pagination, sorting and genre/year filters; movie and person detail pages; seed data; artwork thumbnails; responsive UI; progress and error feedback.

**Out of scope (MVP):** user accounts and login (D2); person photos; fuzzy search and relevance ranking (D9); cloud deployment; internationalisation; ratings and reviews; video or trailer upload; audit history; concurrent-edit conflict resolution beyond the limitation noted in BR-12.

## 3. Stakeholders

| Stakeholder | Interest |
|-------------|----------|
| Catalog user (anonymous, MVP) | Browse, search and maintain data with clear feedback |
| Assessment reviewers | Meet stated requirements; read and run the code; verify AI use is understood |
| Project owner (Balaji) | Owns and must explain the code; approves scope and architecture gates |
| Delivery agents | Need unambiguous, traceable requirements |

## 4. Glossary

| Term | Meaning |
|------|---------|
| Movie | A film record: title, release year, synopsis, runtime, genres, optional artwork |
| Person | An individual who can be cast or crew |
| Credit | A link of one person to one movie with a role (and optional character name) |
| Role | `ACTOR`, `DIRECTOR`, `WRITER`, `PRODUCER` (default vocabulary, still open for change) |
| Cast / creators | Credits with role `ACTOR` / any other role |
| Artwork | One poster image per movie |

## 5. User journeys

| ID | Journey |
|----|---------|
| UJ-01 | Browse the movie list (paged, sorted, filtered), see posters or placeholders, open a movie detail page |
| UJ-02 | Add a movie, upload its artwork with visible progress, see it in the list |
| UJ-03 | Edit a movie's details or replace its artwork; delete a movie after confirmation |
| UJ-04 | Browse people, open a person page showing their filmography, add/edit/delete a person |
| UJ-05 | On a movie page, add or remove cast and creators (credits) |
| UJ-06 | Type in the search box and see grouped movie and people results with loading, empty and error states |
| UJ-07 | Attempt to delete a person who still has credits and receive an explanation of what blocks it |

## 6. Functional requirements

Priority: **M** = must (assessment core), **S** = should (approved extra, decision D3).

### 6.1 Movies

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-MOV-01 | List movies with artwork thumbnail (placeholder if none), title, year, genres | M | A-F1 |
| FR-MOV-02 | Movie detail page shows all fields, full artwork and its credits grouped by role | S | A-F7, D3 |
| FR-MOV-03 | Create a movie | M | A-F2 |
| FR-MOV-04 | Update a movie | M | A-F2 |
| FR-MOV-05 | Delete a movie (confirmation required); removes its credits and artwork | M | A-F2 |
| FR-MOV-06 | Movie list supports pagination, sorting (title, year; asc/desc) and filter by genre and year (single year or range) | S | A-F7, D3 |

### 6.2 Artwork

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-ART-01 | Upload artwork for a movie (at creation or later) | M | A-F3 |
| FR-ART-02 | Validate artwork: JPEG/PNG/WebP by content, 1 byte to 5 MB, readable image; reject others with specific messages | M | A-F3 |
| FR-ART-03 | Show upload progress as a percentage, allow cancel, show success or failure, allow retry | M | A-N2 |
| FR-ART-04 | Replace or remove existing artwork; old file is deleted | M | A-F3 |
| FR-ART-05 | Serve a thumbnail for lists and the original for the detail page; show a placeholder when absent | S | A-F7, D3 |

### 6.3 People

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-PPL-01 | List people (paged, sortable by name) | M | A-F4 |
| FR-PPL-02 | Create a person | M | A-F5 |
| FR-PPL-03 | Update a person | M | A-F5 |
| FR-PPL-04 | Delete a person; **blocked** if the person has credits, with a message naming the blocking movies (D8) | M | A-F5, D8 |
| FR-PPL-05 | Person detail page shows fields and filmography grouped by role | S | A-F4, D3 |

### 6.4 Credits

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-CRD-01 | Add a credit (person, role, optional character name for actors) to a movie | S | A-F4, A-F7, D3 |
| FR-CRD-02 | Update a credit's role or character name | S | D3 |
| FR-CRD-03 | Remove a credit | S | D3 |
| FR-CRD-04 | Movie page lists cast and creators with person names resolved from the people service | S | A-F4 |

Credits are what make "list cast and creators" meaningful, so they are required in practice for A-F4 even though labelled S for build order.

### 6.5 Search

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-SRC-01 | One search box returns movies (title, genre text, synopsis) and people (name, bio) in separate groups; case-insensitive substring match (D9) | M | A-F6 |
| FR-SRC-02 | Search input is debounced; a loading indicator shows while pending; stale responses never overwrite newer ones | M | A-N5 |
| FR-SRC-03 | Distinct empty-result, error (with retry) and "query too short" states | M | A-N5 |
| FR-SRC-04 | Each group in results is paged ("show more") | S | D3 |

### 6.6 Platform and data

| ID | Requirement | Pri | Source |
|----|-------------|-----|--------|
| FR-EXT-01 | Seed script loads demo movies and people (with credits and artwork) | S | A-F7, D3 |
| FR-EXT-02 | Consistent error display: field-level validation, operation-level toasts/banners, retry where safe | M | A-N4 |
| FR-EXT-03 | Consistent loading feedback (skeletons for lists, pending states for buttons) | M | A-N3, A-N4 |

FR-EXT-02/03 are the concrete forms of A-F7 and A-N3/A-N4. Extras approved in D3 map as: detail pages → FR-MOV-02, FR-PPL-05; credits → FR-CRD-*; pagination/sort/filter → FR-MOV-06, FR-PPL-01; seed/thumbnails → FR-EXT-01, FR-ART-05.

## 7. Data and validation rules

| Field | Rule | Message key |
|-------|------|-------------|
| Movie.title | Required, trimmed, 1–200 chars | `movie.title.invalid` |
| Movie.releaseYear | Integer 1888 to current year + 5 | `movie.year.invalid` |
| Movie.runtimeMinutes | Integer 1–1000; optional | `movie.runtime.invalid` |
| Movie.synopsis | Optional, ≤ 2000 chars | `movie.synopsis.too_long` |
| Movie.genres | 0–5 values from the fixed genre list (Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Fantasy, Horror, Mystery, Romance, Sci-Fi, Thriller, War, Western); no duplicates | `movie.genres.invalid` |
| Movie uniqueness | Same title (case-insensitive) and year not allowed | `movie.duplicate` |
| Person.name | Required, trimmed, 1–200 chars | `person.name.invalid` |
| Person.birthDate | Optional, not in the future, not before 1850-01-01 | `person.birthdate.invalid` |
| Person.bio | Optional, ≤ 2000 chars | `person.bio.too_long` |
| Credit | Unique per (movie, person, role); person must exist; characterName only for ACTOR, ≤ 200 chars | `credit.duplicate`, `credit.person.not_found`, `credit.character.invalid` |
| Artwork | Detected content type in {image/jpeg, image/png, image/webp}; 1 B ≤ size ≤ 5 MiB; dimensions ≤ 6000×6000; file name never used for storage | `artwork.type.unsupported`, `artwork.too_large`, `artwork.dimensions.too_large`, `artwork.empty`, `artwork.corrupt` |
| Paging | `page ≥ 0`, `size` 1–100, default 20 | `paging.invalid` |
| Sorting | Sort field must be one allowed for the list | `sort.invalid` |
| Year filter | `yearFrom`/`yearTo` within 1888 to current year + 5, and `yearFrom ≤ yearTo` | `filter.year.invalid` |
| Credit role | Must be ACTOR, DIRECTOR, WRITER or PRODUCER | `credit.role.invalid` |
| Identifier | Must be a well-formed UUID | `id.invalid` |
| Search query | Trimmed; min 2 chars, max 100; `%`, `_` and `\` treated literally | `search.query.invalid` |

Genre list, year bounds and limits are proposed defaults and are listed as assumptions in section 11.

## 8. Business rules

| ID | Rule |
|----|------|
| BR-01 | A movie exists independently of artwork and credits. |
| BR-02 | A movie has at most one artwork; uploading again replaces it and removes the old file. |
| BR-03 | Deleting a movie deletes its credits and artwork. |
| BR-04 | A person can have many credits across movies, including several roles in one movie. |
| BR-05 | A person with at least one credit cannot be deleted (D8); the error lists up to 10 blocking movies. |
| BR-06 | Credits reference people by identifier only; people data is owned by the people service, credits by the movie service (D7). |
| BR-07 | If a credit references a person that no longer resolves, the movie page shows "Unknown person" rather than failing. |
| BR-08 | Artwork type is determined from file content, not file name or the client's declared type. |
| BR-09 | Search covers movies and people and never exposes internal identifiers other than public IDs. |
| BR-10 | All lists are paged; no endpoint returns unbounded data. |
| BR-11 | Failed or cancelled uploads leave no partial file and no changed movie artwork. |
| BR-12 | Concurrent edits: last write wins. This is a known limitation (no versioning). |
| BR-13 | The check-then-delete in BR-05 is not atomic across services; a credit added between check and delete can orphan a credit, handled by BR-07. Documented limitation. |

## 9. Edge cases

| ID | Case | Expected behaviour |
|----|------|--------------------|
| EC-01 | Empty catalog | Friendly empty state with an "Add movie" action |
| EC-02 | Movie without artwork | Placeholder image with accessible text |
| EC-03 | Very long title or synopsis | Truncated in lists, wrapped in detail; layout not broken |
| EC-04 | Title with HTML, emoji, accents, leading/trailing space | Stored trimmed; rendered as text only (no HTML injection) |
| EC-05 | Upload of 0-byte, 5 MiB + 1 byte, renamed `.exe` as `.jpg`, truncated or corrupt image, GIF, SVG | Rejected with a specific message; nothing stored |
| EC-06 | Upload of exactly 5 MiB valid JPEG | Accepted |
| EC-07 | Network lost mid-upload | Upload fails with retry; no partial file remains |
| EC-08 | User cancels upload | Upload aborts, previous artwork unchanged |
| EC-09 | Two uploads at once for the same movie | Last completed wins; one file remains |
| EC-10 | Update or delete of an ID that no longer exists | `NOT_FOUND` error, shown clearly; list refreshes |
| EC-11 | Page beyond the last page | Empty page with correct total; UI returns to last valid page |
| EC-12 | Search with `%`, `_`, quotes, 1 character, 101 characters, whitespace only | Literal matching or clear validation message; no server error |
| EC-13 | Rapid typing in search | Only the latest query's result is shown |
| EC-14 | Movie with 200+ credits | Credits paged or capped with a clear indicator |
| EC-15 | People service unavailable while viewing a movie | Movie data shown; credit names show a degraded state with retry |
| EC-16 | Movie service unavailable during person delete | Delete refused with a "cannot verify credits" error (fail closed) |
| EC-17 | Duplicate movie (same title and year) | Rejected with `movie.duplicate` |
| EC-18 | Same person added twice with the same role | Rejected with `credit.duplicate` |
| EC-19 | Release year at both bounds (1888, current year + 5) and just outside | Inclusive bounds enforced |
| EC-20 | Double-click on submit | Only one request effective; button disabled while pending |

## 10. Acceptance criteria

Format: Given / When / Then. Each AC has a stable ID `AC-<requirement>-<n>` and is later bound to tests.

| ID | Acceptance criterion |
|----|----------------------|
| AC-FR-MOV-01-1 | Given 25 movies, when the list opens, then 20 are shown with thumbnail or placeholder, title, year and genres, and a skeleton is visible while loading |
| AC-FR-MOV-01-2 | Given no movies, when the list opens, then the empty state is shown |
| AC-FR-MOV-02-1 | Given a movie with credits, when its page opens, then fields, full artwork and credits grouped by role are shown |
| AC-FR-MOV-03-1 | Given valid input, when the movie is created, then it appears in the list and detail page |
| AC-FR-MOV-03-2 | Given each invalid field value in section 7, when submitted, then the matching field error is shown and nothing is saved |
| AC-FR-MOV-03-3 | Given a duplicate title and year, when created, then `movie.duplicate` is shown |
| AC-FR-MOV-04-1 | Given an existing movie, when updated with valid data, then the new values are persisted and shown |
| AC-FR-MOV-04-2 | Given a deleted movie, when updated, then a not-found message is shown |
| AC-FR-MOV-05-1 | Given a movie with credits and artwork, when deleted after confirmation, then movie, credits and artwork file are gone |
| AC-FR-MOV-05-2 | Given the confirmation dialog, when cancelled, then nothing changes |
| AC-FR-MOV-06-1 | Given mixed data, when filtering by genre and year range, then only matching movies are returned with correct totals |
| AC-FR-MOV-06-2 | Given a sort by year descending, when applied, then order is correct and stable across pages |
| AC-FR-ART-01-1 | Given a valid JPEG, PNG or WebP ≤ 5 MiB, when uploaded, then the movie shows it |
| AC-FR-ART-02-1 | Given each rejected sample in EC-05, when uploaded, then the specific error is shown and no file is stored |
| AC-FR-ART-02-2 | Given a file renamed to `.jpg` whose content is not an image, when uploaded, then it is rejected (BR-08) |
| AC-FR-ART-03-1 | Given a large upload, when it is in progress, then a percentage bar updates and a cancel control is available |
| AC-FR-ART-03-2 | Given a failed upload, when it ends, then an error and a retry action are shown |
| AC-FR-ART-04-1 | Given a movie with artwork, when new artwork is uploaded, then the old file is deleted |
| AC-FR-ART-04-2 | Given a movie with artwork, when it is removed, then the placeholder is shown and the file is deleted |
| AC-FR-ART-05-1 | Given artwork, when the list loads, then a thumbnail is used; the detail page uses the original |
| AC-FR-PPL-01-1 | Given 25 people, when the list opens, then 20 are shown sorted by name with paging |
| AC-FR-PPL-02-1 | Given valid input, when a person is created, then they appear in the list |
| AC-FR-PPL-02-2 | Given invalid fields from section 7, when submitted, then field errors show and nothing is saved |
| AC-FR-PPL-03-1 | Given an existing person, when updated, then new values are shown everywhere, including movie credits |
| AC-FR-PPL-04-1 | Given a person without credits, when deleted after confirmation, then they are removed |
| AC-FR-PPL-04-2 | Given a person with credits, when delete is attempted, then it is refused and blocking movies are listed (up to 10) |
| AC-FR-PPL-04-3 | Given the movie service is unreachable, when delete is attempted, then it is refused (EC-16) |
| AC-FR-PPL-05-1 | Given a person with credits, when their page opens, then the filmography is grouped by role |
| AC-FR-CRD-01-1 | Given a movie and a person, when a credit is added, then it appears on the movie and person pages |
| AC-FR-CRD-01-2 | Given a non-existent person or duplicate credit, when added, then `credit.person.not_found` / `credit.duplicate` is shown |
| AC-FR-CRD-02-1 | Given an existing credit, when role or character is changed, then it is persisted |
| AC-FR-CRD-03-1 | Given an existing credit, when removed, then it disappears from both pages |
| AC-FR-CRD-04-1 | Given credits, when the movie page loads, then names are resolved in one batched call, and an unresolved person shows "Unknown person" (BR-07) |
| AC-FR-SRC-01-1 | Given matching data, when "nol" is searched, then movies and people containing "nol" in any case appear in separate groups |
| AC-FR-SRC-01-2 | Given `%` or `_` in the query, when searched, then they match literally |
| AC-FR-SRC-02-1 | Given rapid typing, when the user stops for the debounce interval, then exactly one request is sent and a loading indicator is visible |
| AC-FR-SRC-02-2 | Given two overlapping searches, when the older returns last, then it is ignored |
| AC-FR-SRC-03-1 | Given no matches, when searched, then an empty-result message including the query is shown |
| AC-FR-SRC-03-2 | Given a backend failure, when searched, then an error with retry is shown |
| AC-FR-SRC-03-3 | Given a 1-character or whitespace-only query, when typed, then a guidance message is shown and no request is sent |
| AC-FR-SRC-04-1 | Given more than one page of results in a group, when "show more" is used, then the next page is appended |
| AC-FR-EXT-01-1 | Given a clean setup, when the seed script runs, then demo movies, people, credits and artwork appear; running it twice creates no duplicates |
| AC-FR-EXT-02-1 | Given any failed mutation, when it fails, then the user sees a human-readable message and the form keeps its input |
| AC-FR-EXT-03-1 | Given any pending list, search, update or upload, then a visible pending indicator is shown and submit controls are disabled |

Cross-cutting acceptance criteria for NFRs are in `NFR.md`.

## 11. Assumptions and open items for approval

1. The fixed genre list in section 7, the year bounds and the character limits are **proposed defaults** from the assessment's silence; change them here if you prefer.
2. `Movie` uniqueness is (title, year); the assessment does not require it.
3. Credit role vocabulary is the four values in the glossary.
4. One artwork per movie; no gallery.
5. Concurrency is last-write-wins (BR-12), and cross-service delete checks are not atomic (BR-13); both are documented limitations rather than solved problems.
6. No authentication means anyone with network access can modify data. This is stated plainly in the README.

## 12. Approval

Architecture (Phase 3) and story generation (Phase 4) will not start until this BRD, `NFR.md` and the traceability matrix are approved.

| Approver | Decision | Date |
|----------|----------|------|
| Project owner | ☑ Approved (via approval prompt) | 2026-10-21 |
