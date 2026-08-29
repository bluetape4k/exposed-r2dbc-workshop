# Chapter 13: Ecosystem Integrations

[English](README.md) | [Korean](README.ko.md)

Chapter 13 keeps only ecosystem examples that can be taught as real R2DBC
workshop material. CockroachDB retry handling, Ktor integration, a custom
Spring Modulith publication log, DDD boundary examples, and a checkpointable
batch sibling now share the same coroutine-first database boundary. BigQuery,
Trino, StarRocks, and DuckDB are intentionally not ported here because their
practical workshop paths are JDBC, HTTP/native client, or embedded-client
centered rather than R2DBC.

## Modules

| Module | Scenario | Default execution |
|---|---|---|
| [`03-cockroachdb-retry`](03-cockroachdb-retry/) | Rerun an inventory reservation transaction for CockroachDB retryable SQLSTATE `40001` | H2 R2DBC transaction replay with a synthetic SQLSTATE failure |
| [`05-ktor-exposed-integration`](05-ktor-exposed-integration/) | Ktor routes backed by an Exposed R2DBC repository and caller-owned pool | H2 R2DBC pool with health/readiness and CRUD tests |
| [`06-spring-modulith-publications`](06-spring-modulith-publications/) | Spring Modulith-shaped order/fulfillment handoff with a custom publication log | H2 R2DBC order/log transaction and coroutine dispatcher |
| [`07-ddd-aggregate-repository`](07-ddd-aggregate-repository/) | Value-object aggregate, ordered domain events, and atomic repository rollback | H2 R2DBC aggregate, line, and event tables |
| [`08-ddd-modulith-boundaries`](08-ddd-modulith-boundaries/) | Named-interface boundary verification between orders and shipping | H2 R2DBC event handoff plus valid/invalid Modulith checks |
| [`09-checkpointable-r2dbc-batch`](09-checkpointable-r2dbc-batch/) | Checkpointable keyset batch with provider reader/writer, typed metadata, cancellation, and restart | H2 R2DBC source/target tables plus provider job/step metadata |

Each example README explains its local schema, R2DBC trade-offs, and verification
contract so the flow can be reviewed without opening the source first.

## R2DBC Scope

CockroachDB speaks the PostgreSQL wire protocol, so a production deployment can
use a PostgreSQL R2DBC driver and apply CockroachDB's retry contract at the
transaction boundary. The workshop keeps the default run local and deterministic:
tests use H2 R2DBC plus a synthetic SQLSTATE `40001` failure to prove that only
retryable serialization failures are replayed.

The Ktor example keeps the `ConnectionPool` lifecycle in the application and
uses only `suspendTransaction` for repository access. The Spring Modulith
example records a custom publication log because the official
`EventPublicationRepository` SPI is synchronous and has no R2DBC implementation.
The DDD examples make aggregate event sequencing, commit/rollback behavior, and
named-interface boundary violations executable with local H2 R2DBC tests. The
checkpointable batch sibling composes the published snapshot
`io.github.bluetape4k.exposed:bluetape4k-exposed-batch:2.0.0-SNAPSHOT` R2DBC
reader, writer, and metadata repository directly. Its H2 tests prove both
`STOPPED` and `FAILED` restart without duplicating committed target IDs; the
proof remains deliberately separate from the JDBC sibling in `exposed-workshop`.
The snapshot repository and catalog exception are documented in the module
README until the provider is promoted to a stable release.

The removed source examples are not covered by local adapter stand-ins:

| `exposed-workshop` example | Chapter 13 decision |
|---|---|
| `01-bigquery-dry-run` | Excluded; BigQuery dry-run is client/API centered, not R2DBC centered |
| `02-trino-session-options` | Excluded; Trino session options belong to Trino's client/coordinator protocol, not a default R2DBC path |
| `03-cockroachdb-retry` | Covered through PostgreSQL-compatible R2DBC transaction retry behavior |
| `04-starrocks-olap-local` | Excluded; this workshop should not imply a default StarRocks R2DBC driver |
| `09-duckdb-embedded-analytics` | Excluded; DuckDB is treated here as an embedded/JDBC-centered analytics engine |
| `05-ktor-exposed-integration` | Covered by the Ktor R2DBC integration module |
| `06-spring-modulith-publications` | Covered by the custom R2DBC publication-log module; native SPI remains synchronous |
| `07-ddd-aggregate-repository` | Covered by the atomic DDD aggregate repository module |
| `08-ddd-modulith-boundaries` | Covered by the valid/invalid Spring Modulith boundary module |
| `11-checkpointable-batch` | Covered by the checkpointable R2DBC batch sibling; keyset and cancellation semantics remain provider-native |

## Verification

```bash
./gradlew projects --console=plain
repo-test-summary -- ./gradlew :03-cockroachdb-retry:test :05-ktor-exposed-integration:test :06-spring-modulith-publications:test :07-ddd-aggregate-repository:test :08-ddd-modulith-boundaries:test :09-checkpointable-r2dbc-batch:test -PuseDB=H2 --continue --console=plain
```

`.github/workflows/Examples.yml` runs the same six-module Chapter 13 H2 smoke
coverage when Chapter 13 files, root README files, shared infrastructure, Gradle
files, or the workflow itself change.
