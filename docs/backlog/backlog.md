# Prioritised Backlog

Status: **DRAFT, awaiting approval** · generated from `docs/backlog/stories/` · order is a valid dependency order; within it Must precedes Should precedes Could.

| Order | Story | Title | Epic | Pri | Size | Depends on | Requirements |
|------:|-------|-------|------|-----|------|-----------|--------------|
| 1 | [S-001](stories/S-001.md) | Repository and build scaffold | E00 | Must | M | - | NFR-TECH-02, NFR-ARCH-01, NFR-ARCH-02, NFR-MNT-03, NFR-PROC-01, NFR-REL-04, NFR-ARCH-04 |
| 2 | [S-002](stories/S-002.md) | Quality gates, hooks and CI | E00 | Must | M | S-001 | NFR-MNT-01, NFR-TEST-02, NFR-TEST-05, NFR-SEC-07, NFR-SEC-09, NFR-PROC-04 |
| 3 | [S-003](stories/S-003.md) | Frontend scaffold, design system and shared feedback components | E00 | Must | L | - | NFR-TECH-01, NFR-UX-01, NFR-UX-03, NFR-UX-04, NFR-UX-06, NFR-A11Y-01, NFR-SEC-03, NFR-MNT-01, FR-EXT-02, FR-EXT-03 |
| 4 | [S-004](stories/S-004.md) | Walking skeleton: frontend to gateway to both services | E00 | Must | M | S-001, S-003 | NFR-ARCH-02, NFR-ARCH-03, NFR-REL-01, NFR-REL-02, NFR-OBS-01, NFR-OBS-02, NFR-SEC-05, NFR-SEC-06, NFR-TEST-03, NFR-OPS-02 |
| 5 | [S-101](stories/S-101.md) | Create person | E01 | Must | M | S-004 | FR-PPL-02, NFR-SEC-01, NFR-REL-04 |
| 6 | [S-102](stories/S-102.md) | List people with paging and sorting | E01 | Must | M | S-101 | FR-PPL-01, NFR-UX-03, NFR-PERF-01 |
| 7 | [S-103](stories/S-103.md) | Get and update person | E01 | Must | S | S-102 | FR-PPL-03 |
| 8 | [S-104](stories/S-104.md) | Delete person (no credits) | E01 | Must | S | S-103 | FR-PPL-04 |
| 9 | [S-201](stories/S-201.md) | Create movie | E02 | Must | M | S-004 | FR-MOV-03, NFR-SEC-01, NFR-SEC-04, NFR-REL-04 |
| 10 | [S-202](stories/S-202.md) | List movies (paging, empty state, placeholders) | E02 | Must | M | S-201 | FR-MOV-01, NFR-UX-03, NFR-PERF-01, BR-10 |
| 11 | [S-203](stories/S-203.md) | Movie detail page (fields) | E02 | Must | S | S-202 | FR-MOV-02 |
| 12 | [S-204](stories/S-204.md) | Update movie | E02 | Must | S | S-203 | FR-MOV-04 |
| 13 | [S-205](stories/S-205.md) | Delete movie with confirmation | E02 | Must | S | S-203 | FR-MOV-05 |
| 14 | [S-301](stories/S-301.md) | Artwork validation and storage in movie-service | E03 | Must | L | S-201 | FR-ART-01, FR-ART-02, NFR-SEC-02, NFR-ARCH-05, NFR-REL-03 |
| 15 | [S-302](stories/S-302.md) | Thumbnails and artwork download in movie-service | E03 | Must | M | S-301 | FR-ART-05, NFR-PERF-05 |
| 16 | [S-303](stories/S-303.md) | Gateway multipart upload and artwork GET endpoints | E03 | Must | M | S-301, S-302, S-004 | FR-ART-01, NFR-ARCH-03, NFR-ARCH-05, NFR-SEC-02, NFR-TEST-03 |
| 17 | [S-305](stories/S-305.md) | Show artwork: thumbnails in lists, originals on detail | E03 | Must | S | S-302, S-303, S-202, S-203 | FR-ART-05, NFR-PERF-05 |
| 18 | [S-304](stories/S-304.md) | Frontend artwork uploader with progress, cancel and retry | E03 | Must | M | S-303, S-203, S-305 | FR-ART-01, FR-ART-03, NFR-UX-02, NFR-A11Y-01 |
| 19 | [S-306](stories/S-306.md) | Replace and remove artwork | E03 | Must | M | S-304, S-305 | FR-ART-04 |
| 20 | [S-401](stories/S-401.md) | Add credit to a movie | E04 | Must | M | S-203, S-103 | FR-CRD-01, NFR-TEST-03 |
| 21 | [S-402](stories/S-402.md) | Movie page credits with batched name resolution | E04 | Must | M | S-401, S-305 | FR-CRD-04, FR-MOV-02, FR-PPL-03, NFR-PERF-04 |
| 22 | [S-404](stories/S-404.md) | Person filmography page | E04 | Must | M | S-401, S-103, S-402, S-305 | FR-PPL-05 |
| 23 | [S-405](stories/S-405.md) | Block deleting a person who has credits | E04 | Must | M | S-104, S-401 | FR-PPL-04, NFR-ARCH-02, NFR-REL-02 |
| 24 | [S-406](stories/S-406.md) | Movie deletion cleans up credits and artwork | E02 | Must | S | S-205, S-306, S-401 | FR-MOV-05, NFR-REL-03 |
| 25 | [S-501](stories/S-501.md) | Search backend and gateway merge | E05 | Must | M | S-201, S-101 | FR-SRC-01, NFR-SEC-04, NFR-PERF-02, NFR-TEST-03 |
| 26 | [S-502](stories/S-502.md) | Search UI with debounce and full state handling | E05 | Must | M | S-501, S-003, S-203, S-103 | FR-SRC-02, FR-SRC-03, NFR-UX-05 |
| 27 | [S-206](stories/S-206.md) | Sort and filter movies | E02 | Should | M | S-202 | FR-MOV-06, NFR-SEC-01 |
| 28 | [S-403](stories/S-403.md) | Update and remove credit | E04 | Should | S | S-402, S-404 | FR-CRD-02, FR-CRD-03 |
| 29 | [S-503](stories/S-503.md) | Show more results per group | E05 | Should | S | S-502 | FR-SRC-04 |
| 30 | [S-701](stories/S-701.md) | Visual refresh: poster grid, cinematic detail, drag-and-drop upload | E07 | Should | L | S-206, S-304, S-305, S-306, S-402, S-404, S-502 | NFR-UX-01, NFR-UX-02, NFR-A11Y-01, FR-ART-01, FR-ART-03, FR-MOV-06 |
| 31 | [S-702](stories/S-702.md) | Search scope menu, year picker and multi-select genres | E07 | Should | L | S-701, S-206, S-501, S-502 | FR-MOV-06, FR-SRC-02, NFR-UX-01, NFR-A11Y-01, NFR-SEC-01 |
| 32 | [S-602](stories/S-602.md) | Responsive and accessibility verification | E06 | Must | M | S-402, S-405, S-503, S-206, S-403, S-404, S-701, S-702 | NFR-UX-01, NFR-A11Y-01 |
| 33 | [S-601](stories/S-601.md) | Seed data script | E06 | Should | M | S-306, S-401 | FR-EXT-01, NFR-REL-05 |
| 34 | [S-603](stories/S-603.md) | End-to-end journeys | E06 | Must | M | S-306, S-405, S-503, S-206, S-601 | NFR-TEST-04, NFR-ARCH-03 |
| 35 | [S-604](stories/S-604.md) | Performance and security verification | E06 | Must | M | S-603, S-601 | NFR-PERF-01, NFR-PERF-02, NFR-PERF-03, NFR-PERF-04, NFR-PERF-05, NFR-SEC-02, NFR-SEC-09 |
| 36 | [S-605](stories/S-605.md) | README, AI-usage statement and explanation notes | E06 | Must | M | S-603, S-601 | NFR-OPS-03, NFR-PROC-02, NFR-PROC-03, NFR-SEC-08, NFR-SEC-10, NFR-OPS-02 |
| 37 | [S-607](stories/S-607.md) | Final audit and clean-setup validation | E06 | Must | M | S-602, S-603, S-604, S-605, S-601 | NFR-OPS-01, NFR-OPS-02, NFR-PROC-04, NFR-TEST-05 |
| 38 | [S-606](stories/S-606.md) | Optional Docker Compose setup | E06 | Could | S | S-603 | NFR-OPS-04 |

Stories: 38 (Small 10, Medium 24, Large 4). Large stories (S-003, S-301, S-701, S-702) are flagged for a split decision in Phase 5.
Sizing guide: S = under half a day, M = about one day, L = more than a day and a candidate for splitting.
