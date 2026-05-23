# R2DBC Batch Insert Generated Values

## Context

After switching the workshop to `bluetape4k-dependencies` 1.1.1, the
PostgreSQL CI matrix failed in `exposed-r2dbc-shared` while seeding movie sample
data. Exposed R2DBC reported an auto-increment count mismatch during
`MovieTable.batchInsert(...)`.

## Decision

Sample data seed inserts do not use generated return values. Mark those
`batchInsert` calls with `shouldReturnGeneratedValues = false`, matching the
existing high-throughput insert pattern used elsewhere in the bluetape4k Exposed
repositories.

## Outcome

The targeted PostgreSQL shared test passes locally:

- `./gradlew :exposed-r2dbc-shared:test -PuseDB=POSTGRESQL --max-workers=1 --continue`

## Future Guidance

For R2DBC batch inserts where generated IDs are not consumed, explicitly set
`shouldReturnGeneratedValues = false`. This avoids driver- and dialect-specific
generated-key handling failures, especially on PostgreSQL.
