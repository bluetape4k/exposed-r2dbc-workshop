# Issue #115 Chapter 13 R2DBC Ecosystem Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Chapter 13 R2DBC ecosystem foundation examples for database adapter boundaries.

**Architecture:** Create five source-name leaf modules under `13-ecosystem-integrations`. Keep every default test local and deterministic by using Exposed R2DBC with H2, while documenting unsupported or opt-in external vendor boundaries.

**Tech Stack:** Kotlin, Exposed R2DBC, shared `AbstractR2dbcExposedTest`, H2 R2DBC, Gradle, GitHub Actions.

---

## File Structure

- Modify: `settings.gradle.kts`
  - Discover Chapter 13 leaf modules.
- Create: `13-ecosystem-integrations/README.md`
  - English chapter overview and adapter applicability boundary.
- Create: `13-ecosystem-integrations/README.ko.md`
  - Korean chapter overview and adapter applicability boundary.
- Create: `13-ecosystem-integrations/*/build.gradle.kts`
  - Minimal R2DBC module dependencies.
- Create: `13-ecosystem-integrations/*/src/main/kotlin/...`
  - Typed adapter boundary and local R2DBC projection/retry code.
- Create: `13-ecosystem-integrations/*/src/test/kotlin/...`
  - Focused H2 R2DBC tests.
- Modify: `README.md`, `README.ko.md`
  - Root navigation, module map, featured examples, parity table.
- Modify: `.github/workflows/Examples.yml`
  - Chapter 13 path filters and H2 test job.
- Create: `docs/lessons/2026-07-06-issue-115-chapter13-foundation.md`
  - Durable guardrail for future Chapter 13 work.

## Task 1: Add Chapter Registration And Failing Tests

**Files:**
- Modify: `settings.gradle.kts`
- Create five module `build.gradle.kts` files.
- Create five test files under `13-ecosystem-integrations/*/src/test/kotlin`.

- [ ] **Step 1: Register Chapter 13**

Add:

```kotlin
includeModules("13-ecosystem-integrations", false, false)
```

after the Chapter 12 include.

- [ ] **Step 2: Add module build files**

Each module uses the same dependency shape:

```kotlin
configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.spi)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.h2)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 3: Add failing tests first**

Create tests that import the planned main APIs:

- `BigQueryDryRunRequest`, `BigQueryDryRunProfile`, `BigQueryDryRunSource`,
  `renderBigQueryCandidateSql`.
- `TrinoSessionProfile`, `TrinoQuerySession`, `TrinoLineItems`,
  `renderTrinoExplainSql`.
- `CockroachR2dbcRetryPolicy`, `RetryableSqlStateFailure`,
  `CockroachInventoryItems`, `reserveWithRetry`.
- `StarRocksAnalyticsProfile`, `StarRocksOrderEvents`, `projectRegionalRevenue`.
- `DuckDbR2dbcBoundary`, `DuckDbOrderEvents`, `projectDailyCategorySales`.

Run:

```bash
repo-test-summary -- ./gradlew :01-bigquery-dry-run:test :02-trino-session-options:test :03-cockroachdb-retry:test :04-starrocks-olap-local:test :09-duckdb-embedded-analytics:test -PuseDB=H2 --continue --console=plain
```

Expected: compile failure because the main APIs do not exist yet.

## Task 2: Implement Adapter Boundary Modules

**Files:**
- Create five main Kotlin files under the Chapter 13 modules.

- [ ] **Step 1: BigQuery dry-run boundary**

Implement a profile, immutable request, local table, SQL renderer, and request
builder. The request always sets `dryRun = true` and `useLegacySql = false`.

- [ ] **Step 2: Trino session boundary**

Implement a session profile with catalog/schema/source/client tags, table,
rendered SELECT SQL, and EXPLAIN request builder.

- [ ] **Step 3: Cockroach retry boundary**

Implement SQLSTATE predicate for `40001`, bounded retry policy, inventory table,
and `reserveWithRetry` that reruns the whole R2DBC reservation transaction.

- [ ] **Step 4: StarRocks local OLAP boundary**

Implement typed analytics profile, local order table, SQL renderer, and regional
revenue projection using Exposed R2DBC aggregate queries.

- [ ] **Step 5: DuckDB embedded analytics boundary**

Implement an explicit `DuckDbR2dbcBoundary` rationale, local order table, SQL
renderer, and daily category projection using Exposed R2DBC aggregate queries.

Run the targeted test command after each module or after the batch when the
module APIs compile.

## Task 3: Add Chapter And Root Documentation

**Files:**
- Create: `13-ecosystem-integrations/README.md`
- Create: `13-ecosystem-integrations/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Create: `docs/lessons/2026-07-06-issue-115-chapter13-foundation.md`

- [ ] **Step 1: Chapter README pair**

Document module scenarios, local/opt-in boundaries, source parity, and
verification commands. State explicitly that default tests require no real
external credentials or network calls.

- [ ] **Step 2: Root README pair**

Add Chapter 13 to the learning path, module map, featured examples, and parity
table.

- [ ] **Step 3: Lesson note**

Record that future Chapter 13 adapter work must default to local/fake/opt-in
paths unless an issue explicitly requires a live external system.

## Task 4: Add Workflow Coverage

**Files:**
- Modify: `.github/workflows/Examples.yml`

- [ ] **Step 1: Add path filters**

Add `13-ecosystem-integrations/**` to both `push.paths` and
`pull_request.paths`.

- [ ] **Step 2: Add Chapter 13 job**

Run:

```bash
./gradlew :01-bigquery-dry-run:test :02-trino-session-options:test :03-cockroachdb-retry:test :04-starrocks-olap-local:test :09-duckdb-embedded-analytics:test "-PuseDB=H2" --continue
```

Upload `13-ecosystem-integrations/**/build/test-results/test/*.xml` and
`13-ecosystem-integrations/**/build/reports/tests/test/`.

## Task 5: Verify And Review

**Files:**
- All changed files.

- [ ] **Step 1: Module discovery**

Run:

```bash
./gradlew projects --console=plain
```

Expected: all five Chapter 13 modules appear.

- [ ] **Step 2: Targeted tests**

Run:

```bash
repo-test-summary -- ./gradlew :01-bigquery-dry-run:test :02-trino-session-options:test :03-cockroachdb-retry:test :04-starrocks-olap-local:test :09-duckdb-embedded-analytics:test -PuseDB=H2 --continue --console=plain
```

Expected: all five module tests pass.

- [ ] **Step 3: Static validation**

Run:

```bash
git diff --check
actionlint .github/workflows/Examples.yml
```

If `actionlint` is not installed, record the missing tool and rely on YAML
shape review plus GitHub Actions syntax in PR.

- [ ] **Step 4: Review checklist**

Confirm:

- No real external credentials or network calls in default tests.
- README pair and root README pair both mention Chapter 13.
- Workflow coverage includes Chapter 13.
- `settings.gradle.kts` discovers the modules.
