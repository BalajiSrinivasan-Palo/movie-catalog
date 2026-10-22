#!/usr/bin/env bash
# S-002 AC3 and AC4: negative tests that prove the gates really fail. Run by hand or in CI.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$root"
fail=0
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

# 1. Secret scan must fail on a planted fake key, and pass on clean text.
printf 'aws_key = "%s"\n' "AKIA$(printf 'ABCDEFGHIJKLMNOP')" > "$tmp/planted.txt"
if python3 -I scripts/secret_scan.py "$tmp/planted.txt" >/dev/null; then echo "FAIL: planted key not detected"; fail=1; else echo "ok: planted key detected"; fi
echo 'nothing secret here' > "$tmp/clean.txt"
if python3 -I scripts/secret_scan.py "$tmp/clean.txt" >/dev/null; then echo "ok: clean file passes"; else echo "FAIL: false positive"; fail=1; fi
printf -- '-----BEGIN RSA PRIVATE KEY-----\n' > "$tmp/key.pem.txt" # secret-scan: allow
if python3 -I scripts/secret_scan.py "$tmp/key.pem.txt" >/dev/null; then echo "FAIL: private key not detected"; fail=1; else echo "ok: private key detected"; fi

# 2. Traceability check must fail when a story file is removed from a copy of the docs.
cp -R docs "$tmp/docs"; cp -R scripts "$tmp/scripts"
rm "$tmp/docs/backlog/stories/S-101.md"
if (cd "$tmp" && python3 -I scripts/check_traceability.py >/dev/null 2>&1); then echo "FAIL: missing story not detected"; fail=1; else echo "ok: missing story detected"; fi

exit $fail
