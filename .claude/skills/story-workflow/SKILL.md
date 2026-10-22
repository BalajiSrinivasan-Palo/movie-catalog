---
name: story-workflow
description: The per-story delivery pipeline - story-planner writes the plan, an implementer writes the code, the reviewer checks it, the owner approves between steps. Use when starting, implementing or closing any story in docs/backlog.
---
# Story workflow

Each story goes through three agents in order. The main session coordinates: it starts each agent, shows the owner the result, and waits for the owner's decision. It does not write the plan, the code or the review itself (CLAUDE.md rule 7). Every hand-off is a file, because nothing is committed and there is no git diff to pass around.

| Step | Agent | Reads | Writes | Plan status afterwards |
|------|-------|-------|--------|------------------------|
| 1 Plan | `story-planner` | story, requirements, architecture, ADRs, the code it will change | `docs/plans/S-xxx.md` | `Draft` |
| Owner | the owner | the plan and its open questions | answers; approval | `Approved by owner YYYY-MM-DD` |
| 2 Implement | `backend-implementer` and/or `frontend-implementer`, as the plan assigns | approved plan, story | code, tests, `reports/S-xxx/*.log`, `reports/S-xxx/implementation.md` | `Implemented, awaiting review` |
| Owner (backend) | the owner | the Gradle commands in `implementation.md` | saved Gradle output in `reports/S-xxx/` (D15) | unchanged |
| 3 Review | `reviewer` (and `security-reviewer` for uploads, validation, auth, CORS or GraphQL limits) | story, plan, `implementation.md`, the changed files, the logs | `reports/S-xxx/review.md` with a verdict | `Done` or `Changes required` |
| Owner | the owner | the review findings | which findings to fix | |

## Rules
1. No plan, no code: an implementer starts only on a plan whose status is `Approved by owner …`.
2. One story at a time, in `docs/backlog/backlog.md` order. Stop if a dependency is not Done.
3. Each artefact names its author: `Planned by:`, `Implemented by:`, `Reviewed by:`. The reviewer must not be the planner or the implementer.
4. Required changes go back to step 2 with the same plan; a changed plan goes back to step 1 and the owner approves it again.
5. Only the reviewer's APPROVED verdict, with every Definition of Done item backed by saved output, sets the story to Done.
6. After Done, run `python3 -I scripts/check_traceability.py`, and update the story's "why" note and any docs the story changed.

## Starting an agent
Start each agent with the Agent tool, its name as `subagent_type`, and a prompt that gives only the story ID and the step, for example "Plan S-703" or "Review S-703". The agent reads everything else from the files.
