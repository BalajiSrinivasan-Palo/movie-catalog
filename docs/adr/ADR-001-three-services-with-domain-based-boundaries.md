# ADR-001: Three services with domain-based boundaries

**Status:** Accepted (D1)

## Context

The assessment requires at least two Spring Boot services communicating over gRPC, and GraphQL to the frontend. It names no domain split.

## Decision

`movie-service` (movies, credits, artwork), `people-service` (people), `api-gateway` (GraphQL, orchestration).

## Why

Splitting by domain gives each backend a clear data owner and makes gRPC calls necessary rather than decorative (gateway→services, people→movie for the delete rule). A thin gateway keeps the GraphQL concern separate from business rules.

## Consequences and trade-offs

Three deployables to run instead of two; cross-service rules need explicit handling (ADR-003). A two-service design with GraphQL inside `catalog-service` was offered and not chosen.
