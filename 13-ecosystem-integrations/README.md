# Chapter 13: Ecosystem Integrations

[English](README.md) | [한국어](README.ko.md)

Chapter 13 keeps only ecosystem examples that can be taught as real R2DBC
workshop material. In this branch, that means CockroachDB retry handling through
the PostgreSQL-compatible R2DBC boundary. BigQuery, Trino, StarRocks, and DuckDB
are intentionally not ported here because their practical workshop paths are
JDBC, HTTP/native client, or embedded-client centered rather than R2DBC.

## Modules

| Module | Scenario | Default execution |
|---|---|---|
| [`03-cockroachdb-retry`](03-cockroachdb-retry/) | Rerun an inventory reservation transaction for CockroachDB retryable SQLSTATE `40001` | H2 R2DBC transaction replay with a synthetic SQLSTATE failure |

The example README includes its own ERD and sequence diagram so the local schema
and retry flow can be reviewed without opening the source first.

## R2DBC Scope

CockroachDB speaks the PostgreSQL wire protocol, so a production deployment can
use a PostgreSQL R2DBC driver and apply CockroachDB's retry contract at the
transaction boundary. The workshop keeps the default run local and deterministic:
tests use H2 R2DBC plus a synthetic SQLSTATE `40001` failure to prove that only
retryable serialization failures are replayed.

The removed source examples are not covered by local adapter stand-ins:

| `exposed-workshop` example | Chapter 13 decision |
|---|---|
| `01-bigquery-dry-run` | Excluded; BigQuery dry-run is client/API centered, not R2DBC centered |
| `02-trino-session-options` | Excluded; Trino session options belong to Trino's client/coordinator protocol, not a default R2DBC path |
| `03-cockroachdb-retry` | Covered through PostgreSQL-compatible R2DBC transaction retry behavior |
| `04-starrocks-olap-local` | Excluded; this workshop should not imply a default StarRocks R2DBC driver |
| `09-duckdb-embedded-analytics` | Excluded; DuckDB is treated here as an embedded/JDBC-centered analytics engine |
| `05-ktor-exposed-integration` | Future issue #116 |
| `06-spring-modulith-publications` | Future issue #116 |
| `07-ddd-aggregate-repository` | Future issue #117 |
| `08-ddd-modulith-boundaries` | Future issue #117 |

## Verification

```bash
./gradlew projects --console=plain
repo-test-summary -- ./gradlew :03-cockroachdb-retry:test -PuseDB=H2 --continue --console=plain
```

`.github/workflows/Examples.yml` runs the same Chapter 13 H2 smoke coverage
when Chapter 13 files, root README files, shared infrastructure, Gradle files,
or the workflow itself change.
