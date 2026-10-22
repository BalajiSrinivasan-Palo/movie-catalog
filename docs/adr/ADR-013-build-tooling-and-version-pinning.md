# ADR-013: Build tooling and version pinning

**Status:** Proposed; blocked by CR-03

## Context

The build must be reproducible, and versions must be verified rather than assumed.

## Decision

Gradle Kotlin DSL multi-module with a version catalog (`libs.versions.toml`) and the Gradle wrapper; pnpm with a lockfile for the frontend. Exact versions are chosen and recorded here during Phase 7 after resolving them from the registries.

## Why

Single source of versions and lockfiles give reproducible clean builds for Phase 10.

## Consequences and trade-offs

In this sandbox Maven Central and the Gradle plugin portal return 403, so versions cannot be resolved yet. Until then no backend build result can be claimed.
