# Issue #213 Exposed R2DBC Caffeine 예제 구현 계획

> **For agentic workers:** 이 계획은 승인된 설계와 이 저장소의 Type A
> workflow를 따르며, 각 단계는 체크박스와 fresh evidence로 갱신한다.

**Goal:** `bluetape4k-exposed-r2dbc-caffeine:1.12.1` provider를 사용하는
Ktor R2DBC Caffeine cache example을 Chapter 11의 새 sibling module로
추가하고, 세 write mode와 lifecycle 경계를 테스트·문서·CI에 등록한다.

**Architecture:** `R2dbcCaffeineResources`가 bounded H2 `ConnectionPool`,
`R2dbcDatabase`, `ProductCaffeineRepository`를 소유한다. repository는
`AbstractR2dbcCaffeineRepository<String, ProductRecord>`의 mapping만
구현하고, `ProductCacheService`와 Ktor routes가 동일한 `ProductRecord`를
READ_ONLY/WRITE_THROUGH/WRITE_BEHIND로 노출한다. 종료는 repository final
flush, Exposed manager unregister, pool dispose 순서를 고정한다.

**Tech Stack:** Kotlin 2.4/JVM 25, Exposed R2DBC 1.4.0, bluetape4k
dependencies 1.4.0, `bluetape4k-exposed-r2dbc-caffeine` provider 1.12.1,
Ktor 3.5, H2 R2DBC, `r2dbc-pool`, Caffeine, JUnit 5, Kotlin
serialization, Detekt, GitHub Actions.

---

## 파일 구조와 책임

- Create: `11-high-performance/07-cache-strategies-r2dbc-caffeine/build.gradle.kts`
  — provider, Ktor, H2/R2DBC pool, serialization, test 의존성.
- Create: `.../src/main/kotlin/exposed/r2dbc/examples/cache/caffeine/R2dbcCaffeineCacheApplication.kt`
  — Netty `main`, Ktor module, startup initialization, `ApplicationStopped`.
- Create: `.../config/R2dbcCaffeineResources.kt` — pool/database/repository
  소유와 idempotent close 순서.
- Create: `.../config/R2dbcCaffeinePlugins.kt` — JSON과 구조화된 HTTP 오류.
- Create: `.../domain/ProductTable.kt` — SKU `IdTable<String>`과 row mapper.
- Create: `.../domain/ProductRecords.kt` — `ProductRecord`, requests,
  responses, cache status/health payload.
- Create: `.../domain/ProductDataInitializer.kt` — `suspendTransaction` 기반
  schema와 결정적 seed.
- Create: `.../domain/ProductCaffeineRepository.kt` — provider abstract
  mapping 네 함수와 repository KDoc.
- Create: `.../service/ProductCacheService.kt` — cache state 관찰, version
  증가, cancellation/일반 오류 경계, invalidate/clear/health.
- Create: `.../routes/ProductCacheRoutes.kt` — GET/PUT/DELETE routes.
- Create: `.../src/main/resources/application.conf` — Ktor 기본 설정.
- Create: `.../src/test/kotlin/.../R2dbcCaffeineCacheApplicationTest.kt` —
  HTTP hit/miss, modes, invalidation, unknown key, DB source-of-truth.
- Create: `.../src/test/kotlin/.../R2dbcCaffeineRepositoryLifecycleTest.kt` —
  cancellation, write-behind drain/flush failure/admission rollback,
  idempotent close.
- Create: `.../src/test/resources/junit-platform.properties` and
  `logback-test.xml` — 기존 chapter 11 테스트 리소스와 동일한 설정.
- Modify: `gradle/libs.versions.toml` — 중앙 BOM에 위임하는
  `bluetape4k-exposed-r2dbc-caffeine` alias.
- Modify: `.github/workflows/Examples.yml` — chapter 11 path, task, artifact.
- Modify: `11-high-performance/README.md`, `README.ko.md` — module/link/order.
- Modify: root `README.md`, `README.ko.md` — chapter index/link if present.
- Create: `11-high-performance/07-cache-strategies-r2dbc-caffeine/README.md`
  and `README.ko.md` — source-equivalent module guide.
