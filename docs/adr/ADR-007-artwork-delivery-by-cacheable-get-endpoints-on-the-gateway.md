# ADR-007: Artwork delivery by cacheable GET endpoints on the gateway

**Status:** Accepted (D10)

## Context

NFR-ARCH-03 says the browser uses GraphQL only. Browsers load images with `<img src>`, which issues a GET; embedding base64 in GraphQL responses would bloat list payloads and defeat caching.

## Decision

GraphQL returns URLs (`/artwork/{movieId}/thumbnail?v={version}` and `/artwork/{movieId}/original?v={version}`). The gateway serves them by streaming `MovieService.DownloadArtwork`, with `Cache-Control: public, max-age=31536000, immutable` (the `v` token changes on replace), `ETag`, `X-Content-Type-Options: nosniff`, and the detected content type.

## Why

It is the standard approach for binary assets, keeps `<img>` lazy loading and browser caching, and satisfies the intent of A-T5: all data operations use GraphQL.

## Consequences and trade-offs

Literal reading of NFR-ARCH-03 is violated unless it is amended to 'all data operations via GraphQL; binary image delivery via GET'. If the owner rejects this, the fallback is base64 data URLs in GraphQL, which I recommend against.
