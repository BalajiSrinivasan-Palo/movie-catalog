---
name: grpc-contracts
description: Rules for editing protobuf contracts and gRPC servers/clients. Use when touching contracts/ or any gRPC call.
---
# gRPC contract rules
- Contracts live in `contracts/src/main/proto/moviecatalog/<service>/v1/`. Do not change field numbers or remove fields; add new fields or a `v2` package for breaking changes, with an ADR.
- Every RPC documents its error statuses in comments. Failures use `google.rpc.Status` with `common.v1.ErrorDetail`.
- Every call has a deadline. Streams use 64 KiB chunks; first message is metadata/header.
- No service calls another except the dependency graph in `docs/architecture/architecture.md` (gateway -> both; people -> movie).
- Validate protos with `protoc` (or `python3 -m grpc_tools.protoc`) before commit; contract tests cover each RPC and error mapping.
