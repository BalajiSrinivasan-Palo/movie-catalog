# Checks run through the device shell, 2026-10-22

Run after merging the cloud snapshot into this folder (owner's own fixes kept: grpc 1.83.1, logback-classic, kotlin-test-junit5).

| Check | Result |
|---|---|
| `python3 -I scripts/check_traceability.py` | OK: no gaps |
| `python3 -I scripts/secret_scan.py` | OK: no secrets found |
| `scripts/check_no_http_between_services.sh` | OK |
| `scripts/tests/test_gates.sh` | ok |
| frontend `pnpm check` | 0 errors, 0 warnings |
| frontend `pnpm lint` | pass |
| frontend `pnpm test:coverage` | 22 files, 209 tests passed, thresholds met |
| frontend `pnpm build` | built |

Frontend checks ran in a Linux copy of this folder's `frontend/` (the shell here is Linux; the folder's own `node_modules` was not touched).

NOT RUN here, and still needed from the owner's Mac terminal: every Gradle step (this shell has Java 11 and no route to Maven), `:movie-service:streamingTest`, Playwright (browser download blocked), `scripts/e2e.sh`, `scripts/perf/perf.mjs`.