- Create: `docs/images/readme-diagrams/11-high-performance-07-r2dbc-caffeine-architecture.svg`
  and `.png`, plus `.ko.svg` and `.ko.png` — paired architecture visual.
- Create: `docs/images/readme-diagrams/11-high-performance-07-r2dbc-caffeine-sequence.svg`
  and `.png`, plus `.ko.svg` and `.ko.png` — paired lifecycle/flow visual.
- Create: `docs/lessons/2026-08-28-issue-213-r2dbc-caffeine.md` — Korean
  recurrence-prevention lesson before PR.

## Risk register and rerun points

| Risk | Signal | Mitigation | Rerun point |
|---|---|---|---|
| provider API drift | compile/import error | pin live tag `1.12.1` coordinate and inspect source/jar | repository compile |
| global R2DBC manager leakage | later test sees closed/default DB | capture/restore `TransactionManager.defaultDatabase`; unregister before pool dispose | lifecycle tests and chapter tests |
| write-behind timing | flaky queue/health assertion | bounded polling with `withTimeout`, `validateConsistency`, sequential heavy tests | lifecycle test from RED |
| flush error leaves optimistic cache | direct DB differs from cached value | expose health error, invalidate before DB re-read, document no automatic retry | failure test and README audit |
| cancellation swallowed | timeout returns ordinary error or job hangs | preserve `CancellationException`, use cancellable suspending test | cancellation test |
| module not discovered in CI | absent `./gradlew projects` or artifact | verify auto-discovery plus explicit workflow paths/tasks/artifacts | registration and actionlint |
| diagram pair drift | audit missing PNG/locale/ref | create four source/output pairs, render scale 2, inspect full-size PNGs | diagram audits after last coordinate edit |

## Task 1: Freeze catalog and module registration surface

**Files:** `gradle/libs.versions.toml`, new module directory, settings/workflow
references.

- [x] Add the exact versionless catalog alias:

```toml
bluetape4k-exposed-r2dbc-caffeine = { module = "io.github.bluetape4k.exposed:bluetape4k-exposed-r2dbc-caffeine" }
```

- [x] Create the leaf directory and minimal `build.gradle.kts` using
  `alias(libs.plugins.exposed)` and `alias(libs.plugins.kotlin.serialization)`;
  use `implementation(libs.bluetape4k.exposed.r2dbc.caffeine)` and existing
  Ktor/H2/R2DBC aliases, with `testImplementation` for Ktor test host,
  `kotlinx-coroutines-test`, `bluetape4k.junit5`.
- [x] Run `./gradlew projects --no-configuration-cache --console=plain` through
  context-mode and verify exactly `:07-cache-strategies-r2dbc-caffeine` is
  discoverable before writing production code.
- [x] The versionless alias resolves through the central BOM; `./gradlew projects`
  discovers the leaf and the targeted module test compiles against the provider.
  No local provider version was pinned.

## Task 2: Write RED tests for repository and route contracts

**Files:** the two new test files and test resources.

- [x] Add a deterministic resource factory helper in the test package that
  creates a unique H2 database name and `CacheWriteMode` for each test.
- [x] Add failing tests before production classes exist:
  - first `GET /products/sku-1` is `MISS`, second is `HIT`, unknown SKU is
    `404 NOT_FOUND`;
  - single-key and all-key invalidation cause the next request to read DB;
  - `READ_ONLY` PUT changes cache response but a direct DB read stays old;
  - `WRITE_THROUGH` PUT changes both cache and direct DB immediately;
  - `WRITE_BEHIND` PUT publishes cache, drains to DB, and reports queue depth
    zero with `lastFlushError == null`;
  - DB direct list route/reader bypasses cache;
  - cancellation of a suspended `get` preserves `CancellationException`;
  - close drains pending write-behind work, rejects later writes, and is
    idempotent;
  - a write-behind flush failure is visible in `validateConsistency`, leaves
    the DB source value unchanged, and admission failure rolls back cache/queue
    state.
