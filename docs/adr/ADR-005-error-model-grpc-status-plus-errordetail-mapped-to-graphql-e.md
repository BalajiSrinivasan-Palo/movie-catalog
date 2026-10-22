# ADR-005: Error model: gRPC status plus ErrorDetail mapped to GraphQL errors

**Status:** Accepted

## Context

Failures must be consistent, human-readable and testable end to end (NFR-REL-01, NFR-UX-06).

## Decision

Services return standard gRPC status codes with an `ErrorDetail` message carrying a stable `code`, field violations (BRD message keys) and, for person deletion, `blocking_movies`. The gateway maps statuses to `VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, `UNAVAILABLE`, `INTERNAL` in `extensions.code`, adds `fieldErrors` and a `correlationId`. The frontend maps `messageKey` to text in one place.

## Why

Standard codes are idiomatic gRPC; a single detail type avoids per-call error types; message keys keep wording out of the backend and make validation testable.

## Consequences and trade-offs

GraphQL transports errors with HTTP 200, so clients must inspect `errors`. Contract tests must cover each mapping.
