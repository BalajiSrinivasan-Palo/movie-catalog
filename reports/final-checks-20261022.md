# Final checks, 2026-10-22 16:29 to 16:40

Run in the owner's Terminal panel at the owner's request ("run ./gradlew build, scripts/verify.sh and make e2e … tell me what fails").

| Check | Result | Log |
|-------|--------|-----|
| `./gradlew build` | **PASS** (exit 0, BUILD SUCCESSFUL in 11 s; most tasks up to date since the earlier passing runs) | `gradle-build-20261022-1629.log` |
| Backend tests in that build | **171 tests, 0 failed, 0 skipped**: service-support 8, movie-service 76 + 5 streaming, people-service 27, api-gateway 55 | `gradle-build-test-summary-20261022-1629.txt` |
| `scripts/verify.sh` | **FAIL**: every gate passes except `pnpm test:coverage` | `verify-20261022-163357.log`, `verify-console-20261022-1633.log` |
| · traceability, secret scan, gate self-tests, no HTTP between services | pass | same |
| · `./gradlew build` | pass | same |
| · `pnpm check`, `pnpm lint`, `pnpm build` | pass | same |
| · `pnpm test:coverage` | **fail, environment**: 269 of 269 tests stop with "expected any number of assertion, but got none" (two Vitest copies in `node_modules`); pnpm still refuses 17 lockfile entries younger than its minimum release age. Not a test result. | same |
| `make e2e` (real stack, fresh data, seeded) | **10 failed, 11 passed** (1.9 min) | `e2e-stack-20261022-1634.log` |

This was the first run ever of the real-stack browser journeys (S-603).

## The 10 real-stack failures: all in the tests, not shown in the app

1. **"slow response shows pending and blocks a second submit"** (create movie, create person, update movie, update person, delete movie, delete person: 6 tests, `feedback.e2e.ts:128`). Every pending-state check passes (`aria-busy`, `aria-disabled`, "Working…", the extra clicks swallowed). Only the last line fails: after the held request is released, `expect(button).not.toHaveAttribute('aria-busy', 'true')` waits for a button that no longer exists, because a successful create or update navigates to the saved page and a delete closes its dialog. Playwright reports "element(s) not found". Fix in the test: check that exactly one request reached the server, then wait for the button to be gone or no longer busy.
2. **"delete movie / delete person: server error shows a readable message and keeps the input"** (2 tests, `feedback.e2e.ts:146`). The readable error is shown (line 153 passes). The last check fails the same way: on a failed delete the app closes the confirmation dialog by design (`MovieDetailView.confirmDelete`, `PersonDetailView.confirmDelete`), so the dialog's Delete button is gone. Fix in the test: for the delete cases, check that the dialog closed instead.
3. **"add credit: error keeps the chosen person and role; slow shows pending"** and **"UJ-01/02: create a movie, add a credit, upload artwork with visible progress"** (2 tests, `feedback.e2e.ts:172`, `journeys.e2e.ts:37`). `page.getByLabel('Person')` matches by substring, so it also matches the form "Find a person" and the field "Find a person by name". While the people search is still loading, those two are the only matches, so Playwright stops with a strict-mode error instead of waiting for the "Person" dropdown. The page snapshot at the failure shows the search had not answered yet. These labels predate 2026-10-22. Fix in the test: `getByLabel('Person', { exact: true })`.

Because the UJ-01/02 journey stopped at the credit step, the real-stack artwork upload with visible progress was not reached. It is unverified until the rerun.

## Script bug found: `scripts/e2e.sh` leaves its stack running
After the run, the three test services (started 16:34) and the preview server on port 4173 (16:35) were still running, holding the ports. The cleanup had already deleted their temporary data folder. `e2e.sh` starts Gradle and `pnpm preview` without their own process groups, so its `dev.sh --stop` kills only the Gradle client. This is the same fault fixed in `dev.sh` at 14:38. The processes were stopped by PID (16236, 16266, 16267, 16268, 16447), and the owner's stack was restarted with `make dev`: all four parts up. Fix: start the backend and the preview server in `e2e.sh` with `set -m` and stop them by process group, as `dev.sh` now does.

## Not run
- Frontend unit tests (the environment block above).
- `make quality` (opt-in, unverified) and Docker (never built), as the README says.

## Rerun after the fixes, 2026-10-22 16:45
- The `frontend-implementer` agent fixed the three test problems and the `e2e.sh` cleanup (test and script code only; see `reports/S-603/implementation.md`). Its frontend checks passed: `pnpm check`, `pnpm lint`, and the mocked Playwright suite with 68 passed.
- The dev stack was stopped first, and the ports were confirmed free, so the run used a fresh backend and data.
- `make e2e`: **21 passed, 0 failed** (Playwright 9.8 s). This includes UJ-01/02: create a movie, add a credit, and upload artwork with visible progress against the real stack. Log: `e2e-stack-20261022-1645-rerun.log`.
- After the run, nothing was left listening on 4173, 8080-8082 or 9091-9092, and `data/dev.pids` was removed, so the cleanup fix works. The owner's dev stack was then restarted with `make dev`.
- Still not run: the frontend unit tests (Vitest), blocked by the lockfile until pnpm accepts the new packages. The S-603 fixes have not yet been reviewed by the `reviewer` agent.