- [x] Run the smallest test task and capture the expected RED result caused by
  missing module classes, not a malformed test or Gradle registration.
  `./gradlew :07-cache-strategies-r2dbc-caffeine:test -PuseDB=H2
  --no-configuration-cache --console=plain` returned `rc=1` with unresolved
  references to the intentionally absent production classes; no test syntax or
  Gradle registration error remained.

## Task 3: Implement resource/persistence/provider adapter (GREEN)

**Files:** `R2dbcCaffeineResources.kt`, `ProductTable.kt`,
`ProductRecords.kt`, `ProductDataInitializer.kt`,
`ProductCaffeineRepository.kt`.

- [x] Define the string-key table exactly as:

```kotlin
object ProductTable : IdTable<String>("products") {
    override val id = varchar("sku", 40).entityId()
    val name = varchar("name", 120)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}
```

  Keep `ProductRecord(sku, name, version)` as the same DB row/cache/JSON value
  and implement `Serializable` plus Kotlin serialization.
- [x] Implement repository mappings:

```kotlin
override suspend fun ResultRow.toEntity() =
    ProductRecord(this[ProductTable.id].value, this[ProductTable.name], this[ProductTable.version])
override fun UpdateStatement.updateEntity(entity: ProductRecord) { /* name/version */ }
override fun BatchInsertStatement.insertEntity(entity: ProductRecord) { /* EntityID/name/version */ }
override fun extractId(entity: ProductRecord): String = entity.sku
```

- [x] Build a bounded H2 pool with `connectionPoolOf`, set
  `R2dbcDatabaseConfig.dispatcher = Dispatchers.IO`, connect the database,
  set the application default R2DBC database, and create
  `ProductCaffeineRepository(LocalCacheConfig(keyPrefix = "r2dbc:caffeine:products", writeMode = mode, writeBehindBatchSize = 2, writeBehindQueueCapacity = 8))`.
- [x] Implement `close()` with an `AtomicBoolean`: repository first, then
  `TransactionManager.closeAndUnregister(database)`, finally `pool.dispose()`;
  restore the previously captured default database when it was different.
- [x] Implement initializer with `suspendTransaction(db = database)`,
  `SchemaUtils.create(ProductTable)`, and seed `sku-1`/`sku-2` only when empty.
- [x] Run the previously RED repository tests; keep production code minimal
  until the tests turn GREEN, then refactor only duplication while green.
  Evidence: targeted H2 module task passed 10/10 tests after the provider
  initializer/configuration fix.

## Task 4: Implement Ktor service/routes and application lifecycle (GREEN)

**Files:** `R2dbcCaffeinePlugins.kt`, `ProductCacheService.kt`,
`ProductCacheRoutes.kt`, `R2dbcCaffeineCacheApplication.kt`,
`application.conf`.

- [x] Install JSON and status pages mapping invalid input to `400
  INVALID_REQUEST`, missing product to `404 NOT_FOUND`, and unexpected errors
  to a stable structured response without exposing stack traces. `PUT
  /products/{sku}` receives `{"name":"..."}` and returns the updated
  `ProductRecord`; `GET /products/{sku}` returns
  `ProductReadResponse(product, cache)` with `HIT` or `MISS`.
- [x] Implement service operations with exact boundaries:
  - `get`: inspect `repository.cache.synchronous().getIfPresent(sku)`, then call
    `repository.get(sku)` and return `HIT`, `MISS`, or `NOT_FOUND`;
  - `update`: direct-read current record, increment `version`, call
    `repository.put`; rethrow `CancellationException`; invalidate the key on
    non-cancellation failure;
  - `invalidate`, `clear`, `findAllFromDb`, `health` delegate to provider APIs.
- [x] Register routes `GET /`, `GET /products`, `GET /products/{sku}`,
  `PUT /products/{sku}`, `DELETE /products/{sku}/cache`,
  `DELETE /products/cache`, and `GET /cache/health`.
- [x] In application module, subscribe to `ApplicationStopped`, initialize
  seed data before routes, and close resources exactly once. If initialization
  fails, close the resources before rethrowing so a partially started app does
  not leak its pool. `main` starts Netty on `127.0.0.1:8080`.
