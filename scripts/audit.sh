#!/usr/bin/env bash
# S-002 AC5 / NFR-SEC-09: dependency vulnerability audit; output is saved under reports/audit/.
# Frontend: pnpm audit. Backend: needs the OWASP plugin (see docs/adr/ADR-011); reported as NOT RUN until added.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"
mkdir -p reports/audit; rc=0
if [ -d frontend ]; then
  (cd frontend && pnpm audit --prod 2>&1) | tee reports/audit/frontend.txt; [ "${PIPESTATUS[0]}" -eq 0 ] || rc=1
fi
if ./gradlew tasks --all 2>/dev/null | grep -q dependencyCheckAnalyze; then
  ./gradlew dependencyCheckAggregate --console=plain 2>&1 | tee reports/audit/backend.txt; [ "${PIPESTATUS[0]}" -eq 0 ] || rc=1
else
  echo "backend audit: NOT RUN (OWASP dependency-check plugin not configured)" | tee reports/audit/backend.txt
fi
exit $rc
