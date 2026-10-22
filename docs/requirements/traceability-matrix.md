# Requirements Traceability Matrix (Requirements stage)

Status: **DRAFT, awaiting approval** · Version 0.1 · 2026-10-21
Later phases add columns for story IDs, test IDs and evidence. Until then, "Planned verification" is intent, not evidence.

## 1. Assessment to requirements

| Assessment ID | Assessment text (short) | Requirement IDs | Decision refs |
|---------------|-------------------------|-----------------|---------------|
| A-F1 | List movies with artwork | FR-MOV-01, FR-ART-05 | D3 |
| A-F2 | Add, delete, update movies | FR-MOV-03, FR-MOV-04, FR-MOV-05 | |
| A-F3 | Upload movie artwork | FR-ART-01, FR-ART-02, FR-ART-03, FR-ART-04 | D5 |
| A-F4 | List cast and creators | FR-PPL-01, FR-PPL-05, FR-CRD-04 | D6, D7 |
| A-F5 | Add, delete, update cast and creators | FR-PPL-02, FR-PPL-03, FR-PPL-04 | D6, D8 |
| A-F6 | Search movies, cast and creators | FR-SRC-01, FR-SRC-02, FR-SRC-03 | D9 |
| A-F7 | Any other functionality | FR-EXT-01, FR-EXT-02, FR-EXT-03, FR-MOV-02, FR-MOV-06, FR-CRD-01, FR-CRD-02, FR-CRD-03, FR-SRC-04 | D3 |
| A-T1 | Svelte in TypeScript | NFR-TECH-01 | |
| A-T2 | Spring Boot in Kotlin | NFR-TECH-02 | |
| A-T3 | At least 2 Spring Boot services | NFR-ARCH-01 | D1 |
| A-T4 | Service-to-service via gRPC | NFR-ARCH-02, NFR-ARCH-05 | D7, D8 |
| A-T5 | Frontend to backend via GraphQL | NFR-ARCH-03 | D1 |
| A-T6 | Unit tests incl. edge cases | NFR-TEST-01, NFR-TEST-02 | D4 |
| A-N1 | Responsive | NFR-UX-01 | |
| A-N2 | Upload progress feedback | FR-ART-03, NFR-UX-02 | |
| A-N3 | Listing progress feedback | FR-EXT-03, NFR-UX-03 | |
| A-N4 | Updating progress feedback | FR-EXT-02, FR-EXT-03, NFR-UX-04 | |
| A-N5 | Searching progress feedback | FR-SRC-02, FR-SRC-03, NFR-UX-05 | |
| A-D1 | Git repository | NFR-PROC-01 | |
| A-D2 | README with setup, run, AI usage | NFR-OPS-03, NFR-PROC-02 | |
| A-D3 | Walkthrough session | NFR-PROC-03, NFR-MNT-02 | |
| A-E1 | Explain generated code | NFR-MNT-02, NFR-PROC-03 | |
| A-E2 | Own the code; AI as aid | NFR-MNT-02, NFR-PROC-02, NFR-PROC-04 | |

## 2. Requirements to sources, acceptance criteria and planned verification

| Requirement | Source | Acceptance criteria | Planned verification |
|-------------|--------|---------------------|----------------------|
| FR-MOV-01 | A-F1 | AC-FR-MOV-01-1, -2 | Unit, component, E2E |
| FR-MOV-02 | A-F7 | AC-FR-MOV-02-1 | Component, E2E |
| FR-MOV-03 | A-F2 | AC-FR-MOV-03-1, -2, -3 | Unit, integration, E2E |
| FR-MOV-04 | A-F2 | AC-FR-MOV-04-1, -2 | Unit, integration |
| FR-MOV-05 | A-F2 | AC-FR-MOV-05-1, -2 | Unit, integration, E2E |
| FR-MOV-06 | A-F7 | AC-FR-MOV-06-1, -2 | Unit, integration |
| FR-ART-01 | A-F3 | AC-FR-ART-01-1 | Unit, gRPC streaming test, E2E |
| FR-ART-02 | A-F3 | AC-FR-ART-02-1, -2 | Unit (upload test matrix) |
| FR-ART-03 | A-N2 | AC-FR-ART-03-1, -2 | Component, E2E |
| FR-ART-04 | A-F3 | AC-FR-ART-04-1, -2 | Unit, integration |
| FR-ART-05 | A-F7 | AC-FR-ART-05-1 | Unit, integration |
| FR-PPL-01 | A-F4 | AC-FR-PPL-01-1 | Unit, component |
| FR-PPL-02 | A-F5 | AC-FR-PPL-02-1, -2 | Unit, integration |
| FR-PPL-03 | A-F5 | AC-FR-PPL-03-1 | Unit, integration |
| FR-PPL-04 | A-F5 | AC-FR-PPL-04-1, -2, -3 | Unit, gRPC contract, E2E (UJ-07) |
| FR-PPL-05 | A-F4 | AC-FR-PPL-05-1 | Component |
| FR-CRD-01 | A-F7 | AC-FR-CRD-01-1, -2 | Unit, integration |
| FR-CRD-02 | A-F7 | AC-FR-CRD-02-1 | Unit |
| FR-CRD-03 | A-F7 | AC-FR-CRD-03-1 | Unit |
| FR-CRD-04 | A-F4 | AC-FR-CRD-04-1 | gRPC contract, call-count test |
| FR-SRC-01 | A-F6 | AC-FR-SRC-01-1, -2 | Unit, integration, E2E |
| FR-SRC-02 | A-N5 | AC-FR-SRC-02-1, -2 | Component with fake timers |
| FR-SRC-03 | A-N5 | AC-FR-SRC-03-1, -2, -3 | Component, E2E |
| FR-SRC-04 | A-F7 | AC-FR-SRC-04-1 | Component |
| FR-EXT-01 | A-F7 | AC-FR-EXT-01-1 | Run-twice test |
| FR-EXT-02 | A-F7, A-N4 | AC-FR-EXT-02-1 | Component |
| FR-EXT-03 | A-N3, A-N4 | AC-FR-EXT-03-1 | Component |

