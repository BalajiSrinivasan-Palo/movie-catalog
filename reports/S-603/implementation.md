# S-603 real-stack browser journeys: test and script fixes

Implemented by: frontend-implementer (2026-10-22)

## Basis
No plan file exists for this fix (`docs/plans/S-603.md` is absent). The scope came from the main session, which relayed the owner's chat approval of 2026-10-22 ("have the frontend-implementer agent fix the three e2e test problems and the e2e.sh cleanup"). The failure analysis is `reports/final-checks-20261022.md`; the failing run is `reports/e2e-stack-20261022-1634.log`. No app code under `frontend/src`, no backend code and no other tests were changed. Nothing is committed.

## Files changed
- `frontend/e2e/stack/feedback.e2e.ts`
- `frontend/e2e/stack/journeys.e2e.ts`
- `scripts/e2e.sh`

## Changes
1. **Slow-response cases (`feedback.e2e.ts`, test at line 141, check at line 155).** New helper `expectSettled(page, button)` (line 38) waits up to 15 s until no element matches both the button locator and `[aria-busy="true"]`, so the button either went away or stopped being busy. After the held request is released, a successful create or update navigates away and a successful delete closes its dialog, so the old `not.toHaveAttribute('aria-busy', 'true')` waited for a button that no longer exists. All pending-state checks (`aria-busy`, `aria-disabled`, "Working…", the two swallowed clicks) and `expect(fault.hits()).toBe(1)` are unchanged.
2. **Server-error cases for delete movie and delete person (`feedback.e2e.ts`, line 169).** New optional `Case.inDialog` flag, set on the two delete cases. For those cases the last check is now `expect(page.getByRole('dialog')).toHaveCount(0)`, because a failed delete closes the dialog by design (`MovieDetailView.confirmDelete` and `PersonDetailView.confirmDelete` set `confirming = false`). The other four cases keep the existing not-busy check. The readable-message, kept-input and no-"boom/Exception/stack" checks are unchanged.
3. **Ambiguous label (`feedback.e2e.ts` line 187, `journeys.e2e.ts` line 37).** `getByLabel('Person')` became `getByLabel('Person', { exact: true })`. A Playwright probe confirmed that the substring form matches the "Find a person" form and the "Find a person by name" field, and that the exact form matches only the Person select.
   Every other `getByLabel` in `frontend/e2e/stack/` was checked against the labels the app renders on the same page:
   - `'Title'`, `'Release year'` (movie form): one match each. Release year is a `TextField`, not the `YearPicker` with its "Choose release year" button.
   - `'Name'`, `'Biography'` (person form): one match each.
   - `'Synopsis'`, `'Drama'`, `'Character (optional)'`, `'Find a person by name'`, `/Image file/`: one match each.
   - `'Role'` (credit form): one match. The second "Role" label only appears while a credit is being edited, and no test edits a credit.
   None of these needed a change. Note: `exact: true` must not be used on `'Title'` or `'Name'`. Those required fields have the label text "Title *" and "Name *", and the probe showed that `getByLabel('Title', { exact: true })` finds 0 elements.
4. **`scripts/e2e.sh` cleanup.** `set -m` now comes right after the trap, so the Gradle backend and `pnpm preview` each start in their own process group, as in `scripts/dev.sh`. The new `stop_group PID` tries `kill -- -PID` and falls back to `kill PID`. `cleanup` stops the preview server and the backend this way, removes `data/dev.pids` (the file the script writes, which `dev.sh --stop` used to remove), then removes `$DATA_ROOT` as before. No `|| true` was added, and the only `rm -rf` is still the one for `$DATA_ROOT`. The script still writes `data/dev.pids`, so `scripts/dev.sh --stop` can stop a stack left behind if `e2e.sh` is killed hard. The rest of the script is unchanged.
   A second cause was found: the old cleanup ran `scripts/dev.sh --stop` by a relative path after the script had already `cd`'d into `frontend/`. On a normal run that call failed silently, so not even the Gradle client was stopped through it. The new cleanup uses the saved PIDs and does not depend on the working directory.

## Deviations from the given scope
- **One extra line in the add-credit test (`feedback.e2e.ts` line 195).** The slow round's check after release (`not.toHaveAttribute('aria-busy', 'true')`) now uses `expectSettled`. This is the same fault as item 1: on success `CreditsSection.submitAdd` sets `adding = false`, so the "Add to movie" button goes away. Without this change the test that item 3 fixes would fail on its next line. No other part of that test was changed.

## Acceptance coverage (S-603 real-stack feedback and journeys)
| What | Test |
|------|------|
| Pending state and single submit for create/update/delete movie and person | `feedback.e2e.ts` "`<case>`: slow response shows pending and blocks a second submit" |
| Readable error and kept input (dialog closed for deletes) | `feedback.e2e.ts` "`<case>`: server error shows a readable message and keeps the input" |
| Add credit: error keeps the person and role; slow shows pending | `feedback.e2e.ts` "add credit: error keeps the chosen person and role; slow shows pending" |
| UJ-01/02 create, add credit, upload with visible progress | `journeys.e2e.ts` "UJ-01/02: …" |

None of these changes has run against the real stack yet (see below).

## Commands
| Command | Exit | Log |
|---------|------|-----|
| `bash -n scripts/e2e.sh` | 0 | `reports/S-603/bash-n-e2e.log` |
| `cd frontend && pnpm check` | 0 (0 errors, 0 warnings) | `reports/S-603/pnpm-check.log` |
| `cd frontend && pnpm lint` (prettier and eslint, e2e files included) | 0 | `reports/S-603/pnpm-lint.log` |
| `pnpm exec playwright test --project=mocked` | 0 (68 passed, 21 skipped) | `reports/S-603/playwright-mocked.log` |
| `pnpm exec playwright test --project=stack --list` (loads the changed files only, runs nothing) | 0 (21 tests in 2 files) | `reports/S-603/playwright-stack-list.log` |
| `tsc --ignoreConfig --noEmit --strict … e2e/stack/*.e2e.ts` (extra; `pnpm check` only covers `src`) | 0 | `reports/S-603/tsc-stack-e2e.log` |

pnpm printed its usual "install that runs before scripts failed" warning (blocked by the lockfile's minimum release age) before each command; the commands themselves ran.

## Not run
- `pnpm test` (Vitest): not run. It cannot run on this machine today because pnpm's minimum release age blocks the lockfile (see `reports/final-checks-20261022.md`).
- `pnpm build` as its own step: not run separately. The mocked Playwright run builds the app through its `webServer` command.
- Real-stack suite (`make e2e` / `scripts/e2e.sh` / `--project=stack`): not run, as instructed. The owner's dev stack is running, and the main session will run this suite. Until then, the fixes for the 10 failures and the new `e2e.sh` cleanup are unverified. The UJ-01/02 artwork upload with visible progress also stays unverified on the real stack.

## Open
- Rerun `make e2e` and confirm 21 of 21 pass. After it exits, confirm nothing is left listening on 4173, 8080-8082 or 9091-9092 from the e2e run.
- No plan file exists to set to "Implemented, awaiting review"; this record stands in for it. Review by the `reviewer` agent is still to do.
