# ADR-012: Image processing with ImageIO and TwelveMonkeys WebP

**Status:** Proposed; verify before Phase 7

## Context

Artwork must be validated by content, dimension-limited, and thumbnailed. WebP is in the approved type list.

## Decision

Detect type from magic bytes, decode header dimensions with `ImageIO` readers before full decode, reject over 6000×6000, create a 320 px wide JPEG thumbnail (alpha flattened onto white). Add `com.twelvemonkeys.imageio:imageio-webp` because the JDK has no WebP reader.

## Why

ImageIO avoids a native image library. One small, well-known dependency is justified by the WebP requirement.

## Consequences and trade-offs

Thumbnails are JPEG regardless of source. If the WebP dependency cannot be verified, WebP must be dropped from the BRD by owner approval.
