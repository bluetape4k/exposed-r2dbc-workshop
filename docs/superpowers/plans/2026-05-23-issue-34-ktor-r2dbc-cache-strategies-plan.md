# Issue #34 Ktor R2DBC Cache Strategies 구현 계획

## 단계 1 - Baseline

- Confirm GNO issue context for #34, #69, and exposed-workshop #47/#48.
- 실행: `:02-cache-strategies-r2dbc:test` with H2 to prove the existing cache
  module is healthy before adding the Ktor counterpart.

## 단계 2 - Scaffold Module

- 생성: `11-high-performance/04-cache-strategies-ktor-r2dbc`.
- Rely on the existing `settings.gradle.kts` `includeModules("11-high-performance", false, false)`
  convention, then verify discovery with `./gradlew projects --console=plain`.
- 추가: Ktor, Exposed R2DBC, Redisson, R2DBC H2/pool, serialization, and test
  dependencies through existing `Libs.kt` aliases only.
- Follow the package root
  `exposed.r2dbc.examples.cache.ktor`.

## 단계 3 - 구현: Runtime

- 추가: a database owner that creates one H2 R2DBC pool and closes it on
  `ApplicationStopped`.
- 추가: deterministic table and DTO records with `@Serializable` and
  `java.io.Serializable`.
- 검증: the existing Testcontainers Redis launcher is available before
  implementation.
- 추가: a Redisson-backed user cache repository that owns `RMap` and Exposed
  R2DBC DB operations directly, so it can return `CacheReadResult.Hit`,
  `CacheReadResult.Miss`, or `CacheReadResult.NotFound` from one method.
- 추가: a small application-scoped cache observation service using
  `java.util.concurrent.atomic` counters for route-visible hit/miss/update/clear
  metrics.
- 추가: Ktor routes for read-through, list, write-through, invalidation, clear,
  and stats.
- Close both the R2DBC pool and Redisson client from `ApplicationStopped`.
- 유지: public KDoc in English for the repository, routes, database owner, and
  observation service.

## 단계 4 - Tests

- 사용: `testApplication` with unique H2 database names.
- Start Redis through the existing bluetape4k Testcontainers Redis launcher.
- Generate a unique per-test cache name:
  `exposed:ktor:r2dbc:users:test:<uuid>`.
- Clear that exact cache name in `@BeforeEach` and `@AfterEach`, so failed tests
  do not leak keys.
- Assert:
  - first `GET /users/{id}` returns `MISS`,
  - second read returns `HIT`,
  - invalidation preserves the DB row and next read returns `MISS`,
  - `POST /users` returns `201` and `WRITTEN`,
  - `PUT /users/{id}` returns `200` and subsequent read returns updated data
    with `WRITTEN`,
  - clearing cache still allows database fallback,
  - bad ids and missing rows return structured responses.
- 회피: concurrent request tests in this issue; #69 owns cancellation,
  single-flight, and concurrent suspend cache behavior.

## 단계 5 - Docs and Workflow

- 추가: README.md and README.ko.md.
- 추가: PNG/SVG diagram in `docs/images/readme-diagrams/`, matching the existing
  chapter 11 README convention.
- 갱신: chapter 11 README/README.ko module table and verification commands.
- 추가: chapter 11 job, `push`/`pull_request` path filters, and artifacts to
  `.github/workflows/Examples.yml`.
  - `11-high-performance/02-cache-strategies-r2dbc/**`
  - `11-high-performance/03-routing-datasource/**`
  - `11-high-performance/04-cache-strategies-ktor-r2dbc/**`
  - `docs/images/readme-diagrams/**`
- 추가: a lesson note under `docs/lessons/`.

## 단계 6 - Verification and Review

- Run:
  - `./gradlew projects --console=plain`
  - `./gradlew :04-cache-strategies-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain`
  - chapter 11 workflow-equivalent H2 tests,
  - `./gradlew detekt --parallel --console=plain`
  - `actionlint .github/workflows/Examples.yml`
  - `git diff --check`
  - anti-pattern scan for `runBlocking` in request path, `!!`, swallowed
    `CancellationException`, `@Synchronized`, direct `GenericContainer`, JUnit
    `assertThrows`, and `SqlExpressionBuilder.eq` imports.
- 실행: Codex 6-Tier review and Claude Code CLI 6-Tier review.
- Fix all P0/P1 findings and rerun affected checks.

## 단계 7 - PR and Merge

- 커밋: with Lore trailers.
- Open PR with `Closes #34`.
- Post 단계 7-R review evidence and formal PR review.
- Wait for CI, rebase-merge, sync local `develop`, refresh GNO, and verify
  issue/PR state in GNO.
