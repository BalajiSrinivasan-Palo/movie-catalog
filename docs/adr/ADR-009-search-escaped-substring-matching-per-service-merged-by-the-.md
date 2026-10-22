# ADR-009: Search: escaped substring matching per service, merged by the gateway

**Status:** Accepted (D9)

## Context

Search must cover movies, cast and creators, with progress feedback; fuzzy matching is out of scope.

## Decision

`SearchMovies` (title, synopsis, genre names) and `SearchPeople` (name, bio) use case-insensitive `LIKE '%q%' ESCAPE '\'` with `%`, `_`, `\` escaped. The gateway runs both in parallel and returns two paged groups. Queries are trimmed, 2–100 characters.

## Why

Simple, deterministic, testable, and adequate at demo scale (about 1,000 rows each).

## Consequences and trade-offs

Leading-wildcard `LIKE` cannot use an index and scans the table; acceptable for the target size. No ranking. If one backend fails, the whole search fails.
