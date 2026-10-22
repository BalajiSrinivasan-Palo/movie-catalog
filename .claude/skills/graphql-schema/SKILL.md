---
name: graphql-schema
description: Rules for the GraphQL schema and gateway resolvers. Use when editing schema.graphqls or gateway code.
---
# GraphQL rules
- Schema-first in `api-gateway/src/main/resources/graphql/schema.graphqls`; keep it in sync with the gateway contract tests.
- Resolvers only orchestrate gRPC calls; no business rules. Batch person lookups (one `BatchGetPeople` per request).
- Errors: `extensions.code` in {VALIDATION_FAILED, NOT_FOUND, CONFLICT, UNAVAILABLE, INTERNAL}, optional `fieldErrors`, always `correlationId`. No internals in messages.
- Uploads follow the GraphQL multipart request spec; never buffer a whole file. Image bytes are served by `GET /artwork/...`, not GraphQL.
- Limit depth/complexity; introspection only in the `dev` profile.
