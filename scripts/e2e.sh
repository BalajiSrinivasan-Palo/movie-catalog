#!/usr/bin/env bash
# Runs the real-stack browser journeys (S-603): fresh data directory, start the stack, seed, run Playwright, stop.
#   scripts/e2e.sh            run everything
#   scripts/e2e.sh <args>     extra arguments go to `playwright test` (for example: -g "UJ-07")
# Needs: JDK 21, Node and pnpm, `pnpm install` done in frontend/, and `pnpm exec playwright install chromium`.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"
export DATA_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/movie-catalog-e2e.XXXXXX")"
export MOVIE_DB_PATH="$DATA_ROOT/movie-db" PEOPLE_DB_PATH="$DATA_ROOT/people-db" ARTWORK_DIR="$DATA_ROOT/artwork"
export FRONTEND_ORIGIN="http://localhost:4173"
mkdir -p "$ARTWORK_DIR"
# The backend and the preview server each lead their own process group (set -m below), so killing the group
# also ends the children: the bootRun JVMs under Gradle and the Vite node process under pnpm. The plain kill
# is the fallback when there is no such group. A process that is already gone is not an error (no `set -e`).
stop_group() {
  [ -n "$1" ] || return 0
  if ! kill -- "-$1" 2>/dev/null; then kill "$1" 2>/dev/null; fi
  return 0
}
cleanup() {
  stop_group "${PREVIEW_PID:-}"
  stop_group "${BACKEND_PID:-}"
  rm -f "$root/data/dev.pids"
  rm -rf "$DATA_ROOT"
}
trap cleanup EXIT
set -m # job control: each background job gets its own process group, as in scripts/dev.sh

# Backend only: dev.sh also starts the Vite dev server, which this run does not need, so start Gradle directly.
mkdir -p data/logs
./gradlew :movie-service:bootRun :people-service:bootRun :api-gateway:bootRun --parallel --console=plain \
  > data/logs/e2e-backend.log 2>&1 &
BACKEND_PID=$!; echo "$BACKEND_PID" > data/dev.pids
for url in http://127.0.0.1:8081/actuator/health http://127.0.0.1:8082/actuator/health http://127.0.0.1:8080/actuator/health; do
  for _ in $(seq 1 90); do curl -fsS -o /dev/null "$url" 2>/dev/null && break; sleep 2; done
  curl -fsS -o /dev/null "$url" || { echo "backend did not become healthy: $url (see data/logs/e2e-backend.log)"; exit 1; }
done
echo "backend up"

cd frontend
pnpm build >/dev/null || exit 1
pnpm preview --port 4173 >/dev/null 2>&1 & PREVIEW_PID=$!
for _ in $(seq 1 30); do curl -fsS -o /dev/null http://localhost:4173/ 2>/dev/null && break; sleep 1; done

pnpm seed || { echo "seed failed"; exit 1; }
pnpm exec playwright test --project=stack "$@"
