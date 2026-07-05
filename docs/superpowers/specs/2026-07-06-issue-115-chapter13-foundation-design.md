# Issue #115 Chapter 13 R2DBC Ecosystem Foundation Design

## Context

Issue [#115](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/115)
adds the first Chapter 13 R2DBC examples from
`exposed-workshop/13-ecosystem-integrations`.

Source examples in scope:

| Source example | Source behavior | R2DBC decision |
|---|---|---|
| `01-bigquery-dry-run` | BigQuery dry-run request shape, labels, default dataset, mocked external client | Keep a typed dry-run request and render SQL from an Exposed R2DBC transaction; no BigQuery client or credentials |
| `02-trino-session-options` | Trino connection/session profile and EXPLAIN request shape | Keep a typed session profile and EXPLAIN request boundary; no Trino server in default tests |
| `03-cockroachdb-retry` | Cockroach retryable serialization failure reruns a whole reservation transaction | Implement R2DBC retry policy plus H2-backed transaction replay test; no Cockroach Testcontainer in default tests |
| `04-starrocks-olap-local` | StarRocks OLAP profile, DDL/query rendering, local projection fixture | Keep typed OLAP profile and H2 R2DBC projection tests; document StarRocks R2DBC driver boundary |
| `09-duckdb-embedded-analytics` | DuckDB embedded analytics via JDBC/session helpers | Keep embedded analytics concept as H2 R2DBC local projection; document DuckDB JDBC-only boundary for this workshop |

## Problem

The R2DBC workshop currently stops at Chapter 12. Chapter 13 needs a
foundation that teaches ecosystem integration boundaries without making default
tests depend on real cloud credentials, external networks, unsupported R2DBC
drivers, or heavyweight vendor containers.

## Design Decision

Create `13-ecosystem-integrations` with five leaf modules matching the source
example names. Each module exposes a small typed boundary in `src/main/kotlin`
and a focused H2 R2DBC test in `src/test/kotlin`.

External adapters stay local by default:

- BigQuery and Trino are request/profile builders, not live clients.
- CockroachDB retry is modeled with SQLSTATE-aware retry policy and a local
  R2DBC transaction replay.
- StarRocks and DuckDB keep their analytics/projection lesson while documenting
  that this R2DBC workshop does not open unsupported vendor connections.

This keeps the examples executable in CI and still shows where a real adapter
would sit in production code.

## Scope

Required changes:

- Add `13-ecosystem-integrations/README.md` and `README.ko.md`.
- Add leaf modules:
  - `01-bigquery-dry-run`
  - `02-trino-session-options`
  - `03-cockroachdb-retry`
  - `04-starrocks-olap-local`
  - `09-duckdb-embedded-analytics`
- Register Chapter 13 in `settings.gradle.kts`.
- Update root `README.md` and `README.ko.md` navigation and parity rows.
- Add focused Chapter 13 coverage to `.github/workflows/Examples.yml`.
- Add a lesson note for future Chapter 13 adapter work.

Out of scope:

- Real BigQuery, Trino, CockroachDB, StarRocks, or DuckDB connections in default
  tests.
- New dependencies.
- Diagrams or image assets for this issue.
- Framework/domain examples from issues #116 and #117.

## Risks And Mitigations

- False parity: tests could validate only data classes. Mitigation: every leaf
  module includes at least one Exposed R2DBC transaction or rendered SQL/projection
  assertion.
- Unsupported driver confusion: README could imply R2DBC drivers exist for all
  source systems. Mitigation: Chapter README includes an explicit applicability
  boundary table.
- CI cost: DB matrix could become expensive. Mitigation: Examples workflow runs
  Chapter 13 through H2 only; nightly full H2 `test` still discovers modules.
- Module-name collision: Gradle leaf names are global. Mitigation: source names
  are unique in this repository.

## Acceptance Criteria

- `./gradlew projects` shows the five Chapter 13 leaf modules.
- Each leaf module has a focused test proving its local R2DBC boundary.
- Chapter 13 README pair explains scenario, adapter boundaries, and verification.
- Root README pair links Chapter 13 and updates the source parity map.
- `.github/workflows/Examples.yml` includes Chapter 13 path filters and an H2
  Chapter 13 job.
- No default test requires real external credentials or network calls.

## DoD

- Spec and plan exist before implementation.
- Targeted tests for all five Chapter 13 modules pass with `-PuseDB=H2`.
- `./gradlew projects`, `git diff --check`, and workflow syntax validation pass
  or record a concrete unavailable-tool gap.
- PR body ends with `## DoD Status` if a PR is created.
