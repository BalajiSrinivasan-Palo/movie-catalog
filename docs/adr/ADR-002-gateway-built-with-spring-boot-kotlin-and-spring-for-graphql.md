# ADR-002: Gateway built with Spring Boot, Kotlin and Spring for GraphQL

**Status:** Accepted

## Context

Frontend-to-backend traffic must be GraphQL. The gateway should be implemented in the same stack as the other services.

## Decision

Use Spring for GraphQL with schema-first `schema.graphqls`, resolvers calling gRPC stubs, and `DataFetcherExceptionResolver` for error translation. Business validation is not duplicated in the gateway.

## Why

Schema-first keeps the contract reviewable as a plain file. Spring for GraphQL integrates with Boot, testing (`GraphQlTester`) and security hooks. Staying in Spring avoids a second runtime.

## Consequences and trade-offs

Spring for GraphQL has no built-in multipart upload (ADR-006). Resolvers must avoid N+1 calls by batching person lookups (BatchGetPeople).
