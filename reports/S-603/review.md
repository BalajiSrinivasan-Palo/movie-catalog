# S-603 review: real-stack test fixes and `e2e.sh` cleanup (2026-10-22)

Reviewed by: reviewer (2026-10-22)

Implemented by: frontend-implementer (`reports/S-603/implementation.md`). The reviewer did not plan or implement this change (CLAUDE.md rule 7).

## Basis
There is no plan file (`docs/plans/S-603.md` does not exist). The owner approved the scope in chat. The approved scope, used here as the plan, is the four items in "The 10 real-stack failures" and "Script bug found" in `reports/final-checks-20261022.md`. This review covers only that fix. It does not assess S-603 as a whole. `README.md` (edited by the owner at 17:30) is not part of this review.

## Files read
- `docs/backlog/stories/S-603.md`
- `reports/final-checks-20261022.md`
- `reports/S-603/implementation.md`
- Changed files: `frontend/e2e/stack/feedback.e2e.ts`, `frontend/e2e/stack/journeys.e2e.ts`, `scripts/e2e.sh`
- Context: `scripts/dev.sh`, `scripts/hooks/guard-bash.sh`, `.claude/settings.json`, `frontend/playwright.config.ts`, `frontend/src/lib/components/Button.svelte`, `frontend/src/lib/views/MovieDetailView.svelte`, `PersonDetailView.svelte`, `CreditsSection.svelte`, and the label strings rendered under `frontend/src`
- Logs: `reports/e2e-stack-20261022-1634.log` (failing run), `reports/e2e-stack-20261022-1645-rerun.log` (rerun), and `reports/S-603/bash-n-e2e.log`, `pnpm-check.log`, `pnpm-lint.log`, `playwright-mocked.log`, `playwright-stack-list.log`, `tsc-stack-e2e.log`

## What I ran (read-only)
- `bash -n scripts/e2e.sh`: exit 0.
- `grep` over `scripts/e2e.sh` and the e2e and src label strings.

I did not run `make e2e`, `scripts/e2e.sh` or any Playwright suite, and I did not start or stop any stack.

## How the old code was checked
Nothing is committed, so there is no diff. I rebuilt the old lines from the code excerpts in the failing log (`e2e-stack-20261022-1634.log`, old lines 140-143, 153-156 and 170-175) and compared them with the current files. The only assertion lines that changed are the ones the scope names:
- old line 142 became line 155
- old line 155 became lines 169-170
- old line 172 became line 187
- `journeys.e2e.ts:37`
- the add-credit slow check, line 195 (the recorded deviation)

## Acceptance criteria (for this fix only)
| AC | Status | Evidence |
|----|--------|----------|
| S-603.AC1: UJ-01, UJ-02 (with upload progress), UJ-06 and UJ-07 pass against the full stack started by one command | **Met for this run** | `e2e-stack-20261022-1645-rerun.log`: `make e2e` ran 21 tests, 21 passed. This includes `journeys.e2e.ts:14` UJ-01/02, which had stopped at the credit step in the 16:34 run, plus `:87` UJ-02 bad image, `:102` UJ-06 and `:121` UJ-07. |
| S-603.AC2: the browser requests only `/graphql` and `/artwork/*` | **Met where asserted** | `journeys.e2e.ts:84` `recorder.offenders(baseURL!)` equals `[]`, passing in the rerun. It is asserted in the UJ-01/02 journey only. |
| S-603.AC3: pending state, disabled submit, readable error and kept input for every mutation, list, search and upload | **Partly. Not assessed as a whole.** | Passing in the rerun: create, update and delete for movie and person (`feedback.e2e.ts:141`, `:159`), add credit (`:175`), lists (`:205`), search (`:229`), upload error (`:249`). The table has no case for update credit or delete credit, and the add-credit slow round does not check `aria-disabled` or the single request. Both gaps existed before this fix and are outside its scope. |
| AC-FR-EXT-02-1, AC-FR-EXT-03-1 | **Not assessed** | Outside this fix's scope. |

