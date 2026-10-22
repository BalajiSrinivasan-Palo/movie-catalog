#!/usr/bin/env bash
# Starts the three services and the Vite dev server (S-004 AC6).
#   scripts/dev.sh          start everything in the background; logs go to data/logs/
#   scripts/dev.sh --check  wait for all four health endpoints; exit non-zero on timeout
#   scripts/dev.sh --stop   stop what this script started
# Ports and directories come from the environment (names as in docs/architecture/architecture.md section 10).
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"
export GATEWAY_PORT="${GATEWAY_PORT:-8080}"
export MOVIE_SERVICE_GRPC_PORT="${MOVIE_SERVICE_GRPC_PORT:-9091}" PEOPLE_SERVICE_GRPC_PORT="${PEOPLE_SERVICE_GRPC_PORT:-9092}"
export MOVIE_HTTP_PORT="${MOVIE_HTTP_PORT:-8081}" PEOPLE_HTTP_PORT="${PEOPLE_HTTP_PORT:-8082}"
export MOVIE_DB_PATH="${MOVIE_DB_PATH:-$root/data/movie-db}" PEOPLE_DB_PATH="${PEOPLE_DB_PATH:-$root/data/people-db}"
export ARTWORK_DIR="${ARTWORK_DIR:-$root/data/artwork}"
export FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-http://localhost:5173}"
logs="$root/data/logs"; pids="$root/data/dev.pids"

# Vite listens on localhost, which is IPv6 (::1) on macOS, so the frontend is checked by name, not 127.0.0.1.
health_urls="http://127.0.0.1:$MOVIE_HTTP_PORT/actuator/health http://127.0.0.1:$PEOPLE_HTTP_PORT/actuator/health http://127.0.0.1:$GATEWAY_PORT/actuator/health http://localhost:5173/"

case "${1:-start}" in
  --check)
    timeout="${DEV_CHECK_TIMEOUT:-120}"; deadline=$(( $(date +%s) + timeout ))
    for url in $health_urls; do
      until curl -fsS -o /dev/null "$url" 2>/dev/null; do
        if [ "$(date +%s)" -ge "$deadline" ]; then echo "TIMEOUT waiting for $url"; exit 1; fi
        sleep 2
      done
      echo "up: $url"
    done
    echo "all four are up"; exit 0 ;;
  --stop)
    [ -f "$pids" ] || { echo "nothing to stop"; exit 0; }
    # Each saved pid leads its own process group (see start), so killing the group also ends the children:
    # the bootRun JVMs under Gradle and the Vite node process under pnpm. The plain kill covers older pid files.
    # A process that is already gone is not an error here (no `set -e` in this script).
    while read -r pid; do
      if ! kill -- "-$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; fi
    done < "$pids"
    rm -f "$pids"; echo "stopped"; exit 0 ;;
  start|"")
    [ -x ./gradlew ] || { echo "gradlew missing; see README"; exit 1; }
    mkdir -p "$logs" "$ARTWORK_DIR"; : > "$pids"
    set -m # job control: each background job gets its own process group, so --stop can end it with its children
    # One Gradle invocation runs the three long-lived bootRun tasks side by side.
    ./gradlew :movie-service:bootRun :people-service:bootRun :api-gateway:bootRun --parallel --console=plain \
      > "$logs/backend.log" 2>&1 & echo $! >> "$pids"
    # --strictPort: fail instead of moving to 5174 when 5173 is taken (CORS allows FRONTEND_ORIGIN only).
    (cd frontend && exec pnpm dev --strictPort > "$logs/frontend.log" 2>&1) & echo $! >> "$pids"
    echo "started; logs in data/logs/. Run: scripts/dev.sh --check   (stop: scripts/dev.sh --stop)" ;;
  *) echo "usage: $0 [--check|--stop]"; exit 2 ;;
esac
