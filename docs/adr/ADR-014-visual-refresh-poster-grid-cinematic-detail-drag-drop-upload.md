# ADR-014: Visual refresh: poster grid lists, cinematic detail pages, drag-and-drop upload

**Status:** Accepted (D21, approved by the owner 2026-10-22)

## Context

The owner was not happy with the first UI: a grey header with solid blue tabs, a plain list of movies and form-like detail pages. On 2026-10-22 three mockups were shown: A (poster grid), B (sidebar list) and C (cinematic detail). The owner chose A for the lists and C for the detail pages, and asked for drag-and-drop artwork upload. ADR-010 (hand-written CSS, no UI library) and the WCAG 2.1 AA target (NFR-A11Y-01) still apply.

## Decision

- Lists (movies, people) are responsive card grids. Movies show 2:3 posters, people show initials circles. The genre filter becomes a single-choice chip row built from native radio inputs.
- Detail pages (movie, person) open with a dark hero band holding the picture, title, key facts and the main actions. Credits and filmography are card grids.
- The artwork uploader becomes a drop zone around the existing labelled file input. Drop and choose share one code path, so validation, progress, cancel and retry are unchanged.
- Still hand-written CSS with tokens in `global.css`: no new dependency, no icon font, no dark mode.

## Why

A poster-first layout is what people expect from a film catalog, and it uses the artwork and thumbnails the app already has (FR-ART-05). The hero band gives the detail page a clear hierarchy without new data. Option B was not chosen: it suits data management but looked less like a movie product. Building the drop zone around the native file input keeps keyboard and screen-reader access without extra work, and reusing the upload path means the S-304 tests still guard it.

## Consequences and trade-offs

- Frontend markup and CSS change on six views and the uploader. Existing component and E2E tests need selector updates. Assertions must not be weakened, and this is reviewed.
- The dark hero band adds colour pairs that must be checked for 4.5:1 contrast by the static test.
- Choosing a genre chip now applies the filter at once, while sort and years still use the Apply button. This is a small behaviour difference that is worth noting in the walkthrough.
- Movies without artwork look emptier in a poster grid, so the placeholder needs care. The seed data (S-601) includes artwork for most movies.
- Drag and drop is not available on touch devices, which still use "choose a file".
- No backend, contract or data change. Rolling back means reverting the frontend commits.