## Scope items (stand-in for plan tasks)
| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | Slow-response tests: keep every pending assertion, allow the button to be gone after release, keep "exactly one request" | **Done** | `feedback.e2e.ts:149-153` are unchanged: `aria-busy`, `aria-disabled`, "Working…" and two forced clicks. `:155` now calls `expectSettled`. `:156` keeps `hits() == 1`. The rerun lines 157-167 show all 6 tests passing. |
| 2 | Delete server-error tests: assert that the dialog closed | **Done** | `feedback.e2e.ts:169`, gated by `Case.inDialog` (`:56`, `:124`, `:136`). The other four cases keep the not-busy check at `:170`. I confirmed in the app code that a failed delete closes the dialog: `MovieDetailView.svelte:54` and `PersonDetailView.svelte:40` set `confirming = false`. |
| 3 | `getByLabel('Person', { exact: true })`, and check the other labels | **Done** | `feedback.e2e.ts:187`, `journeys.e2e.ts:37`. My own check of the label strings in `frontend/src` matches the implementer's survey. "Find a person" and "Find a person by name" are the only labels that collide with "Person". "Role" has a second instance only in the edit-credit form, which no test opens. "Name contains" exists only on the people list page. The note that `exact: true` must not be used on "Title *" or "Name *" is correct. |
| 4 | `e2e.sh`: own process groups, stop by group, no `\|\| true`, no new `rm -rf` | **Done, with finding R1** | `set -m` is at `:27`. `stop_group` is at `:15-19`. `cleanup` is at `:20-25`. There is no `\|\| true`, and the only recursive delete is the existing one on `$DATA_ROOT` (`:24`). The new `rm -f` at `:23` causes R1. |
| D | Deviation: the add-credit slow round also uses `expectSettled` (`feedback.e2e.ts:195`) | **Necessary and sound** | On success, `CreditsSection.svelte:79` sets `adding = false`, so the form and its "Add to movie" button unmount. The old `not.toHaveAttribute` would have failed with "element(s) not found", the same failure as item 1. The run at 16:34 never reached that line because it stopped at the label. The change is recorded in `implementation.md` and does not drop any assertion. |

## Were the assertions weakened? (regression analysis)
- **Pending state disappears:** lines 149-151 still fail.
- **A second click sends a second request:** this still fails. Line 156 is unchanged. The route handler counts every matching request at interception. The forced clicks happen before `release()`, and `expectSettled` needs at least one real round trip to the backend after that. So a second request is counted before line 156 runs. The only gap is a theoretical race, if the duplicate request reaches the route handler after the first one has completed. I do not count it as a finding.
- **A delete error shows no readable message:** this still fails at line 166, `getByRole('alert')` filtered by `READABLE`. The no-"boom/Exception/stack" check at `:171` is unchanged.
- **Delete error leaves the dialog stuck open:** the new check fails on this, so the test now catches a regression it could not catch before.
- **Pending never clears after release (a stuck spinner):** `expectSettled` times out after 15 s and fails.
- **What it can no longer catch:** a slow create or update that, after release, removes the button for the wrong reason, such as a crash or a blank page. The old check would have caught that only by accident, since it also failed on the correct outcome. The scope accepts this. Finding O1 suggests how to close it.

## Can `expectSettled` pass vacuously?
`expectSettled` (`feedback.e2e.ts:38-40`) is `expect(button.and(page.locator('[aria-busy="true"]'))).toHaveCount(0)`. Here is why it cannot pass too early or for the wrong reason:
- It is called only after `fault.release()`, and only after line 149 has proved that a busy matching button exists. While the request is held, the intersection has one element, so the check cannot pass before release.
- The locator is re-evaluated on every poll. If the button is gone, the count is 0. If it is still there, `Button.svelte:39` renders `aria-busy` as `true` or `false`, and only `"true"` matches.
- The button names stay stable while pending: "Create movie Working…" still matches `/Create movie/`. So the locator does not lose the busy button just because its text changed. If it did lose it, the check would pass falsely.
- In the add-credit slow round (`:193-195`), line 193 likewise proves the busy state before release.

The only "wrong moment" is any time after release, and that is the intended contract.

## Findings

### R1 (required): `scripts/e2e.sh:23` and `:33` overwrite and then delete the dev stack's pid file, and a running dev stack makes the e2e run silently use the dev data
- **Where:** `scripts/e2e.sh:33` `echo "$BACKEND_PID" > data/dev.pids` (this line existed before the fix), and `:23` `rm -f "$root/data/dev.pids"` (new). `scripts/dev.sh:15,42,46,48` use the same file to hold the Gradle and pnpm-dev process-group ids, and `make dev-stop` reads it.
- **Failing scenario:** the owner's dev stack is running (`make dev`), which is the normal state on this machine, and someone runs `make e2e`.
  1. Line 33 truncates `data/dev.pids` to the e2e pid.
  2. The e2e Gradle bootRun cannot bind 8080-8082 or 9091-9092.
  3. The health loop at `:34-37` still succeeds, because the dev services answer.
  4. `pnpm seed` and every stack test run against the owner's dev databases, not the fresh `$DATA_ROOT`. The run can report a pass that breaks the "fresh data directory per run" expectation of S-603, and it writes test data into the owner's data.
  5. On exit, `cleanup` deletes `data/dev.pids`. `make dev-stop` then prints "nothing to stop", and the dev JVMs and Vite are orphaned. This is the "left running" problem the fix was meant to remove, now hitting the dev stack.

  The 16:45 rerun avoided all of this only because the main session stopped the dev stack by hand first (`final-checks-20261022.md`). The script does not enforce that precondition.
- **Fix suggestion (the owner decides):**
  - Fail fast before starting anything if any of 4173, 8080-8082 or 9091-9092 is already in use, or if `data/dev.pids` exists, with a message like "stop the dev stack first (make dev-stop)".
  - Use a separate pid file, for example `data/e2e.pids`, so the dev stack's file is never touched.

  Both are small, plain changes and need no new `rm -rf` or `|| true`.

