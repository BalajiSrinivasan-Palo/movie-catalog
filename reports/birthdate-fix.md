# Birth dates before 1905 stored one day early (people-service)

Date: 2026-10-22. Found while checking the filter layout on `/people`. **Status: fixed in code with a regression test; NOT compiled or run yet (D15, owner runs Gradle); stored dates not yet repaired.**

## Symptom
The API returned, for example, Clyde Bruckman `1894-09-19` and Lon Chaney `1883-03-31`, although the seed sent `1894-09-20` and `1883-04-01`. People born in 1904 and later were correct. The frontend only displays what the API returns.

## Cause (reproduced outside the app)
`DateRoundTrip.java`, run with the project's H2 2.4.240 driver and JDK 21, writes a `LocalDate` into an H2 `DATE` column in two ways:
| JVM zone | `setObject(LocalDate)` | `setDate(java.sql.Date)` |
|----------|------------------------|---------------------------|
| Asia/Singapore | 1894-09-20 stored as 1894-09-20 | **stored as 1894-09-19**; 1883-04-01 stored as **1883-03-31** |
| UTC | correct | correct |

Dates from 1904 and later are correct in both zones. Singapore used local mean time (+06:55:25) before 1905, so the legacy `java.sql.Date` path, which goes through midnight in the JVM zone, lands on the previous day. Hibernate 7.4.5 binds `LocalDate` through `java.sql.Date` unless told otherwise; the services run on the owner's Mac in `Asia/Singapore`. The proto and GraphQL date handling is clean: year, month and day are copied field by field.

## Fix
- `people-service/src/main/resources/application.yml`: `spring.jpa.properties.hibernate.type.java_time_use_direct_jdbc: true` (setting confirmed present in hibernate-core 7.4.5 `MappingSettings`), so `LocalDate` is passed to the driver as a date.
- `people-service/build.gradle.kts`: the people-service test JVM runs with `user.timezone=Asia/Singapore`, so the bug cannot hide on a machine set to UTC.
- New test `PersonServiceTest`: "birth dates before 1905 are stored and read back unchanged, whatever the JVM time zone". It stores 1894-09-20, 1883-04-01, 1868-05-06 and 1904-01-18, checks the raw column with `cast(birth_date as varchar)`, and checks `get`. The earlier test used 1906, after the offset change, so it could not catch this.
- movie-service stores no `LocalDate` (only a release year and `Instant` timestamps), so it is not affected.

## Owner commands
```
./gradlew :people-service:test --console=plain 2>&1 | tee reports/birthdate-fix-people-test-$(date +%Y%m%d-%H%M).log
```
Then restart the stack (`make dev-stop`, `make dev`, `make dev-check`) so the running people-service uses the fix.

## Stored data to repair after the restart
20 seed people have a stored date one day early (every one born before 1905); no person outside the seed has a pre-1906 date. They will be corrected through `updatePerson` with the dates in `scripts/seed/films.mjs`: Buster Keaton, Clyde Bruckman, Joseph M. Schenck, Robert Wiene, Carl Mayer, Hans Janowitz, Werner Krauss, Conrad Veidt, Lil Dagover, Erich Pommer, Charlie Chaplin, Mack Swain, Rupert Julian, Gaston Leroux, Lon Chaney, Norman Kerry, Carl Laemmle, Sergei Eisenstein, Howard Hawks, Bela Lugosi.

## Outcome, 2026-10-22 15:07
- Owner ran `./gradlew :people-service:test` at 15:06: **BUILD SUCCESSFUL**, people-service 27 tests, 0 failed; the new test "birth dates before 1905 are stored and read back unchanged, whatever the JVM time zone" passed (results 15:06:54). Log: `birthdate-fix-people-test-20261022-1506.log`. (Gradle's result files do not record system properties, so the run itself does not show the `Asia/Singapore` setting; the owner's machine default is Asia/Singapore in any case.)
- Stack restarted at 15:07 with the fix (`make dev-stop`, `make dev`, `make dev-check`: all four up).
- The 20 wrong dates were repaired with `updatePerson`, keeping each stored name and bio; afterwards no seed person's stored date differs from `scripts/seed/films.mjs`. Log: `birthdate-repair-20261022-1507.log`. Spot check: Clyde Bruckman `1894-09-20`, Lon Chaney `1883-04-01`.
