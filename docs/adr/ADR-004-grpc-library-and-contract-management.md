# ADR-004: gRPC library and contract management

**Status:** Proposed; verify before Phase 7

## Context

Service-to-service calls must use gRPC with versioned contracts.

## Decision

Single `contracts` Gradle module holds `.proto` files under `moviecatalog.<service>.v1` and generates stubs with the protobuf Gradle plugin. Server/client wiring uses Spring gRPC if it is compatible with the chosen Spring Boot version; otherwise plain grpc-java with a `SmartLifecycle` bean per server and `ManagedChannel` beans. Errors use `google.rpc.Status` with a custom `ErrorDetail`. No TLS in local setup.

## Why

A shared contracts module guarantees all services compile against the same definitions. Versioned packages permit later evolution. The library choice depends on version compatibility that cannot be checked until Maven Central is reachable (CR-03).

## Consequences and trade-offs

Risk of rework if the preferred library does not fit. Plain grpc-java is the fallback because it has no framework-compatibility dependency. Plaintext gRPC must not be used beyond local development (NFR-SEC-08).
