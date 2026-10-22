---
name: sveltekit-typescript
description: Conventions for the SvelteKit TypeScript frontend. Use when writing or reviewing frontend code, components, routes or tests.
---
# SvelteKit / TypeScript conventions
- SPA mode (`ssr=false`, adapter-static). TypeScript `strict`; no `any` without a comment explaining why.
- Every async action exposes `idle | pending | success | error`; lists show skeletons; mutations disable controls while pending and keep form input on failure.
- GraphQL only through `lib/api/client.ts`; upload only through the XHR helper (progress, abort). Cancel stale requests with AbortController.
- Render user text with normal interpolation; never `{@html}` on user data.
- Accessibility: labelled inputs, visible focus, keyboard-operable dialogs, `aria-live` for status, `role=progressbar` with values, alt text, contrast 4.5:1.
- Mobile-first CSS with custom properties; breakpoints 600 px and 960 px; no horizontal scroll at 360 px; touch targets 44 px.
- Tests: Vitest + Testing Library for components and stores (fake timers for debounce); Playwright for journeys with axe checks.
- Gates: `pnpm check`, `pnpm lint`, `pnpm test` must pass.
