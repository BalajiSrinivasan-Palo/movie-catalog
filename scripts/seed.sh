#!/usr/bin/env bash
# Loads demo data into a running stack. Safe to run twice. Usage: scripts/seed.sh [gateway-url]
set -euo pipefail
cd "$(dirname "$0")/.."
GATEWAY_URL="${1:-${GATEWAY_URL:-http://localhost:8080}}" exec node scripts/seed/seed.mjs
