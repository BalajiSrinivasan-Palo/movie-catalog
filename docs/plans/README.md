# Implementation Plans

One plan per story, written before implementation starts (`story-workflow` skill). From D26 on, the `story-planner` agent writes each plan, the implementer agents build from it, and the `reviewer` checks the result against it. Plans for Foundation stories (S-001 to S-004) exist now; every other plan is written just before its wave begins, so it reflects the code that exists then. No implementation starts without an approved plan.

Required header:

```
**Story:** `docs/backlog/stories/S-xxx.md` · **Objective:** one sentence
**Status:** Draft
**Planned by:** story-planner (YYYY-MM-DD)
**Implementers:** backend-implementer (part A), frontend-implementer (part B)
```

Status lifecycle (each step changes only this line):

| Status | Set by | Meaning |
|--------|--------|---------|
| `Draft` | `story-planner` | written, waiting for the owner |
| `Approved by owner YYYY-MM-DD` | the owner | an implementer may start |
| `Implemented, awaiting review` | the implementer | code, tests and `reports/S-xxx/implementation.md` are in place |
| `Changes required, see reports/S-xxx/review.md` | `reviewer` | back to the implementer for the findings the owner picks |
| `Done, see reports/S-xxx/review.md` | `reviewer` | APPROVED with evidence for every Definition of Done item |

Required sections: objective, preconditions and dependencies, files and modules, tasks per part with the implementer for each (frontend, backend, data, integration), the test for each acceptance criterion, contract changes, tests (unit, integration, contract, E2E), security and regression considerations, exact commands (backend ones marked "owner runs"), completion criteria, rollback, and open questions for the owner.

Evidence: command output is saved to `reports/<story>/` (the owner pastes backend output there under D15). Do not write "passed" without such a file.
