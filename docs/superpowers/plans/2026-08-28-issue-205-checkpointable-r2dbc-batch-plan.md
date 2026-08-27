# Checkpointable Exposed R2DBC Batch 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Issue #205의 승인된 설계대로 `13-ecosystem-integrations/09-checkpointable-r2dbc-batch`를 추가하여 published Exposed R2DBC batch provider의 chunk commit, checkpoint/restart, skip, retry, timeout, cancellation 경계를 H2 R2DBC에서 실행 가능한 예제로 고정한다.

**Architecture:** 새 sibling 모듈은 `exposed.examples.batch.r2dbc` 패키지에서 source/target table, immutable record, options, schema helper, provider reader/processor/writer/job factory를 소유한다. `ExposedR2dbcBatchJobRepository`, `ExposedR2dbcBatchReader`, `ExposedR2dbcBatchWriter`를 `R2dbcDatabase`와 함께 직접 조합하고 caller-owned database/pool은 close하지 않는다. published `1.12.1`의 metadata package가 `io.bluetape4k.batch.jdbc.tables.*`인 사실을 그대로 사용하며 custom runner, JDBC API, `runBlocking`, Spring Batch는 추가하지 않는다.

**Tech Stack:** catalog/BOM이 해석하는 Kotlin 2.4.0, Exposed 1.4.0, `bluetape4k-dependencies:1.4.0`, `bluetape4k-exposed-batch:1.12.1`, Exposed R2DBC, `bluetape4k-jackson3`, Kotlin Coroutines/Flow, H2 R2DBC, JUnit 5, shared `AbstractR2dbcExposedTest`/`withDb`/`withTables`.

---

## 실행 전 고정 사항

