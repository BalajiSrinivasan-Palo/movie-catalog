---
name: quality-gates
description: Mandatory quality gates and how to report results honestly. Use before declaring any work complete.
---
# Quality gates
Run `./scripts/verify.sh`. It runs only what exists and fails if a required tool is missing; it never masks failures.
- Required: ktlint, detekt, backend tests and Kover threshold (80% on domain/service), svelte-check, ESLint, Prettier, Vitest, secret scan, traceability check.
- Report each command with its exit status and the saved output path. If a gate could not run (for example Maven Central unreachable), say "not run" and why; never write "passed".
- Coverage is a proxy: every BRD acceptance criterion must also map to a named test.
