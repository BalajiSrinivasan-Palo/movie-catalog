# Frontend dependencies and justification (NFR-MNT-03)

Runtime dependencies: **none** beyond Svelte and SvelteKit themselves (they are dev dependencies because the output is a static bundle). Everything else is development tooling.

| Package | Purpose | Why needed |
|---------|---------|-----------|
| `svelte`, `@sveltejs/kit`, `@sveltejs/vite-plugin-svelte`, `vite` | Framework and build | A-T1 |
| `@sveltejs/adapter-static` | Static SPA output with `index.html` fallback | ADR-010 |
| `typescript`, `svelte-check`, `typescript-eslint` | Type checking (strict) and lint | NFR-TECH-01 |
| `eslint`, `eslint-plugin-svelte`, `eslint-config-prettier`, `@eslint/js`, `globals`, `prettier`, `prettier-plugin-svelte` | Lint and format; `svelte/no-at-html-tags` forbids `{@html}` | NFR-MNT-01, NFR-SEC-03 |
| `vitest`, `jsdom`, `@testing-library/svelte`, `@testing-library/jest-dom`, `@testing-library/user-event`, `@vitest/coverage-v8` | Unit and component tests, coverage | NFR-TEST-01/02. `jsdom` is pinned to major 29 because later releases need Node 22.22.2+ |
| `@playwright/test` | Browser tests: layout and accessibility against a mocked API (S-602), journeys against the real stack (S-603) | NFR-TEST-04 |
| `@axe-core/playwright` | Automated accessibility scan (WCAG 2.2 AA tags) inside the browser tests | NFR-A11Y-01, S-602 |
| `@types/node` | Types for Node APIs used in tests and config | tooling |

Not used on purpose: UI frameworks, GraphQL client libraries, state libraries (ADR-010).
