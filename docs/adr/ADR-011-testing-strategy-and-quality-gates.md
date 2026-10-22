# ADR-011: Testing strategy and quality gates

**Status:** Accepted (D4)

## Context

A-T6 requires unit tests with complete cases; the delivery brief adds integration, contract and E2E tests and mandatory gates.

## Decision

Pyramid: many unit tests (domain rules, validators, resolvers with fakes, Svelte components and stores with fake timers), a smaller set of integration tests (JPA with H2, in-process gRPC servers, GraphQL tester), contract tests for each gRPC service and the GraphQL schema, and four Playwright journeys (UJ-01, UJ-02, UJ-06, UJ-07) with axe checks. Gates: ktlint, detekt, `svelte-check`, ESLint, tests, coverage at least 80% on domain/service and frontend logic, secret scan, dependency audit. Gates run through `./scripts/verify.sh`, a Claude Code hook before commit, and CI.

## Why

Cheap, fast unit tests give the edge-case coverage the assessment asks for; fewer slow tests protect integration seams; scripts make gates reproducible and honest.

## Consequences and trade-offs

Backend gates cannot run in this sandbox until CR-03 is resolved. Coverage percentage is a proxy, so each BRD acceptance criterion is also mapped to a named test.