- [x] Run route RED tests and verify GREEN, including mode-specific direct DB
  assertions within the live test application before shutdown.
  Evidence: application contract tests passed for cache hit/miss, invalidation,
  all three write modes, direct DB bypass, and structured errors.

## Task 5: Verify write-behind edge cases and lifecycle

**Files:** `R2dbcCaffeineRepositoryLifecycleTest.kt` and any minimal test-only
subclasses.

- [x] Add a test-only subclass whose `findByIdFromDb` suspends indefinitely;
  cancel the caller with `withTimeout` and assert `CancellationException` is
  preserved and the test completes.
- [x] Add a valid write-behind update, call `repository.close()`, and assert
  the final DB row contains the updated value and health state is `STOPPED`.
- [x] Add an overlong name to trigger H2 flush failure; poll
  `validateConsistency()` until `lastFlushError != null`, read the DB directly,
  invalidate the optimistic entry, and assert the prior DB value is restored.
  Close the failed repository and document that this is observable failure,
  not automatic retry or crash durability.
- [x] Close a write-behind repository before another `put`, assert
  `IllegalStateException`, cache miss, and `queueDepth == 0`; call close again
  without an exception.
- [x] Run the lifecycle test alone, then rerun the complete new module tests
  sequentially. Any retry-only pass requires diagnosis and a fresh run.
  Evidence: lifecycle and application tests completed sequentially with 10/10
  passing; failed-batch queue retention is asserted explicitly.

## Task 6: Add bilingual module and chapter documentation

**Files:** module `README.md`/`README.ko.md`, chapter/root READMEs.

- [x] Write the Korean module explanation first, then natural English parity.
  Include exact commands, API paths, module project name, mode comparison,
  same key/value table, pool/repository close order, cancellation, health/
  failure semantics, and deterministic H2 scope.
- [x] Explicitly separate provider `R2dbcCaffeineSnapshotCache` 기준 데이터
  캐시 as an opt-in contract and
  state that this example does not implement it; state current upstream
  issue/reference status from live sources without claiming unsupported
  guarantees.
- [x] Embed only PNG diagrams and keep EN/KO content source-equivalent in
  headings, tables, links, commands, asset references, and caveats.
- [x] Add module links and recommended order/commands to chapter 11 EN/KO and
  update root indexes only where the current structure exposes chapter modules.
- [x] Run the contextual Korean terminology audit and the naturalness checklist
  for every changed Korean README/KDoc-facing document; repair findings by
  context, not global replacement.
  Evidence: terminology audit findings=0 and bilingual asset-pair audit passed.

## Task 7: Create and validate architecture/sequence diagrams

**Files:** eight SVG/PNG assets under `docs/images/readme-diagrams`.

- [x] Record a semantic ledger for the two high-change assets: reader question,
  source paths, unique node IDs, closed edges, locale pair, and geometry
  invariants.
- [x] Draw architecture and sequence diagrams in SVG with concise labels,
  explicit endpoints, locale-equivalent concepts, and no Mermaid/Graphviz.
- [x] Run XML validation, `diagram-svg-text-normalize.py`, semantic audit,
  connector/endpoint/arrowhead/geometry audits, and render each SVG with
  `cairosvg <file>.svg -o <file>.png -s 2`.
- [x] Run `diagram-visual-audit.py` and `diagram-asset-pair-audit.py`; inspect
  every final PNG at full size after the last coordinate edit. Record dimensions,
  occupancy, margins, marker roles/sizes, failure counts, and visual notes in
  the checklist. Evidence: all four PNGs are opaque and balanced; architecture
  3200x2000 (occupancy .908, margins 58) and sequence 3680x3000 (occupancy .926,
  margins 62) passed visual/pair audits and full-size inspection.

## Task 8: Update workflow and run registration/static checks

**Files:** `.github/workflows/Examples.yml`, changed-task selector inputs,
catalog/module/docs/assets.

- [x] Add `11-high-performance/07-cache-strategies-r2dbc-caffeine/**` to both
  push and pull-request path filters.
