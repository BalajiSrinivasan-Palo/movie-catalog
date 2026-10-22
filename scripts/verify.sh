#!/usr/bin/env bash
# Runs every quality gate that applies. Never masks failures; a required tool that is missing is a failure.
# Gates that cannot run yet are listed as NOT RUN, and the final line says so.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"
mkdir -p "$root/reports"; log="$root/reports/verify-$(date +%Y%m%d-%H%M%S).log"; fail=0; notrun=""
run() { echo "== $*" | tee -a "$log"; "$@" >>"$log" 2>&1; rc=$?; echo "   exit=$rc" | tee -a "$log"; [ $rc -eq 0 ] || fail=1; return $rc; }
run python3 -I scripts/check_traceability.py
run python3 -I scripts/secret_scan.py
run bash scripts/tests/test_gates.sh
run bash scripts/check_no_http_between_services.sh
if [ -f gradlew ]; then
  run ./gradlew build --console=plain # ktlint, detekt and Kover join this line when S-002's plugin step lands
else
  echo "== backend: NOT RUN (no gradlew yet)" | tee -a "$log"; notrun="$notrun backend"
fi
if [ -f frontend/package.json ]; then
  if ! command -v pnpm >/dev/null; then echo "== frontend: pnpm missing" | tee -a "$log"; fail=1
  elif [ ! -d frontend/node_modules ]; then echo "== frontend: dependencies not installed (run: cd frontend && pnpm install)" | tee -a "$log"; fail=1
  else
    pushd frontend >/dev/null
    run pnpm check
    run pnpm lint
    run pnpm test:coverage
    run pnpm build
    popd >/dev/null
  fi
else
  echo "== frontend: NOT RUN (no frontend/ yet)" | tee -a "$log"; notrun="$notrun frontend"
fi
echo "log: $log"
if [ $fail -eq 0 ]; then
  [ -z "$notrun" ] && echo "VERIFY: PASS" || echo "VERIFY: PASS for the gates that ran; NOT RUN:$notrun"
else
  echo "VERIFY: FAIL"
fi
exit $fail
