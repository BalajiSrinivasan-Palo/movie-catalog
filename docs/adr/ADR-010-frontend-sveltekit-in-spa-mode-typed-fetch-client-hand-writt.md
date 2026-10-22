# ADR-010: Frontend: SvelteKit in SPA mode, typed fetch client, hand-written CSS

**Status:** Accepted

## Context

The frontend must be Svelte in TypeScript, responsive, with explicit loading and progress feedback, and keep dependencies minimal.

## Decision

SvelteKit with `ssr = false` and `adapter-static` (fallback page); typed GraphQL operations through a small `fetch` wrapper with `AbortController`; upload through an XHR helper; CSS with custom properties and mobile-first breakpoints; no UI or GraphQL client library; list state kept in URL query parameters.

## Why

There is no SEO need, SPA mode removes server/browser divergence and keeps state handling simple for the owner to explain. A tiny client is easier to understand and test than a framework. URL state makes lists shareable and back-button friendly.

## Consequences and trade-offs

More hand-written UI code than with a component library; accessibility of dialogs, toasts and focus handling must be built and tested manually. Cache management is manual.