- Worktree: `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-205-checkpointable-r2dbc-batch`
- Branch: `feat/issue-205-checkpointable-r2dbc-batch`
- Issue: [#205](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/205)
- 설계: `docs/superpowers/specs/2026-08-28-issue-205-checkpointable-r2dbc-batch-design.md`
- 기준 merge base: `origin/develop`
- 사용자가 승인한 Type A 범위와 이 설계에서 material scope change는 없다. implementation은 이 plan의 순서대로 진행한다.
- 모든 public KDoc·Korean README·Issue/PR/commit prose는 한국어로 작성한다. English README는 동일 구조의 영어 prose로 작성한다.
- 모든 commit은 Lore protocol trailer를 사용한다. PR은 `develop` base와 이 branch head로 생성하고, merge는 fresh exact-head approval 뒤 `--rebase`만 사용한다.

## 파일 소유권과 변경 지도

| 순서 | 파일 | 책임 |
|---|---|---|
| T1 | `gradle/libs.versions.toml`, `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/build.gradle.kts` | provider alias와 새 Gradle project dependency |
| T2–T4 | `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/main/kotlin/exposed/examples/batch/r2dbc/R2dbcBatchWorkshop.kt` | table, record, options, schema, reader/processor/writer/job DSL |
| T2–T4 | `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/test/kotlin/exposed/examples/batch/r2dbc/R2dbcBatchWorkshopTest.kt` | H2 RED/GREEN 정상·실패·skip·retry·timeout·cancellation/restart·schema/options 테스트 |
| T5 | `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/README.md`, `README.ko.md` | source-equivalent reader-facing module guide |
| T6 | `docs/review/issue-205-r2dbc-batch-diagram-semantic-ledger.json`, `docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-{architecture,lifecycle}-01-{en,ko}.{svg,png}` | paired architecture/lifecycle visual assets와 semantic ledger |
| T7 | `13-ecosystem-integrations/README.md`, `README.ko.md`, root `README.md`, `README.ko.md`, `.github/workflows/Examples.yml` | Chapter 13/root module map, command, CI report 등록 |
| T8 | `docs/review/issue-205-*`, `docs/lessons/2026-08-28-issue-205-checkpointable-r2dbc-batch.md` | six-lens review, verification evidence, Korean lesson |

기존 `03`, `05`, `06`, `07`, `08` 모듈과 `00-shared/exposed-r2dbc-shared` source는
수정하지 않는다. settings의 leaf auto-discovery에 의존하므로 수동 `include`를
추가하지 않는다.

---

## Task 1: catalog와 빈 module을 등록한다

**Files:**

- Modify: `gradle/libs.versions.toml`의 `[libraries]` provider alias 인접 영역
- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/build.gradle.kts`

- [ ] **Step 1: RED project discovery 확인**

  ```bash
  ./gradlew projects --console=plain | rg '09-checkpointable-r2dbc-batch'
  ```

  Expected before directory/build exists: no matching project. This is a
  discovery baseline, not a failure of the repository.

- [ ] **Step 2: provider alias를 추가한다**

  `gradle/libs.versions.toml`의 Exposed/bluetape4k alias 묶음에 다음 한 줄을
  추가한다. version은 central BOM에 맡긴다.

  ```toml
  exposed-batch = { module = "io.github.bluetape4k.exposed:bluetape4k-exposed-batch" }
  ```

- [ ] **Step 3: module build를 작성한다**

  ```kotlin
  plugins {
      alias(libs.plugins.exposed)
  }

  dependencies {
      implementation(project(":exposed-r2dbc-shared"))
      implementation(libs.exposed.batch)
      implementation(libs.jetbrains.exposed.core)
      implementation(libs.jetbrains.exposed.r2dbc)
      implementation(libs.exposed.r2dbc)
      implementation(libs.bluetape4k.jackson3)

      runtimeOnly(libs.h2.v2)
      runtimeOnly(libs.r2dbc.h2)
      runtimeOnly(libs.r2dbc.pool)

      testImplementation(libs.bluetape4k.junit5)
      testImplementation(libs.kotlinx.coroutines.test)
  }
  ```

- [ ] **Step 4: project discovery와 dependency graph를 확인한다**

  ```bash
  ./gradlew projects --console=plain
  ./gradlew :09-checkpointable-r2dbc-batch:dependencies --configuration testRuntimeClasspath --console=plain
  ```

  Expected: project appears and `bluetape4k-exposed-batch` resolves through the
  current catalog/BOM. If alias generation differs, use the generated
  `libs.exposed.batch` accessor reported by Gradle rather than hard-coding a
  version.

## Task 2: RED 테스트와 R2DBC fixture를 먼저 고정한다

**Files:**

- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/test/kotlin/exposed/examples/batch/r2dbc/R2dbcBatchWorkshopTest.kt`
- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/test/resources/junit-platform.properties`
- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/test/resources/logback-test.xml`

- [ ] **Step 1: RED 테스트 골격을 작성한다**

  Test class는 `AbstractR2dbcExposedTest`를 확장하고 각 suspend test는
  `runTest` 또는 repository의 `runSuspendIO` convention을 사용한다. 아직
  source symbols가 없으므로 다음 unresolved API를 명시한 테스트를 먼저
  작성한다.

  ```kotlin
  class R2dbcBatchWorkshopTest : AbstractR2dbcExposedTest() {
      @Test
      fun `normal execution persists transformed rows and checkpoint`() = runTest {
          val database = preparedDatabase("normal", 1..8)
          val report = runCheckpointableR2dbcBatch(database, R2dbcBatchOptions(chunkSize = 3))
          report shouldBeInstanceOf BatchReport.Success::class
          report.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED
          report.stepReports.single().checkpoint shouldBeEqualTo 8L
          targetRows(database).map { it.sourceId } shouldBeEqualTo (1L..8L).toList()
      }
  }
  ```

- [ ] **Step 2: RED를 실행하고 unresolved output을 저장한다**

  ```bash
  ./gradlew :09-checkpointable-r2dbc-batch:test \
    --tests '*R2dbcBatchWorkshopTest' -PuseDB=H2 --no-daemon --console=plain
  ```

  Expected: source tables/functions are not yet defined. Do not weaken the
  assertion or add a fake provider implementation to make RED pass.

- [ ] **Step 3: shared fixture 사용을 고정한다**

  `preparedDatabase(name, values)`는 `R2dbcDatabase.connect`로 H2 memory
  database를 만들고 `createR2dbcBatchSchema(database)`와
  `R2dbcBatchSourceTable.batchInsert` seed를 호출한다. table read-back은
  `suspendTransaction(db = database) { ...selectAll().map { ... } }`에서
  `toList()`로 소비한다. test database/pool은 helper가 소유하고 production
  job factory가 닫지 않는다는 경계를 유지한다.

## Task 3: 최소 source API와 provider job을 구현한다

**Files:**

- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/src/main/kotlin/exposed/examples/batch/r2dbc/R2dbcBatchWorkshop.kt`

- [ ] **Step 1: table/record/options를 작성한다**

  ```kotlin
  object R2dbcBatchSourceTable : Table("r2dbc_batch_source") {
      val id = long("id").autoIncrement()
      val name = varchar("name", 255)
      val value = integer("value")
      override val primaryKey = PrimaryKey(id)
  }

  object R2dbcBatchTargetTable : Table("r2dbc_batch_target") {
      val sourceId = long("source_id")
      val sourceName = varchar("source_name", 255)
      val transformedValue = integer("transformed_value")
      override val primaryKey = PrimaryKey(sourceId)
  }

  data class R2dbcSourceRecord(val id: Long, val name: String, val value: Int)
  data class R2dbcTargetRecord(val sourceId: Long, val sourceName: String, val transformedValue: Int)
  ```

  `R2dbcBatchOptions`는 JDBC sibling의 option shape을 R2DBC 이름으로 유지하고
  `jobName.isNotBlank()`, `chunkSize > 0`, `pageSize > 0`를 `init`에서
  `require`한다. 기본 `commitTimeout`은 `BatchDefaults.COMMIT_TIMEOUT`을
  사용하며 `SkipPolicy.NONE`, `RetryPolicy.NONE`을 기본값으로 둔다.

- [ ] **Step 2: published metadata와 schema helper를 연결한다**

  ```kotlin
  val r2dbcBatchMetadataTables: List<Table> = listOf(
      BatchJobExecutionTable,
      BatchStepExecutionTable,
      R2dbcBatchSourceTable,
      R2dbcBatchTargetTable,
  )

  suspend fun createR2dbcBatchSchema(database: R2dbcDatabase) {
      suspendTransaction(db = database) {
          SchemaUtils.create(*r2dbcBatchMetadataTables.toTypedArray())
      }
  }
  ```

  Metadata imports는 `io.bluetape4k.batch.jdbc.tables.*`를 사용한다. published
  1.12.1에 없는 unreleased `batch.r2dbc.tables`를 import하지 않는다.

- [ ] **Step 3: reader와 writer를 provider API로 조합한다**

  `ExposedR2dbcBatchReader`는 `database`, source table, `id` key column,
  `pageSize`, row mapper, `R2dbcSourceRecord::id`, `keyClass = Long::class`
  을 넘긴다. `minKey`/`maxKey`는 default parameter를 사용한다. row mapper는
  `suspend` lambda에서 `ResultRow`의 세 column을 읽는다.

  `r2dbcTargetWriter(database)`는 `ExposedR2dbcBatchWriter(database,
  R2dbcBatchTargetTable)`의 bind lambda로 세 target column을 채운다.
  writer에서 `batchInsert`나 JDBC transaction을 직접 호출하지 않는다.

- [ ] **Step 4: job DSL과 public runner를 구현한다**

  ```kotlin
  fun checkpointableR2dbcBatchJob(
      database: R2dbcDatabase,
      options: R2dbcBatchOptions = R2dbcBatchOptions(),
      processor: BatchProcessor<R2dbcSourceRecord, R2dbcTargetRecord> = defaultR2dbcProcessor,
      writer: BatchWriter<R2dbcTargetRecord> = r2dbcTargetWriter(database),
  ): BatchJob = batchJob(options.jobName) {
      repository(ExposedR2dbcBatchJobRepository(database, CheckpointJson.jackson3()))
      params(options.parameters)
      step<R2dbcSourceRecord, R2dbcTargetRecord>("transform-and-write") {
          reader(r2dbcSourceReader(database, options.pageSize))
          processor(processor)
          writer(writer)
          chunkSize(options.chunkSize)
          skipPolicy(options.skipPolicy)
          retryPolicy(options.retryPolicy)
          commitTimeout(options.commitTimeout)
      }
  }

  suspend fun runCheckpointableR2dbcBatch(
      database: R2dbcDatabase,
      options: R2dbcBatchOptions = R2dbcBatchOptions(),
  ): BatchReport = checkpointableR2dbcBatchJob(database, options).run()
  ```

  `CheckpointJson.jackson3()`는 `io.bluetape4k.batch.internal`에서 import한다.
  `database`/pool close는 이 함수에 넣지 않는다.

- [ ] **Step 5: compile와 첫 GREEN을 확인한다**

  ```bash
  ./gradlew :09-checkpointable-r2dbc-batch:compileKotlin \
    :09-checkpointable-r2dbc-batch:test \
    --tests '*normal*' -PuseDB=H2 --no-daemon --console=plain
  ```

  Expected: provider job completes and target IDs `1..8`, checkpoint `8L` are
  observed. Signature mismatch는 published bytecode/source를 다시 확인해
  최소 수정하고, custom adapter를 추가하지 않는다.

## Task 4: 실패·skip·retry·timeout·cancellation/restart 테스트를 완성한다

**Files:**

- Modify: `.../src/test/kotlin/exposed/examples/batch/r2dbc/R2dbcBatchWorkshopTest.kt`

- [ ] **Step 1: failure/skip/retry 테스트를 추가한다**

  - `FailOnceWriter`는 두 번째 `write`에서 한 번 예외를 발생시키고
    `RetryPolicy.NONE`으로 `BatchReport.Failure`, `FAILED` metadata와 첫 chunk
    row만 검증한다.
  - processor가 짝수 value에서 `IllegalArgumentException`을 던지고
    `SkipPolicy.ALL`을 사용하면 홀수 source ID만 남고
    `COMPLETED_WITH_SKIPS`/skip count를 검증한다.
  - `FailFirstWriter`는 첫 attempt만 실패하고
    `RetryPolicy(maxAttempts = 2, delay = 1.milliseconds)`로 성공하며 attempt
    count가 2인지 검증한다.

- [ ] **Step 2: timeout 테스트를 추가한다**

  `SlowWriter(delegate, 50.milliseconds)`와 `chunkSize = 3`,
  `commitTimeout = 5.milliseconds`, `SkipPolicy.maxSkips(3)`를 사용한다.
  report가 `PartiallyCompleted`/`COMPLETED_WITH_SKIPS`, skip count 3,
  write count 0이고 target row가 없음을 검증한다. `Thread.sleep`이나
  `runBlocking`을 사용하지 않는다.

- [ ] **Step 3: cancellation/restart 테스트를 추가한다**

  `CancellationWriter`는 첫 write를 delegate에 전달한 뒤
  `firstWriteCompleted.complete(Unit)`을 호출하고, 두 번째 write에 진입하면
  `secondWriteStarted.complete(Unit)` 후 `awaitCancellation()`한다. `async`
  job을 시작하고 두 deferred를 기다린 뒤 `running.cancel()`한다. caller는
  `CancellationException`을 받아야 하며 metadata status가 `STOPPED`,
  checkpoint JSON에 `java.lang.Long`과 payload `3`이 있어야 한다.

  같은 `jobName`/parameters로 `runCheckpointableR2dbcBatch`를 다시 실행한 뒤
  report가 `Success`/`COMPLETED`이고 target source IDs가 `1..8`의 unique 집합인지
  검증한다. checkpoint 이후부터 읽는다는 것을 중복 row count로 증명한다.

- [ ] **Step 4: schema/options 테스트를 추가한다**

  `r2dbcBatchMetadataTables` 각 table의 `selectAll().count()`가 0이고 table
  이름 집합이 일치하는지, target primary key가 `sourceId`인지 확인한다.
  blank job name, zero chunk/page size는 `assertFailsWith<IllegalArgumentException>`으로
  확인한다.

- [ ] **Step 5: targeted test를 순서대로 실행한다**

  ```bash
  ./gradlew :09-checkpointable-r2dbc-batch:test \
    --tests '*R2dbcBatchWorkshopTest' -PuseDB=H2 --no-daemon --console=plain
  ```

  Expected: all test methods pass; cancellation test는 cancellation을 일반
  failure report로 바꾸지 않는다. Failure 시 stack trace와 metadata row를
  먼저 읽고 provider source contract에 맞춰 최소 수정한다.

## Task 5: module README 두 locale를 작성한다

**Files:**

- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/README.md`
- Create: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch/README.ko.md`

- [ ] **Step 1: 동일 section 구조를 작성한다**

  두 문서의 순서를 `목적/Scope → provider contract → schema/API → execution
  flow → checkpoint/restart → failure matrix → exactly-once boundary → test
  command → JDBC sibling 비교 → limitations → diagram`으로 동일하게 한다.
  English README는 영어 설명, Korean README는 자연스러운 한국어 설명을 쓴다.

- [ ] **Step 2: source-backed code excerpt와 경계를 기록한다**

  README에는 `R2dbcBatchOptions`, `checkpointableR2dbcBatchJob`,
  `suspendTransaction`, `CheckpointJson.jackson3()`를 실제 source와 동일하게
  보여 준다. metadata package가 published `1.12.1`에서 `jdbc.tables`인 이유와
  unreleased `batch.r2dbc.tables`를 사용하지 않는 이유를 명시한다.
  provider checkpoint 이후의 DB restart 경계만 exactly-once처럼 관찰되며
  외부 side effect는 at-least-once라는 한계를 명확히 쓴다.

- [ ] **Step 3: command와 링크를 read-back한다**

  ```bash
  ./gradlew :09-checkpointable-r2dbc-batch:test -PuseDB=H2
  ./gradlew :09-checkpointable-r2dbc-batch:build -PuseDB=H2
  ```

  JDBC sibling path와 provider manual URL이 실제 존재하는지 확인하고
  `audit-korean-terms.mjs` 및 Markdown link/asset check를 실행한다.

## Task 6: paired architecture/lifecycle diagram을 만든다

**Files:**

- Create: `docs/review/issue-205-r2dbc-batch-diagram-semantic-ledger.json`
- Create: `docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-architecture-01-en.svg`
- Create: `docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-architecture-01-ko.svg`
- Create: `docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-lifecycle-01-en.svg`
- Create: `docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-lifecycle-01-ko.svg`
- Create: corresponding four `.png` files generated from SVG at scale 2

- [ ] **Step 1: semantic ledger를 작성한다**

  ledger에는 reader question, source anchors, unique node IDs, edge list,
  locale topology equivalence, complexity decision을 기록한다. Source anchor는
  `R2dbcBatchWorkshop.kt`의 table, reader, writer, job factory와 test의
  cancellation/restart method를 가리킨다.

- [ ] **Step 2: 동일 topology SVG를 작성한다**

  architecture nodes: `caller coroutine`, `BatchJob DSL`, `R2dbcReader`,
  `processor`, `R2dbcWriter`, `R2dbcDatabase`, `source/target tables`,
  `metadata checkpoint tables`. lifecycle edges: read chunk → process/write →
  commit → checkpoint → next key; cancellation → STOPPED → restart. English와
  Korean은 node ID/edge를 동일하게 유지하고 label만 번역한다.

- [ ] **Step 3: SVG/PNG audit를 실행한다**

  ```bash
  for locale in en ko; do
    for kind in architecture lifecycle; do
      svg="docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-${kind}-01-${locale}.svg"
      png="docs/images/readme-diagrams/13-09-checkpointable-r2dbc-batch-${kind}-01-${locale}.png"
      xmllint --noout "$svg"
      cairosvg "$svg" -o "$png" -s 2
      python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-connector-audit.py "$svg"
      python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-arrowhead-audit.py "$svg"
      python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-endpoint-audit.py "$svg"
      python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-geometry-audit.py "$svg"
    done
  done
  python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-semantic-audit.py \
    --repo-root . --json docs/review/issue-205-r2dbc-batch-diagram-semantic-ledger.json
  ```

  PNG를 full-size로 inspect하고 text overlap, clipped label, arrowhead 방향,
  missing pair를 확인한다. README에는 대응 PNG만 embed한다.

## Task 7: root/chapter README와 Examples workflow를 등록한다

**Files:**

- Modify: `13-ecosystem-integrations/README.md`, `README.ko.md`
- Modify: root `README.md`, `README.ko.md`
- Modify: `.github/workflows/Examples.yml`

- [ ] **Step 1: Chapter 13 module map과 verification command를 갱신한다**

  두 locale 표에 `09-checkpointable-r2dbc-batch` row를 추가하고 기존 5개
  module command에 `:09-checkpointable-r2dbc-batch:test`를 추가한다. 문서의
  모듈 수, description, command가 서로 일치하는지 확인한다.

- [ ] **Step 2: root README parity를 갱신한다**

  root README의 Chapter 13 module map/description와 Korean counterpart를
  동일한 row/order로 갱신한다. root 문서에 source에 없는 runtime promise를
  추가하지 않는다.

- [ ] **Step 3: Examples workflow를 갱신한다**

  Chapter 13 H2 smoke test와 report aggregation에
  `:09-checkpointable-r2dbc-batch:test` 및 해당 `build/test-results`/
  `build/reports/tests` 경로를 추가한다. 기존 `--continue`, `-PuseDB=H2`,
  path filter를 보존한다.

- [ ] **Step 4: registration read-back**

  ```bash
  ./gradlew projects --console=plain
  git diff --check
  rg -n '09-checkpointable-r2dbc-batch' README.md README.ko.md \
    13-ecosystem-integrations/README.md 13-ecosystem-integrations/README.ko.md \
    .github/workflows/Examples.yml
  ```

## Task 8: Type A verification/review/lesson을 기록한다

**Files:**

- Create: `docs/review/issue-205-plan-review.md`
- Create: `docs/review/issue-205-implementation-review.md`
- Create: `docs/review/issue-205-verification.md`
- Create: `docs/lessons/2026-08-28-issue-205-checkpointable-r2dbc-batch.md`

- [ ] **Step 1: six-lens read-only review를 통합한다**

  performance, stability, security, operator/Ops, developer/API, user/caller
  관점별로 exact file/line evidence, priority, required edit, rerun 여부를
  기록한다. P0/P1은 0이 될 때까지 최소 수정 후 affected review를 다시 읽는다.
  native subagent는 사용하지 않고 main session에서 각 관점을 독립적으로
  수행하되, 관점별 결론을 분리해서 artifact에 남긴다.

- [ ] **Step 2: targeted와 proportional validation을 실행한다**

  ```bash
  ./gradlew :09-checkpointable-r2dbc-batch:test -PuseDB=H2 --no-daemon --console=plain
  ./gradlew :09-checkpointable-r2dbc-batch:build -PuseDB=H2 --no-daemon --console=plain
  ./gradlew :09-checkpointable-r2dbc-batch:koverHtmlReport --no-daemon --console=plain
  ./gradlew projects --console=plain
  git diff --check
  ```

  가능하면 detekt task도 실행하되 task가 없는 경우 `N/A`로 기록한다. root
  baseline `./gradlew test`는 이미 300초 내 완료되지 않았으므로 반복하지 않고
  targeted evidence와 known gap으로 기록한다. Chapter 13 H2 smoke는 이후
  targeted tests가 통과한 뒤 한 번 실행한다.

- [ ] **Step 3: verification report를 작성한다**

  각 command의 exit code, test count, Kover/detekt 결과, diagram audit,
  workflow/readme registration, baseline timeout을 표로 기록한다. `Required
  checks: X/Y; N/A: N; Blocked: N` 형식과 unchecked item을 포함한다.

- [ ] **Step 4: Korean lesson을 PR 전 commit에 포함한다**

  lesson에는 provider 1.12.1 package boundary, #745 workaround 금지, R2DBC
  Flow/cancellation test synchronization, target PK duplicate observability,
  future provider upgrade 주의점을 source-backed evidence와 함께 기록한다.

## Task 9: Lore commit, PR, CI와 merge-ready 상태를 마친다

- [ ] **Step 1: transient workflow input을 제거한다**

  `issue-205-lane*.json`, `issue-205-topology*.json` 같은 helper 입력 파일은
  source artifact가 아니므로 `apply_patch`로 삭제한다. `.bluetape/` runtime
  state는 ignored evidence로 남긴다.

- [ ] **Step 2: Lore commit을 만든다**

  commit message intent와 trailers는 한국어로 작성한다.

  ```text
  R2DBC batch checkpoint 경계를 실행 가능한 workshop으로 고정

  Constraint: published bluetape4k-exposed-batch 1.12.1 API와 H2 R2DBC만 사용
  Rejected: custom runner와 unreleased r2dbc metadata package | provider 계약을 가림
  Confidence: high
  Scope-risk: moderate
  Directive: provider package가 변경되면 catalog artifact와 metadata imports를 함께 재검증
  Tested: targeted module test/build, projects, diagram audits, git diff --check
  Not-tested: root full test baseline은 300초 내 완료되지 않음
  ```

- [ ] **Step 3: branch를 push하고 PR을 생성한다**

  PR repo `bluetape4k/exposed-r2dbc-workshop`, base `develop`, head
  `feat/issue-205-checkpointable-r2dbc-batch`로 생성한다. PR body는 Korean이고
  Issue #205, `debop`, milestone `1.4.0`, labels와 evidence links를 mirror하며
  마지막 section heading은 반드시 `## DoD Status`이다. 생성 직후 `gh pr view
  --json`으로 title/body/base/head/labels/milestone/assignees를 live read-back한다.

- [ ] **Step 4: CI/review를 확인하고 merge-ready를 보고한다**

  required checks, review threads, mergeability, exact head SHA와 PR body DoD를
  다시 확인한다. CI 또는 review가 pending이면 수정 후 새 SHA를 검증한다.
  이 단계에서는 merge하지 않고 merge-ready report만 사용자에게 보고한다.

- [ ] **Step 5: fresh approval 뒤 rebase merge와 local sync를 수행한다**

  사용자의 새 `승인`을 exact live head SHA에 묶어 확인한 뒤에만 다음 형태로
  실행한다.

  ```bash
  gh pr merge <PR_NUMBER> --rebase --match-head-commit <EXACT_HEAD_SHA>
  git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop fetch origin
  git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop switch develop
  git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop pull --ff-only origin develop
  ```

  merge 후 PR가 `MERGED`, root `develop`가 `origin/develop`과 같은 SHA인지,
  feature worktree가 clean인지 확인한다. auto-merge는 활성화하지 않는다.

---

## 현재 검증 blocker

`bluetape4k-exposed-batch:1.12.1`은 2026-08-06 release이며, upstream #747은
2026-08-27 merge된 `[2.0.0]` 변경이다. 현재 Maven Central에는 2.0.0 또는
#747 backport artifact가 없어 실패 후 keyset restart를 검증할 수 없다.
승인된 “workshop-local workaround 금지” 경계를 유지하는 동안 STOPPED
restart만 완료로 기록하고, FAILED restart 회귀는 provider release 후
추가한다.

## Plan self-review

- **Spec coverage:** provider API/package boundary는 Tasks 1–3, all failure
  states와 restart/no-duplicate는 Task 4, source-equivalent docs는 Task 5,
  diagram pair/ledger는 Task 6, module/root/workflow registration은 Task 7,
  Type A review/lesson/verification/PR DoD는 Tasks 8–9에 각각 trace된다.
- **Placeholder scan:** 계획 전체에 미완성 placeholder, 모호한 구현 지시,
  또는 구현 세부를 생략하는 문구가 없다. 각 변경 task에 실제 path, command,
  expected evidence와 핵심 signature를 기재했다.
- **Type consistency:** source API는 `R2dbc*` prefix로 정의하고 모든 test,
  README, diagram anchor, workflow path에서 동일한 module/package/function
  이름을 사용한다. provider metadata만 published `jdbc.tables`로 고정된다.
- **Risk/rollback:** dependency/catalog, new module, docs/workflow, lesson/review
  commit을 분리 가능한 단위로 유지하고, provider signature mismatch는 source
  contract 확인 후 module-local 최소 수정만 한다. 기존 sibling/shared fixture를
  건드리지 않아 rollback은 새 module/registration diff 제거로 제한된다.
