# Issue #115 Chapter 13 R2DBC Ecosystem Foundation 설계

## 맥락

Issue [#115](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/115)
adds the Chapter 13 ecosystem integration counterpart for the R2DBC workshop.
The scope has been narrowed to examples that are honest R2DBC teaching material.

## Source Example Decisions

| Source example | Source behavior | R2DBC workshop decision |
|---|---|---|
| `01-bigquery-dry-run` | BigQuery dry-run request shape, labels, default dataset, mocked external client | Excluded; BigQuery dry-run is client/API centered rather than R2DBC centered |
| `02-trino-session-options` | Trino connection/session profile and EXPLAIN request shape | Excluded; Trino session options belong to Trino client/coordinator protocol, not a default R2DBC path |
| `03-cockroachdb-retry` | Cockroach retryable serialization failure reruns a whole reservation transaction | Covered with SQLSTATE-aware retry policy and H2-backed R2DBC transaction replay test |
| `04-starrocks-olap-local` | StarRocks OLAP profile, DDL/query rendering, local projection fixture | Excluded; the workshop should not imply a default StarRocks R2DBC driver |
| `09-duckdb-embedded-analytics` | DuckDB embedded analytics via JDBC/session helpers | Excluded; DuckDB is treated here as an embedded/JDBC-centered analytics engine |

## Problem

The R2DBC workshop should not create fake parity for systems whose normal
workshop path is JDBC, HTTP/native client, or embedded-client based. Chapter 13
needs a small foundation that demonstrates a real R2DBC-compatible ecosystem
boundary without requiring live vendor infrastructure in default tests.

## 설계 Decision

Keep `13-ecosystem-integrations/03-cockroachdb-retry` as the only Chapter 13
leaf module for issue #115. CockroachDB speaks the PostgreSQL wire protocol, so
production code can apply CockroachDB's retry contract through a PostgreSQL
R2DBC driver boundary.

Default tests stay local and deterministic:

- H2 R2DBC creates the workshop table and transaction boundary.
- A synthetic SQLSTATE `40001` failure proves retryable serialization failures
  rerun the whole reservation transaction.
- Non-retryable SQLSTATE failures fail immediately.
- No default test opens a live CockroachDB container or external network.

## 범위

Required changes:

- Keep `13-ecosystem-integrations/03-cockroachdb-retry`.
- Delete the BigQuery, Trino, StarRocks, and DuckDB Chapter 13 modules.
- Keep only the CockroachDB ERD and sequence README diagrams.
- Update Chapter 13 README pair, root README pair, workflow coverage, plan,
  review notes, and lessons to match the narrowed scope.
- Keep `.github/workflows/Examples.yml` Chapter 13 coverage on
  `:03-cockroachdb-retry:test`.

Out of scope:

- BigQuery, Trino, StarRocks, or DuckDB local stand-ins.
- Live CockroachDB Testcontainers coverage in default tests.
- New dependencies.
- Framework/domain examples from issues #116 and #117.

## Risks And Mitigations

- False parity: README could still imply non-R2DBC systems were ported.
  Mitigation: Chapter and root README files explicitly list those examples as
  excluded from R2DBC scope.
- Retry behavior drift: H2 is not CockroachDB. Mitigation: tests target the
  SQLSTATE-based retry policy and transaction replay boundary, not CockroachDB
  engine semantics.
- CI cost: vendor containers would slow default examples. Mitigation: Chapter
  13 workflow runs only the H2-backed CockroachDB retry smoke test.

## 수용 기준

- `./gradlew projects` shows only `:03-cockroachdb-retry` under Chapter 13.
- The Chapter 13 README pair explains why only CockroachDB remains.
- Root README pair links Chapter 13 and updates the parity map.
- `.github/workflows/Examples.yml` runs only `:03-cockroachdb-retry:test` for
  Chapter 13.
- Non-Cockroach Chapter 13 source modules and diagram assets are removed.

## DoD

- Spec and plan reflect the narrowed CockroachDB-only scope.
- Targeted CockroachDB retry test passes with `-PuseDB=H2`.
- 유지함: CockroachDB diagrams pass SVG/render/style audits and eye inspection.
- `./gradlew projects`, `git diff --check`, and workflow syntax validation pass
  or record a concrete unavailable-tool gap.
- PR body ends with `## DoD Status` if a PR is created.