### O1 (optional): `feedback.e2e.ts:155` does not check the outcome after release
`expectSettled` accepts "gone" without checking why. For create and update you could add `await expect(page).toHaveURL(...)` (the saved page), and for delete `toHaveURL(/\/movies$|\/people$/)`. That turns "the button is gone" into "the save succeeded". This is not required by the scope.

### O2 (optional): `scripts/e2e.sh:20-25` deletes the data folder without waiting for the processes to stop
`stop_group` sends SIGTERM to both groups, and `cleanup` removes `$DATA_ROOT` straight away. The JVMs may still be closing their H2 files, so the delete can race with that and leave a partly rebuilt temp folder under `$TMPDIR`. A short bounded wait, for example polling `kill -0` for a few seconds, would close the race. Nothing in the logs shows this happening.

### O3 (optional): `scripts/e2e.sh:42` does not save the preview server's pid in `data/dev.pids`
`implementation.md` says the pid file lets `dev.sh --stop` clean up "if `e2e.sh` is killed hard". That is true only for the backend. After a SIGKILL of the script, `pnpm preview` would keep port 4173. If R1 is fixed with a separate pid file, write both pids to it.

### O4 (optional): background jobs under `set -m` keep the terminal as stdin (`scripts/e2e.sh:31-32`, `:42`)
Under job control, bash does not redirect a background job's stdin to `/dev/null`. A background process group that reads the terminal gets SIGTTIN and stops. For example, a CLI-shortcut handler would do this if the preview server enabled one. This was not seen: the rerun passed, and `dev.sh` does the same thing. Adding `</dev/null` to both background commands would remove the risk at no cost.

### O5 (optional, cosmetic): bash prints job-control notices during cleanup
The rerun log ends with `scripts/e2e.sh: line 20: 22074 Terminated: 15 pnpm preview ...`. That is bash's job notice for the expected stop, not an error. A reader could still mistake it for one.

### Notes, not findings
- **`set -m` placement:** it comes after `trap cleanup EXIT` (`:26-27`), and the trap still applies. Foreground commands such as `pnpm build`, `pnpm seed` and `pnpm exec playwright` each get their own process group and the terminal. The script's exit status is still Playwright's, because the EXIT trap does not call `exit`. The `exit 1` paths at `:36`, `:41` and `:45` run in the main shell, so the trap fires. The rerun shows the normal path working: 21 passed, the preview was stopped at cleanup line 20, and there was no `make` error. The `exit 1` paths and a Ctrl-C during the health loop have not been run.
- **Guard hook:** `guard-bash.sh` checks commands Claude runs, not the contents of scripts. The script has no `|| true`, and the only recursive delete is the existing one on the `mktemp` directory.
- **The old `dev.sh --stop` call:** the implementer's second cause is right. The old cleanup ran `scripts/dev.sh --stop` by a relative path after `cd frontend`, so it could not have worked.

## Evidence
| Claim | Evidence | Status |
|-------|----------|--------|
| 10 tests failed before the fix, for the reasons stated | `e2e-stack-20261022-1634.log` lines 157-177 and 180-420 | Verified |
| 21 of 21 pass after the fix on a fresh real stack | `e2e-stack-20261022-1645-rerun.log` lines 155-179 | Verified |
| `pnpm check` and `pnpm lint` pass, with e2e files included in lint | `reports/S-603/pnpm-check.log`, `pnpm-lint.log`, both exit 0 | Verified |
| Mocked suite passes: 68 passed, 21 skipped | `reports/S-603/playwright-mocked.log` | Verified |
| The changed stack files type-check | `reports/S-603/tsc-stack-e2e.log`, exit 0 | Verified |
| `bash -n scripts/e2e.sh` | `reports/S-603/bash-n-e2e.log`, and my own rerun, both exit 0 | Verified |
| The preview server was stopped by cleanup | Rerun log line 180 (the "Terminated" notice from `cleanup`) | Verified |
| Nothing was left on 8080-8082, 9091-9092 or 4173, and `data/dev.pids` was removed | Only a statement in `final-checks-20261022.md`. No saved command output, such as `lsof`, is cited. | **Not verified by a saved log**, though the statement is plausible |
| Frontend unit tests (Vitest) | Not run, because the pnpm minimum release age blocks them. Stated in both reports. | Not run |

## Status lines
- There is no plan file for this fix, so no plan status line is set.
- No story status is changed. S-603 has other acceptance criteria that this fix does not cover (see the AC table above).

## Verdict
**CHANGES REQUIRED.** The three test fixes and the deviation are correct and narrowly scoped. They do not weaken any assertion beyond what the scope allows, and the passing rerun backs them. One required finding remains. **R1:** `e2e.sh` overwrites and then deletes `data/dev.pids`. Combined with the missing check that the ports are free, running `make e2e` while the dev stack is up silently tests against the dev data and orphans the dev stack. The optional findings O1-O5 are for the owner to decide.
