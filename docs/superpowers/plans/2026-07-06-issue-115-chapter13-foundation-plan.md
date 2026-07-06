# Issue #115 Chapter 13 R2DBC Ecosystem Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans
> or direct solo execution to implement this narrowed plan task-by-task. Steps
> use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Chapter 13 honest by retaining only the CockroachDB retry example
that can be explained through a real PostgreSQL-compatible R2DBC boundary.

**Architecture:** `13-ecosystem-integrations` has one leaf module,
`03-cockroachdb-retry`. It uses shared R2DBC test infrastructure and H2 for
default deterministic retry-policy coverage.

**Tech Stack:** Kotlin, Exposed R2DBC, shared `AbstractR2dbcExposedTest`, H2
R2DBC, Gradle, GitHub Actions.

---

## File Structure

- Keep: `13-ecosystem-integrations/03-cockroachdb-retry/`
  - CockroachDB retry policy, inventory reservation code, focused tests, README
    pair with ERD and sequence embeds.
- Delete:
  - `13-ecosystem-integrations/01-bigquery-dry-run/`
  - `13-ecosystem-integrations/02-trino-session-options/`
  - `13-ecosystem-integrations/04-starrocks-olap-local/`
  - `13-ecosystem-integrations/09-duckdb-embedded-analytics/`
  - Non-Cockroach Chapter 13 diagram assets and contact sheets.
- Modify:
  - `13-ecosystem-integrations/README.md`
  - `13-ecosystem-integrations/README.ko.md`
  - `README.md`
  - `README.ko.md`
  - `.github/workflows/Examples.yml`
  - `docs/lessons/2026-07-06-issue-115-chapter13-foundation.md`
  - `docs/review/2026-07-06-issue-115-chapter13-diagram-review.md`

## Task 1: Remove Non-R2DBC Chapter 13 Modules

- [x] Delete BigQuery, Trino, StarRocks, and DuckDB module directories.
- [x] Delete their ERD/sequence SVG and PNG assets.
- [x] Delete the obsolete Chapter 13 architecture diagram and contact sheets.
- [x] Keep only CockroachDB ERD/sequence SVG and PNG assets.

## Task 2: Rewrite Documentation Scope

- [x] Update Chapter 13 README pair to describe CockroachDB as the only
  retained R2DBC-shaped ecosystem example.
- [x] Document why BigQuery, Trino, StarRocks, and DuckDB are excluded instead
  of replaced with local stand-ins.
- [x] Update root README pair navigation, module map, featured example text,
  and parity table.
- [x] Update the durable lesson note with the future guardrail:
  verify actual R2DBC support before adding ecosystem examples.

## Task 3: Update Workflow Coverage

- [x] Keep Chapter 13 path filters in `.github/workflows/Examples.yml`.
- [x] Change the Chapter 13 job to run only:

```bash
./gradlew :03-cockroachdb-retry:test "-PuseDB=H2" --continue
```

## Task 4: Verify

- [x] Run module discovery:

```bash
./gradlew projects --console=plain
```

Expected: only `:03-cockroachdb-retry` appears among Chapter 13 leaf modules.

- [x] Run targeted test:

```bash
repo-test-summary -- ./gradlew :03-cockroachdb-retry:test -PuseDB=H2 --continue --console=plain
```

Expected: CockroachDB retry example passes on H2.

- [x] Validate kept CockroachDB diagrams:

```bash
~/.local/bin/cairosvg docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-erd-01.svg -o docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-erd-01.png -s 2
~/.local/bin/cairosvg docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-sequence-01.svg -o docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-sequence-01.png -s 2
```

Then run the diagram style/audit scripts and eye-inspect the final PNGs.

- [x] Run static validation:

```bash
actionlint .github/workflows/Examples.yml
git diff --check
```

If `actionlint` is not installed, record the missing tool and rely on YAML
shape review plus GitHub Actions syntax in PR.

## Task 5: PR Closeout

- [ ] Commit with Lore protocol.
- [ ] Push branch.
- [ ] Update PR #119 body to CockroachDB-only scope and keep final section
  `## DoD Status`.
- [ ] Verify live PR assignee, labels, body, and checks.