NFR verification methods are listed in `NFR.md` under each requirement.

## 3. Business rules and edge cases to requirements

| Item | Linked requirements |
|------|---------------------|
| BR-01, BR-02, BR-03 | FR-MOV-05, FR-ART-04 |
| BR-04 | FR-CRD-01 |
| BR-05, BR-13 | FR-PPL-04 |
| BR-06, BR-07 | FR-CRD-04, NFR-ARCH-04 |
| BR-08 | FR-ART-02 |
| BR-09, BR-10 | FR-SRC-01, FR-MOV-06, FR-PPL-01 |
| BR-11 | FR-ART-03, NFR-REL-03 |
| BR-12 | Documented limitation |
| EC-01, EC-02 | FR-MOV-01, FR-ART-05 |
| EC-03, EC-04 | FR-MOV-01, FR-MOV-03, NFR-SEC-03 |
| EC-05 to EC-09 | FR-ART-02, FR-ART-03, FR-ART-04, NFR-SEC-02 |
| EC-10, EC-11 | FR-MOV-04, FR-MOV-05, FR-PPL-03, FR-MOV-06 |
| EC-12, EC-13 | FR-SRC-01, FR-SRC-02, NFR-SEC-04 |
| EC-14 | FR-CRD-04 |
| EC-15, EC-16 | FR-CRD-04, FR-PPL-04, NFR-REL-02 |
| EC-17, EC-18, EC-19, EC-20 | FR-MOV-03, FR-CRD-01, FR-EXT-03 |

## 4. Requirements not derived from an assessment line

These come from the delivery brief or from proposed quality targets, not from the assessment text. They are kept visible so they are not mistaken for assessment requirements, and the owner can remove any of them at approval.

| Requirement | Origin |
|-------------|--------|
| NFR-TEST-03, NFR-TEST-04, NFR-TEST-05 | Decision D4 and delivery brief |
| NFR-MNT-01, NFR-MNT-03 | Delivery brief |
| NFR-A11Y-01 | Proposed (WCAG 2.1 AA target); delivery brief asks for accessibility checks |
| NFR-PERF-01 to NFR-PERF-05 | Proposed targets; the assessment states none |
| NFR-SEC-01, -05 to -10 | Delivery brief security scope (D2 limits auth only) |
| NFR-REL-01, -04, -05 | Delivery brief (error conventions, migrations) and D5 |
| NFR-OBS-01, NFR-OBS-02 | Proposed |
| NFR-OPS-01, -02, -04 | Supports A-D2 (setup and run) and D5 |
| NFR-UX-06 | Supports A-N4 |

## 5. Coverage check

Checked by an inline script on 2026-10-21 (not yet a repository script). Results: all 23 assessment IDs appear in section 1; all 27 functional requirements appear in section 2 and have at least one acceptance criterion; all 44 acceptance criteria are listed against their requirement and none reference an undefined ID; all 52 NFRs are defined in `NFR.md` with a verification method, and those not tied to an assessment line are listed in section 4. Story and test coverage will be re-checked in Phase 4 and Phase 10.

## 6. Story coverage (Phase 4)

Requirement-to-story and AC-to-story mapping is generated into `docs/backlog/coverage-report.md` by `scripts/check_traceability.py`, which exits non-zero when any requirement, acceptance criterion, edge case or business rule has no owning story. Run on 2026-10-21 after backlog creation: no gaps; a negative test (deleting story S-405) made it fail as expected. Test and evidence columns are added in Phases 8–10.
