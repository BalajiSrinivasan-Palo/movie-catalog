# S-701 part 7c report: drag-and-drop artwork upload

Date: 2026-10-22. Implementer: Claude (main session). **Status: implemented; unit and component tests not yet run (same environment block as 7a); not reviewed.**

## What changed
| File | Change |
|------|--------|
| `src/lib/drag.ts` (new) | `isFileDrag(event)`: true only when the drag carries files |
| `src/lib/components/ArtworkUploader.svelte` | The label around the file input is now a dashed drop zone: "Drag an image here or choose a file" and "JPEG, PNG or WebP · up to 5 MB"; while a file is dragged over, "Drop to upload" (plus "Replaces the current artwork" when there is one). `choose()` and the new `drop()` both call `accept(file)`, so validation, progress, Processing…, Cancel, error and Retry are one code path. Several files: first used, polite note "Only the first file was used." Drops while uploading and non-file drags are ignored. The input keeps its text label (visually hidden) and stays focusable; the zone shows the focus ring |
| `src/routes/+layout.svelte` | Window `dragover`/`drop` handler that only calls `preventDefault()` for file drags, so a missed drop does not open the image and leave the app (AC13). It never reads the file |
| `e2e/mocked/responsive.e2e.ts` | The 44 px check measures a file input by its label (the drop zone), as it already did for checkboxes and radios |
| Tests | `ArtworkUploader.test.ts` (8 new drop tests), `drag.test.ts` (new), `e2e/mocked/dropzone.e2e.ts` (new, 3 tests with a real `DataTransfer`), `e2e/mocked/screenshots.e2e.ts` (zone at rest and dragging) |

No contract, validation, backend or dependency change. The server stays the authority on the file (S-304 AC3).

## Evidence
| Gate | Result | Log |
|------|--------|-----|
| `pnpm check` | 0 errors, 0 warnings | `7c-gates-final-20261022-1336.log` |
| `pnpm lint` | pass | same |
| `pnpm build` | pass | same |
| `pnpm exec playwright test --project=mocked` | 60 passed (57 earlier plus 3 drop-zone tests: drag highlight and drop-to-upload ending in "Artwork saved."; outside file drop prevented; outside text drop not prevented), 17 skipped (screenshot spec) | same |
| Screenshots | zone at rest and while dragging, 360 and 1280 px | `screenshots-7c/` |
| `python3 -I scripts/check_traceability.py` | OK: no gaps | run in session |
| **`pnpm test` (Vitest)** | **NOT RUN successfully: 239 of 239 fail before any assertion (environment, see `7a-report.md`)** | same |

## Acceptance criteria
| AC | Evidence |
|----|----------|
| AC10 zone, labelled input | unit "shows the drop zone text…" (unrun); a11y E2E uploader error with axe and `getByLabel(/Image file/)` (passed); responsive E2E 44 px via the label (passed) |
| AC11 drag highlight | unit "highlights while…", "does not mention replacing…" (unrun); E2E "dragging a file over the zone highlights it…" (passed); screenshots |
| AC12 same path, several files, busy, non-file | five unit tests (unrun); E2E drop ending in "Artwork saved." (passed) |
| AC13 no navigation on a missed drop | E2E "a file dropped outside the zone is not opened…" and the text-drag counterpart (passed); `drag.test.ts` (unrun) |

## Not covered
- The E2E tests dispatch drag events with a real `DataTransfer`; they do not perform an operating-system drag gesture. A manual check is still needed: drag a real image from Finder onto a movie page, onto the zone and beside it.
- The `stack` E2E project (real services) was not run in this session.
