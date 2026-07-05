# Issue #115 Chapter 13 Foundation

## Context

Issue #115 added the first R2DBC Chapter 13 ecosystem examples based on
`exposed-workshop/13-ecosystem-integrations`.

## Decision

Keep default Chapter 13 R2DBC examples local and deterministic. BigQuery,
Trino, StarRocks, and DuckDB examples expose typed adapter boundaries and local
R2DBC SQL/projection checks instead of opening live external systems.
CockroachDB retry is represented by the retryable SQLSTATE contract and an H2
R2DBC transaction replay test.

## Guardrail

Future Chapter 13 adapter work should default to fake, local, Testcontainers,
or explicitly opt-in behavior. Do not add real credentials, external networks,
or unsupported vendor connections to default tests.

## Verification

- `repo-test-summary -- ./gradlew :01-bigquery-dry-run:test :02-trino-session-options:test :03-cockroachdb-retry:test :04-starrocks-olap-local:test :09-duckdb-embedded-analytics:test -PuseDB=H2 --continue --console=plain`
