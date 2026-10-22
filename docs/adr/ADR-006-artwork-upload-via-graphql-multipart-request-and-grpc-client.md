# ADR-006: Artwork upload via GraphQL multipart request and gRPC client streaming

**Status:** Accepted

## Context

Frontend traffic must be GraphQL, uploads need a visible progress bar, and files up to 5 MiB must not be buffered whole in memory.

## Decision

The browser sends a multipart request following the GraphQL multipart request specification to `/graphql` using `XMLHttpRequest` (for `upload.onprogress` and `abort`). A small gateway component parses `operations`, `map` and the file part and injects an `Upload` value. The resolver streams the file to `MovieService.UploadArtwork` in 64 KiB chunks. `movie-service` writes to a temp file, validates, then commits (BR-11).

## Why

`fetch` cannot report upload progress, so XHR is required. The multipart spec keeps the operation inside GraphQL (A-T5). Streaming bounds memory use (NFR-ARCH-05).

## Consequences and trade-offs

The multipart handling is custom code (about one class) because Spring for GraphQL does not provide it; it needs focused tests for malformed `map`, missing parts and oversize bodies. Progress reaches 100% when bytes reach the gateway, so a 'Processing' state covers server work.
