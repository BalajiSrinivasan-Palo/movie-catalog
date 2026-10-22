# Movie Catalog frontend

SvelteKit (Svelte 5, TypeScript strict) in single-page mode, built to static files. It has no runtime dependencies. It talks only to the gateway: GraphQL at `/graphql`, images at `/artwork/*`. In development and preview, Vite forwards both to `http://localhost:8080`.

```bash
pnpm install
pnpm dev                 # http://localhost:5173
pnpm check               # svelte-check, strict
pnpm lint                # eslint and prettier rules
pnpm test                # vitest
pnpm test:coverage       # same, with the coverage thresholds
pnpm build               # static output in build/
pnpm exec playwright install chromium                  # once
pnpm exec playwright test --project=mocked             # layout and accessibility, no backend needed
pnpm seed                # load demo data into a running gateway
```

The `stack` Playwright project needs the whole backend, so use `../scripts/e2e.sh`, which starts it, seeds, runs the tests and stops it.

Layout of `src/lib`: `api/` (typed GraphQL client and upload helper), `stores/` (loading and action state), `components/`, `views/` (one per page, kept out of `routes/` so they can be tested), `forms/` (validation rules shared by forms), `messages.ts` (every user-facing error text, keyed by the server's message keys). Dependencies and why each exists: `../docs/frontend-dependencies.md`.
