#!/usr/bin/env bash
# Starts the three services and the Vite dev server (S-004 AC6).
#   scripts/dev.sh          start everything in the background and show progress until all four are up
#   scripts/dev.sh --no-wait  start in the background and return at once
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

# localhost, not 127.0.0.1: Vite listens on localhost, which is IPv6 (::1) on macOS, and curl falls back to IPv4 for the services.
names=(movie-service people-service api-gateway frontend)
urls=("http://localhost:$MOVIE_HTTP_PORT/actuator/health" "http://localhost:$PEOPLE_HTTP_PORT/actuator/health" "http://localhost:$GATEWAY_PORT/actuator/health" "http://localhost:5173/")

# wait_for_stack <timeout seconds>: prints each part as it answers, a heartbeat every 10 s, and stops early
# (with the log) if the Gradle or Vite process has already exited. The pid file lists Gradle first, then Vite.
wait_for_stack() {
  local timeout="$1" started now pending still i dead_pid log last_note
  started=$(date +%s); last_note=$started; pending="0 1 2 3"
  while :; do
    still=""
    for i in $pending; do
      if curl -fsS --max-time 2 -o /dev/null "${urls[$i]}" 2>/dev/null; then
        printf '  up       %-15s %s  (%ss)\n' "${names[$i]}" "${urls[$i]}" "$(( $(date +%s) - started ))"
      else
        still="$still $i"
      fi
    done
    pending="${still# }"
    if [ -z "$pending" ]; then echo "all four are up"; return 0; fi
    now=$(date +%s)
    for i in $pending; do
      case "$i" in 3) dead_pid="$(sed -n 2p "$pids" 2>/dev/null)"; log="$logs/frontend.log" ;; *) dead_pid="$(sed -n 1p "$pids" 2>/dev/null)"; log="$logs/backend.log" ;; esac
      if [ -n "$dead_pid" ] && ! kill -0 "$dead_pid" 2>/dev/null; then
        echo "FAILED   ${names[$i]} did not start: its process has exited."
        echo "--- last lines of $log"; tail -n 20 "$log" 2>/dev/null
        return 1
      fi
    done
    if [ "$now" -ge $(( started + timeout )) ]; then
      echo "TIMEOUT after ${timeout}s, still waiting for:$(for i in $pending; do printf ' %s' "${names[$i]}"; done)"
      case " $pending " in *" 3 "*) echo "--- last lines of $logs/frontend.log"; tail -n 20 "$logs/frontend.log" 2>/dev/null ;; esac
      case " $pending " in *" 0 "* | *" 1 "* | *" 2 "*) echo "--- last lines of $logs/backend.log"; tail -n 20 "$logs/backend.log" 2>/dev/null ;; esac
      return 1
    fi
    if [ "$now" -ge $(( last_note + 10 )) ]; then
      echo "  waiting  $(for i in $pending; do printf '%s ' "${names[$i]}"; done) ($(( now - started ))s)"
      last_note=$now
    fi
    sleep 2
  done
}

case "${1:-start}" in
  --check)
    wait_for_stack "${DEV_CHECK_TIMEOUT:-120}"; exit $? ;;
  --stop)
    [ -f "$pids" ] || { echo "nothing to stop"; exit 0; }
    # Each saved pid leads its own process group (see start), so killing the group also ends the children:
    # the bootRun JVMs under Gradle and the Vite node process under pnpm. The plain kill covers older pid files.
    # A process that is already gone is not an error here (no `set -e` in this script).
    while read -r pid; do
      if ! kill -- "-$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; fi
    done < "$pids"
    rm -f "$pids"; echo "stopped"; exit 0 ;;
  start|--no-wait|"")
    [ -x ./gradlew ] || { echo "gradlew missing; see README"; exit 1; }
    [ -d frontend/node_modules ] || { echo "frontend packages are not installed; run: make setup"; exit 1; }
    mkdir -p "$logs" "$ARTWORK_DIR"; : > "$pids"
    set -m # job control: each background job gets its own process group, so --stop can end it with its children
    # One Gradle invocation runs the three long-lived bootRun tasks side by side.
    # stdin from /dev/null: a background job that reads the terminal (Vite's keyboard shortcuts) dies with EIO on some systems.
    ./gradlew :movie-service:bootRun :people-service:bootRun :api-gateway:bootRun --parallel --console=plain \
      < /dev/null > "$logs/backend.log" 2>&1 & echo $! >> "$pids"
    # --strictPort: fail instead of moving to 5174 when 5173 is taken (CORS allows FRONTEND_ORIGIN only).
    (cd frontend && exec pnpm dev --strictPort < /dev/null > "$logs/frontend.log" 2>&1) & echo $! >> "$pids"
    echo "Starting movie-service, people-service, api-gateway and the frontend (logs in data/logs/)."
    if [ "${1:-}" = "--no-wait" ]; then
      echo "started. Run: scripts/dev.sh --check   (stop: scripts/dev.sh --stop)"; exit 0
    fi
    echo "The first run downloads Gradle and the libraries and can take several minutes."
    echo "Ctrl-C stops watching only; the parts keep running (stop them with: make dev-stop)."
    if wait_for_stack "${DEV_START_TIMEOUT:-600}"; then
      echo
      echo "Ready: open http://localhost:5173   (load sample data: make seed, stop: make dev-stop)"
      exit 0
    fi
    echo "Something did not start. Fix the cause shown above, run: make dev-stop, then make dev again."
    exit 1 ;;
  *) echo "usage: $0 [--no-wait|--check|--stop]"; exit 2 ;;
esac
