# ADR-003: Credits owned by movie-service; cross-service rules via gRPC

**Status:** Accepted (D7, D8, D11)

## Context

Credits link movies to people owned by different services. Deleting a person must be blocked if credits exist (BR-05). Adding a credit must reject unknown people.

## Decision

`movie-service` owns `Credit(movieId, personId, role)`. `people-service` calls `MovieService.CheckPersonCredits` before deleting. For `addCredit` the **gateway** calls `PeopleService.GetPerson` first, then `MovieService.AddCredit`. Names are resolved by the gateway via `BatchGetPeople`.

## Why

One owner per relationship avoids distributed joins. If `movie-service` validated person existence it would call `people-service` while `people-service` calls `movie-service`, creating a cycle where each needs the other up. The gateway pre-check keeps the call graph acyclic.

## Consequences and trade-offs

Person existence is enforced at the gateway, not in `movie-service`; a person deleted between check and insert leaves a credit that renders as 'Unknown person' (BR-07, BR-13). Accepted as a documented limitation. Alternative considered: `movie-service` calls `people-service` (rejected: cycle).
