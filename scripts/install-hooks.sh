#!/usr/bin/env bash
# Points git at the versioned hooks, so every clone gets the same pre-commit gate.
set -eu
cd "$(git rev-parse --show-toplevel)"
chmod +x scripts/hooks/pre-commit scripts/verify.sh
git config core.hooksPath scripts/hooks
echo "git hooks installed (core.hooksPath=scripts/hooks)"
