# Issue #115 Chapter 13 R2DBC Ecosystem Foundation 구현 계획

> **작업자 참고:** 필수 sub-skill은 superpowers:executing-plans다. 또는 이 좁힌 계획을 task 단위로 직접 solo 실행한다. 진행 상태는 checkbox(`- [ ]`) 문법으로 추적한다.

**목표:** 유지: Chapter 13 honest by retaining only the CockroachDB retry example
that can be explained through a real PostgreSQL-compatible R2DBC boundary.

**아키텍처:** `13-ecosystem-integrations` has one leaf module,
`03-cockroachdb-retry`. It uses shared R2DBC test infrastructure and H2 for
default deterministic retry-policy coverage.

**기술 스택:** Kotlin, Exposed R2DBC, shared `AbstractR2dbcExposedTest`, H2
R2DBC, Gradle, GitHub Actions.

---

## 파일 구조

- Keep: `13-ecosystem-integrations/03-cockroachdb-retry/`
  - CockroachDB retry policy, inventory reservation code, focused tests, README
    pair with ERD and sequence embeds.
- Delete:
  - `13-ecosystem-integrations/01-bigquery-dry-run/`
  - `13-ecosystem-integrations/02-trino-session-options/`
  - `13-ecosystem-integrations/04-starrocks-olap-local/`
  - `13-ecosystem-integrations/09-duckdb-embedded-analytics/`
  - Non-Cockroach Chapter 13 diagram assets and contact sheets.
- 수정:
  - `13-ecosystem-integrations/README.md`
  - `13-ecosystem-integrations/README.ko.md`
  - `README.md`
  - `README.ko.md`
  - `.github/workflows/Examples.yml`
  - `docs/lessons/2026-07-06-issue-115-chapter13-foundation.md`
  - `docs/review/2026-07-06-issue-115-chapter13-diagram-review.md`

## 작업 1: Non-R2DBC Chapter 13 Module 제거

- [x] 삭제: BigQuery, Trino, StarRocks, and DuckDB module directories.
- [x] 삭제: their ERD/sequence SVG and PNG assets.
- [x] 삭제: the obsolete Chapter 13 architecture diagram and contact sheets.
- [x] 유지: only CockroachDB ERD/sequence SVG and PNG assets.

## 작업 2: 문서 범위 재작성

- [x] 갱신: Chapter 13 README pair to describe CockroachDB as the only
  retained R2DBC-shaped ecosystem example.
- [x] 문서화: why BigQuery, Trino, StarRocks, and DuckDB are excluded instead
  of replaced with local stand-ins.
- [x] 갱신: root README pair navigation, module map, featured example text,
  and parity table.
- [x] 갱신: the durable lesson note with the future guardrail:
  verify actual R2DBC support before adding ecosystem examples.

## 작업 3: Workflow Coverage 갱신

- [x] 유지: Chapter 13 path filters in `.github/workflows/Examples.yml`.
- [x] 변경: the Chapter 13 job to run only:

```bash
./gradlew :03-cockroachdb-retry:test "-PuseDB=H2" --continue
```

## 작업 4: 검증

- [x] 실행: module discovery:

```bash
./gradlew projects --console=plain
```

기대: only `:03-cockroachdb-retry` appears among Chapter 13 leaf modules.

- [x] 실행: targeted test:

```bash
repo-test-summary -- ./gradlew :03-cockroachdb-retry:test -PuseDB=H2 --continue --console=plain
```

기대: CockroachDB retry example passes on H2.

- [x] 검증: kept CockroachDB diagrams:

```bash
~/.local/bin/cairosvg docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-erd-01.svg -o docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-erd-01.png -s 2
~/.local/bin/cairosvg docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-sequence-01.svg -o docs/images/readme-diagrams/issue-115-chapter13-03-cockroachdb-retry-sequence-01.png -s 2
```

그 다음 the diagram style/audit scripts and eye-inspect the final PNGs.

- [x] 실행: static validation:

```bash
actionlint .github/workflows/Examples.yml
git diff --check
```

If `actionlint` is not installed, record the missing tool and rely on YAML
shape review plus GitHub Actions syntax in PR.

## 작업 5: PR Closeout

- [ ] 커밋: with Lore protocol.
- [ ] 푸시: branch.
- [ ] 갱신: PR #119 body to CockroachDB-only scope and keep final section
  `## DoD Status`.
- [ ] 검증: live PR assignee, labels, body, and checks.
