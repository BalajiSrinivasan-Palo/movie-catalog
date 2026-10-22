# ADR-015: Multi-genre movie filter (match any), year picker over release years

**Status:** Accepted (D22, approved by the owner 2026-10-22)

## Context

On 2026-10-22 the owner shared reference images: an IMDb-style search box with a scope menu, a date-range calendar, and a genre chip group where several genres can be selected. The movie filter takes one `genre` (GraphQL `MovieFilter.genre`, protobuf `ListMoviesRequest.genre = 4`). Movies store only `releaseYear` (BRD section 7, ADR-008). The owner chose: a year picker rather than a full release date, and several genres matched with **any** (OR).

## Decision

- Add `repeated Genre genres = 7` to `ListMoviesRequest` and `genres: [Genre!]` to GraphQL `MovieFilter`. A movie matches if it has any listed genre; an empty list means no genre filter. Keep `genre = 4` in protobuf and keep `genre` in GraphQL marked `@deprecated`; when both are sent, they are combined.
- movie-service validates the list (at most 15, no duplicates, known values) with the existing key `movie.genres.invalid`, and filters with an OR of `isMember` predicates, so no join and no duplicate rows.
- The "from" and "to" pickers choose years from a calendar-style grid; no release-date field is added.
- The search scope menu (All, Titles, People) uses the existing `search`, `searchMovies` and `searchPeople` queries; no contract change.

## Why

"Any" is what a browsing user expects from several selected chips ("show me comedies or crime films") and never empties the list as fast as "all" does. Adding a field rather than changing `genre` keeps the change backward compatible: old clients and tests keep working, and rollback is a frontend revert. A year grid gives the calendar feel without asking for a day and month the catalog does not store; adding a full release date would touch the database, every contract, forms, validation and seed data for no gain in this assessment.

## Consequences and trade-offs

- Contract and backend change in movie-service and the gateway; per D15 the owner builds and tests it, so 8c cannot be marked done on Claude's word.
- Two ways to filter one genre exist until `genre` is removed; this is documented as deprecated.
- A user cannot ask for "comedy **and** crime"; that would be a later, separate change.
- The year picker and scope menu are hand-built (ADR-010): more code to test for keyboard and screen-reader behaviour, but no new dependency.
