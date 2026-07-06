# Chapter 13: Ecosystem Integrations

[English](README.md) | [한국어](README.ko.md)

Chapter 13 introduces R2DBC-safe ecosystem integration examples. The source
`exposed-workshop` chapter includes cloud warehouses, distributed SQL engines,
and embedded analytics stores. This R2DBC workshop keeps the same learning
targets but makes the default path local, deterministic, and credential-free.

## Modules

| Module | Scenario | Default execution |
|---|---|---|
| [`01-bigquery-dry-run`](01-bigquery-dry-run/) | Build a typed BigQuery dry-run request from locally rendered Exposed R2DBC SQL | H2 R2DBC SQL rendering; no BigQuery credentials |
| [`02-trino-session-options`](02-trino-session-options/) | Keep Trino catalog/schema/source/tags/session properties explicit at the query boundary | H2 R2DBC SQL rendering plus EXPLAIN request shape |
| [`03-cockroachdb-retry`](03-cockroachdb-retry/) | Rerun an inventory reservation transaction for CockroachDB retryable SQLSTATE `40001` | H2 R2DBC transaction replay with a synthetic SQLSTATE failure |
| [`04-starrocks-olap-local`](04-starrocks-olap-local/) | Prepare OLAP rollup rows and document the StarRocks adapter boundary | H2 R2DBC local projection; no StarRocks server |
| [`09-duckdb-embedded-analytics`](09-duckdb-embedded-analytics/) | Preserve the embedded analytics lesson while documenting the DuckDB JDBC boundary | H2 R2DBC local projection; no DuckDB driver |

## Architecture

The chapter separates three concerns:

![Chapter 13 R2DBC ecosystem boundary architecture](../docs/assets/readme-diagrams/issue-115-chapter13-ecosystem-architecture-01.png)

Solid green arrows are the default local H2 R2DBC path executed by tests.
Dashed amber arrows are opt-in external adapter handoff boundaries; they
document request and driver contracts but do not call live vendor systems by
default.

| Boundary | Responsibility | Default test strategy |
|---|---|---|
| Typed adapter model | Capture the external system options that production code must pass deliberately | Data-class validation and header/request assertions |
| Exposed R2DBC local work | Render SQL, collect Flow results, or rerun a transaction inside `suspendTransaction` helpers | H2 R2DBC via `withDb` / `withTables` |
| External system handoff | Explain where a real BigQuery, Trino, CockroachDB, StarRocks, or DuckDB adapter would run | Documentation-only or synthetic failure, never live by default |

Default tests do not require real credentials, external networks, or vendor
containers. When a real adapter is needed later, it should be added behind an
explicit opt-in issue and test profile.

## Source Example Parity

| `exposed-workshop` example | R2DBC counterpart | Coverage decision |
|---|---|---|
| `01-bigquery-dry-run` | `01-bigquery-dry-run` | Covered with typed dry-run request plus local R2DBC SQL rendering |
| `02-trino-session-options` | `02-trino-session-options` | Covered with typed session headers plus local R2DBC EXPLAIN SQL |
| `03-cockroachdb-retry` | `03-cockroachdb-retry` | Covered with SQLSTATE retry policy and H2 R2DBC transaction replay |
| `04-starrocks-olap-local` | `04-starrocks-olap-local` | Covered with local OLAP projection and explicit StarRocks R2DBC boundary |
| `09-duckdb-embedded-analytics` | `09-duckdb-embedded-analytics` | Covered with local analytics projection and explicit DuckDB JDBC boundary |
| `05-ktor-exposed-integration` | Future issue #116 | Framework integration scope |
| `06-spring-modulith-publications` | Future issue #116 | Framework integration scope |
| `07-ddd-aggregate-repository` | Future issue #117 | Domain modeling scope |
| `08-ddd-modulith-boundaries` | Future issue #117 | Domain modeling scope |

## Adapter Boundary Notes

- BigQuery dry-run tests validate request shape only; a production client would
  submit the request outside the database transaction.
- Trino session tests keep catalog, schema, source, client tags, and session
  properties visible as headers.
- CockroachDB retry examples retry only SQLSTATE `40001`; non-retryable
  SQLSTATEs fail immediately.
- StarRocks examples produce local rollup rows and a JDBC URL preview but do
  not claim a default StarRocks R2DBC driver.
- DuckDB examples keep the embedded analytics concept local; this workshop
  uses H2 R2DBC because DuckDB remains a JDBC-centered embedded engine here.

## Verification

```bash
./gradlew projects --console=plain
repo-test-summary -- ./gradlew :01-bigquery-dry-run:test :02-trino-session-options:test :03-cockroachdb-retry:test :04-starrocks-olap-local:test :09-duckdb-embedded-analytics:test -PuseDB=H2 --continue --console=plain
```

`.github/workflows/Examples.yml` runs the same Chapter 13 H2 smoke coverage
when Chapter 13 files, root README files, shared infrastructure, Gradle files,
or the workflow itself change.
