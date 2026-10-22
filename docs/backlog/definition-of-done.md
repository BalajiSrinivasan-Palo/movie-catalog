# Definition of Done (applies to every story)

A story is **Done** only when all of the following hold, each backed by command output saved or linked in the story's review record.

1. Every acceptance criterion in the story (BRD `AC-*` and story `S-xxx.ACn`) is met and mapped to at least one named test.
2. `./scripts/verify.sh` passes: ktlint, detekt, `svelte-check`, ESLint, Prettier, unit and integration tests, coverage thresholds, secret scan.
3. Contract tests pass for any gRPC or GraphQL contract the story touches; contract files changed only with an ADR or approval note when the change is breaking.
4. Validation rules the story touches have both a passing-input and a failing-input test, including boundaries (NFR-TEST-01, NFR-SEC-01).
5. Previously completed stories' tests still pass (regression).
6. UI work: keyboard operable, labelled, no serious axe violations, usable at 360 px, loading and error states present (NFR-UX-03/04/06, NFR-A11Y-01).
7. Documentation updated: README or architecture notes if behaviour changed, Liquibase changesets for schema changes, the story's "why" note.
8. An independent reviewer agent (not the implementer) has compared the diff to the story and architecture and any required changes are fixed and re-verified.
9. No new dependency without a justification line (NFR-MNT-03); no secrets; user text is never rendered as HTML (NFR-SEC-03).
10. The story status is changed to Done only by the reviewer step, citing the evidence.

## Cross-cutting requirements verified by every story

NFR-MNT-01, NFR-MNT-02, NFR-MNT-03, NFR-TEST-01, NFR-TEST-02, NFR-TEST-05, NFR-SEC-01, NFR-SEC-03, NFR-SEC-04, NFR-UX-04, NFR-UX-06, NFR-REL-01, NFR-PROC-01, NFR-PROC-03
