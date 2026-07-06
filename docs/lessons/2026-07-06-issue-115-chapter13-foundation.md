# Issue #115 Chapter 13 Foundation

## Context

Issue #115 added the first R2DBC Chapter 13 ecosystem example based on
`exposed-workshop/13-ecosystem-integrations`.

## Decision

Keep only CockroachDB retry handling in this R2DBC workshop chapter. CockroachDB
can be taught through a PostgreSQL-compatible R2DBC boundary, while the default
test stays local with H2 and a synthetic SQLSTATE `40001` retry failure.

Do not keep BigQuery, Trino, StarRocks, or DuckDB as local R2DBC stand-ins.
Those examples are JDBC, HTTP/native-client, or embedded-client centered in this
workshop context, so retaining them would create false parity.

## Guardrail

Before adding future ecosystem examples, verify that the target system has a
real R2DBC-shaped driver or protocol path for the lesson being taught. If the
source example is fundamentally JDBC, HTTP/native client, or embedded-client
based, document it as out of R2DBC scope instead of adding a fake local adapter
module.

## Verification

- `repo-test-summary -- ./gradlew :03-cockroachdb-retry:test -PuseDB=H2 --continue --console=plain`
