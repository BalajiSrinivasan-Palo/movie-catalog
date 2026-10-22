# Phase 5: Story Refinement Record

Date: 2026-10-21 · Method: an independent review agent (not the author of the stories) read the BRD, NFR, architecture, data model, contracts and all 36 stories and judged each against six criteria: clarity, rules and edge cases, testability, dependencies and architectural consistency, security/accessibility/error handling, and size. Its findings were checked against the files before being applied. The review is model-generated and has not been reviewed by a human yet.

## Result

About 45 findings (12 cross-cutting, the rest per story; 8 high, about 20 medium). All were accepted except where listed below. Traceability stays intact: `python3 -I scripts/check_traceability.py` reports no gaps after the changes, and the contracts re-validate (protoc compiles; the GraphQL schema builds).

## Changes that alter contracts or the approved BRD/NFR (need your sign-off, see D14)

| Ref | Change | Reason |
|-----|--------|--------|
| C-1 | GraphQL: `health` query and `ServiceHealth` type | S-004 needed it; it did not exist |
| C-2 | GraphQL `people(query:)` and proto `ListPeopleRequest.query` | The credit person picker could not search 1,000 people |
| C-3 | GraphQL `Credit.personStatus` (RESOLVED, NOT_FOUND, UNAVAILABLE) | "Unknown person" (BR-07) and a degraded lookup (EC-15) were indistinguishable |
| C-4 | GraphQL `searchMovies`, `searchPeople` | "Show more" for one group refetched the other group |
| C-5 | BRD section 7 keys: `sort.invalid`, `filter.year.invalid`, `credit.role.invalid`, `id.invalid` | Validation errors had no defined keys |
| C-6 | NFR performance dataset: measured on a generated 1,000 movies and 1,000 people, not on the 30-movie demo seed | The two documents disagreed |
| C-7 | Proto comments: ordering of credit lists, `BatchGetPeople` limit error, `creditCount` metadata | Ordering and the "N more" message were undefined |
| C-8 | Person deletion guarded by flag `catalog.people.delete-enabled` until S-405 | Unprotected deletion should never be reachable |

## Changes within approved scope

- AC ownership moved so each AC belongs to the story that can actually pass it: `AC-FR-PPL-02-1` to S-102; `AC-FR-MOV-03-1` to S-203; `AC-FR-CRD-01-1` to S-404; `AC-FR-EXT-02-1` and `AC-FR-EXT-03-1` to S-603 (verified across every mutation and list).
- Dependencies added: S-304 on S-305; S-403 on S-404; S-404 on S-402 and S-305; S-502 on S-203 and S-103; S-602, S-603 on S-206; S-601 into S-603, S-604, S-605, S-607.
- Priorities: S-404 and S-604 raised to Must (S-403 and S-607 depend on them).
- New acceptance criteria for: `scripts/dev.sh`; message-key catalogue; verify.sh frontend wiring; touch targets; `grpc.health` and message-size limits; dependency-resolution blocker; HTTP-client check between services; named git and Claude hooks and a regex secret scan; streaming upload under a 128 MB heap; header-only dimension check; upload protocol violations; Liquibase changesets for `artwork` and `credit`; `tmp/` sweep; 304 handling; failed upload after create; layout-shift test; credit ordering and truncation; DataLoader for credits; seed implemented as a Node script with idempotency rules; axe on dialogs and error states.
- Stale statuses fixed: BRD, NFR and data model marked approved; schema header no longer says CR-01 is pending.

## Not applied, with reasons

- **Splitting S-003 and S-301:** you approved the backlog without splitting. Both stay Large and are marked to be implemented and reviewed in two parts each.
- **Boilerplate text in some stories (X10):** low value; stories gain concrete edge-case IDs as plans are written in Phase 6.
- **Search failing as a whole when one backend fails:** kept as designed and documented (architecture 4.6).

## Residual risks

- The two Large stories remain the riskiest to review.
- The review was a single model pass; a second human read before implementation is advisable, especially for S-301 and S-303 (upload security).