- [x] Add `:07-cache-strategies-r2dbc-caffeine:test` to chapter 11 task list and
  its test-results/reports artifact paths.
- [x] Run `./gradlew projects`, new module H2 tests, full chapter 11 H2 test
  task, `python3 .github/scripts/changed-r2dbc-test-tasks.py` with a JSON
  change set containing module 07, and `actionlint .github/workflows/Examples.yml`.
  New module is `10/10`; full Chapter 11 is `03/06/07` green while existing
  02/04/05 fail independently at Testcontainers Docker discovery.
- [x] Run `git diff --check`, available static checks, and dependency resolution
  to prove the versionless catalog alias resolves through the central BOM.
  `:07...:check` and `:07...:koverXmlReport` passed; the module task graph has
  no Detekt task, so no separate Detekt report exists for this leaf.

## Task 9: Verify against spec, review, lesson, and commit

**Files:** approved spec/plan, checklist, `docs/review/` if a tracked review is
needed, lesson file, final branch diff.

- [x] Re-read the design and this plan; map every acceptance criterion to a
  source file and fresh command result using `step-5-verifier-checklist.md`.
- [x] Run the final six perspective review lenses in dependency order for the
  integrated diff (performance, stability, security, Ops, developer/API,
  user/caller) and main-session integration. Normalize findings to P0–P3 and
  repair until P0=0/P1=0.
- [x] Write the Korean lesson with context, decision, outcome, verification,
  any TDD/review miss, and one concrete future guard. Complete SPW-01..05 and
  keep it tracked.
- [x] Run `git diff --check`, inspect untracked files, and create a Lore commit
  whose intent explains why the provider example is a separate sibling. Use
  Korean commit prose and trailers:

```text
Constraint: 중앙 BOM 1.4.0와 provider 1.12.1 계약을 유지해야 한다
Rejected: Redisson 재사용과 `R2dbcCaffeineSnapshotCache` 구현 | provider 계약 범위를 벗어나므로 제외
Confidence: high
Scope-risk: broad
Directive: write-behind 실패를 자동 retry/durability로 해석하지 말 것
Tested: targeted module tests 10/10, Chapter 11 H2 command with existing
02/04/05 Testcontainers failures recorded, `./gradlew projects`, changed-task
selector, available `:07...:check`/Kover checks, `actionlint`, docs/diagram
audits, and `git diff --check` with exact results recorded in the commit body.
Not-tested: separate Detekt task is unavailable in this leaf; exact-head CI,
live review, and merge remain pending.
```

## Task 10: Authorized PR delivery and merge-ready handoff

- [x] Refresh guidance, issue metadata, PR template, and workflow rows
  immediately before push/PR (`CG-12A`).
- [x] Push `feat/issue-213-r2dbc-caffeine`, verify local/remote exact head,
  create Korean PR against `develop`, assign `debop`, mirror Issue #213
  milestone/labels, link `#213`, and end the body with `## DoD Status`.
- [ ] Read the live PR back, wait for exact-head CI, inspect reviews/threads,
  rerun any affected review/diagram proof, and report
  `Required checks: X/Y; N/A: N; Blocked: 0` with merge rows still unchecked.
- [ ] Stop at merge-ready until the user gives fresh explicit approval tied to
  the exact PR/head. Earlier plan approval does not authorize merge.
- [ ] After that approval only, run `gh pr merge --rebase --match-head-commit
  <exact-head>`; verify live merged state and merge SHA, fast-forward local
  `develop` from origin, and remove only the proven merged worktree/branch.

## Rollback and recovery

- Before code tests pass, revert only the new module/catalog/docs changes in the
  feature worktree; leave root `develop` and unrelated worktrees untouched.
- If provider resolution fails, remove only the new alias/module dependency and
  return to the last green catalog/module discovery proof.
- If a diagram audit fails, keep SVG source, repair one asset at a time, rerender
  PNG, and rerun all affected audits; never accept source-only success.
- If CI or review finds a P0/P1, diagnose from exact-head evidence, repair on
  the same feature branch, rerun targeted and affected broader checks, and
  republish the exact head without force unless explicitly required.
