# Issue #205 Checkpointable Exposed R2DBC Batch 설계

## 설계 상태

- Issue: [#205](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/205)
- 분류: Type A — 새 Gradle 모듈·provider 의존성·README·diagram·workflow 등록을 포함하는 full feature
- 배치: `13-ecosystem-integrations/09-checkpointable-r2dbc-batch`
- branch/worktree: `feat/issue-205-checkpointable-r2dbc-batch`
- 승인 경계: 승인된 실행 계획과 이 설계에 따라 구현을 시작한다.
- 기준 artifact: `io.github.bluetape4k.exposed:bluetape4k-exposed-batch:1.12.1`

## 문제와 목표

`exposed-workshop/13-ecosystem-integrations/11-checkpointable-batch`에는 JDBC
기반 checkpointable batch workshop이 있다. R2DBC workshop에는 동일한 실행
경계를 직접 검증할 수 있는 예제가 없으므로, Exposed R2DBC batch provider의
reader, processor, writer, metadata repository와 checkpoint/restart 계약을
coroutine-first 예제로 고정한다.

목표는 다음과 같다.

1. provider DSL을 직접 조합한 작은 R2DBC batch job을 제공한다.
2. H2 R2DBC에서 chunk commit, keyset checkpoint, skip, retry/backoff, timeout,
   cancellation과 restart를 결정적으로 검증한다.
3. published artifact의 실제 API와 package 경계를 README, KDoc, 테스트,
   diagram에 동일하게 반영한다.
4. 기존 JDBC sibling의 의미를 재사용하되 코드를 기계적으로 복사하지 않고
   R2DBC의 `suspendTransaction`과 Flow 경계를 보여 준다.

## 조사 근거와 provider 경계

| 근거 | 확인 결과 | 설계 의미 |
|---|---|---|
| `exposed-workshop/13-ecosystem-integrations/11-checkpointable-batch` | `JdbcBatchWorkshop.kt`와 테스트가 정상, 실패, skip, retry, timeout, cancellation/restart를 고정 | 같은 학습 목표를 R2DBC sibling으로 옮기되 R2DBC transaction/Flow 방식으로 재작성 |
| `bluetape4k-exposed` PR #747 / Issue #745 | failed checkpoint 보존/restart 버그가 수정된 provider 변경 | workshop에 provider workaround를 넣지 않고 provider 계약을 직접 사용 |
| published `bluetape4k-exposed-batch:1.12.1` | `ExposedR2dbcBatchJobRepository`, `ExposedR2dbcBatchReader`, `ExposedR2dbcBatchWriter`, `BatchJob`, `BatchStep` API가 존재 | source의 현재 unreleased package를 import하지 않고 catalog가 해석하는 artifact만 사용 |
| published 1.12.1 bytecode/source | metadata table은 `io.bluetape4k.batch.jdbc.tables.*`, checkpoint codec은 `io.bluetape4k.batch.internal.CheckpointJson` | R2DBC job도 published artifact의 이 package 경계를 따름. 이는 workaround가 아닌 artifact compatibility 경계 |
| 현재 workshop shared fixture | `AbstractR2dbcExposedTest`, `withDb`, `withTables`, `TestDB.H2`가 UTC/H2 R2DBC lifecycle을 제공 | test lifecycle을 새로 만들지 않고 shared helper와 `runTest`를 사용 |

`bluetape4k-exposed` 저장소의 최신 작업 트리에는 향후
`io.bluetape4k.batch.r2dbc.tables` package가 보이지만, 현재 workshop catalog의
central/BOM 해석은 1.12.1이다. 따라서 구현은 반드시 published 1.12.1의
`jdbc.tables` metadata package를 사용한다. provider가 향후 package를 바꾸는
것을 이 workshop에서 선제적으로 흉내 내지 않는다.

## 결정

새 sibling 모듈 `13-ecosystem-integrations/09-checkpointable-r2dbc-batch`를
추가한다. 현재 Chapter 13의 R2DBC 모듈이 `03`, `05`, `06`, `07`, `08`로
끝나므로 `09`는 기존 번호를 바꾸지 않고 새 batch integration의 의미를
드러낸다. `settings.gradle.kts`는 leaf directory를 자동 검색하므로 별도
include를 추가하지 않는다.

모듈은 `R2dbcBatchSourceTable`, `R2dbcBatchTargetTable`, 불변 source/target
record, options, schema helper, provider reader/processor/writer/job factory,
H2 R2DBC 테스트와 양쪽 README를 소유한다. `09`가 기존 모듈의 source나
shared test fixture를 수정하지 않는다.

## 대안과 기각 이유

### A안: JDBC runner를 R2DBC에서 그대로 감싸기 — 기각

blocking JDBC transaction과 `runBlocking`을 감싸면 R2DBC connection lifecycle,
Flow 소비, cancellation 재전파를 설명할 수 없다. R2DBC API를 직접 조합하는
것이 이 workshop의 학습 목적에 맞다.

### B안: provider를 우회한 custom `suspendTransaction` runner — 기각

custom runner는 provider의 checkpoint JSON, lease/version, retry/skip/timeout,
STOPPED 상태 전이를 복제하게 된다. 실제 provider contract를 검증하지 못하므로
provider DSL(`ExposedR2dbcBatchJobRepository`/reader/writer)을 사용한다.

### C안: Spring Batch 의존성 추가 — 기각

Issue의 범위는 bluetape4k batch provider의 R2DBC adapter workshop이다. Spring
Batch job repository와 transaction manager를 추가하면 Chapter 13의 작은
provider integration 경계와 dependency surface가 불필요하게 넓어진다.

## 모듈·API 계약

### Gradle

- `gradle/libs.versions.toml`에 버전 없는
  `exposed-batch = { module = "io.github.bluetape4k.exposed:bluetape4k-exposed-batch" }`
  alias를 추가한다.
- 새 모듈은 `alias(libs.plugins.exposed)`를 사용하고
  `implementation(project(":exposed-r2dbc-shared"))`, Exposed core/R2DBC,
  `bluetape4k-exposed-batch`, `bluetape4k-jackson3`, H2/R2DBC runtime과 기존
  JUnit/coroutines test dependency만 사용한다.
- 새 외부 dependency나 `buildSrc` 파일은 추가하지 않는다.

### Source API

패키지는 `exposed.examples.batch.r2dbc`로 고정한다.

- `R2dbcBatchSourceTable`: `r2dbc_batch_source`, `id` keyset,
  `name`, `value`.
- `R2dbcBatchTargetTable`: `r2dbc_batch_target`, `sourceId` primary key,
  uppercase `sourceName`, doubled `transformedValue`.
- `R2dbcSourceRecord`와 `R2dbcTargetRecord`: mutable 상태가 없는 data class.
- `R2dbcBatchOptions`: `jobName`, `parameters`, positive `chunkSize`/`pageSize`,
  `SkipPolicy`, `RetryPolicy`, `commitTimeout`을 가진다. blank job name과
  non-positive chunk/page size는 즉시 `IllegalArgumentException`으로 거부한다.
- `r2dbcBatchMetadataTables`: published provider의
  `BatchJobExecutionTable`, `BatchStepExecutionTable`와 source/target table의
  목록.
- `createR2dbcBatchSchema(database: R2dbcDatabase)`: caller-owned database에서
  `suspendTransaction(db = database)`로 `SchemaUtils.create`를 수행한다.
- `defaultR2dbcProcessor`, `r2dbcTargetWriter(database)`,
  `checkpointableR2dbcBatchJob(database, options, processor, writer)`,
  `runCheckpointableR2dbcBatch(database, options)`을 제공한다.

job factory는 다음 published provider 조합을 사용한다.

```kotlin
repository(ExposedR2dbcBatchJobRepository(database, CheckpointJson.jackson3()))
reader(ExposedR2dbcBatchReader(...))
writer(ExposedR2dbcBatchWriter(...))
```

모든 database access는 provider 내부 또는 workshop helper의
`suspendTransaction` 안에서 실행한다. `selectAll()`/reader Flow는 테스트와
helper에서 `toList()` 또는 명시적 collection으로 소비한다. job helper는
caller-owned `R2dbcDatabase`나 underlying pool을 close하지 않는다.

## 실행과 상태 흐름

```text
caller coroutine
    |
    v
R2dbc batch job DSL
    |
    +--> ExposedR2dbcBatchReader --keyset--> source Flow
    |
    +--> processor --chunk--> ExposedR2dbcBatchWriter --> target table
    |
    +--> commit --> checkpoint JSON --> published metadata tables
                         |
                         +--> cancellation: STOPPED
                         +--> restart: last committed key 이후부터 재개
```

provider의 chunk 순서는 write → `onChunkCommitted` → checkpoint persist이며,
successful chunk의 마지막 key만 다음 run의 시작점이 된다. target의
`sourceId` primary key는 restart 시 duplicate write가 관측 가능하도록 한다.
이 예제는 provider가 성공적으로 commit한 chunk를 재실행하지 않는 정상
restart 경계를 보여 주지만, 외부 side effect까지 exactly-once라고 주장하지
않는다. writer가 DB commit 뒤 예외를 던지는 at-least-once 상황과 외부 메시지
broker 연동은 범위 밖이다.

## 오류·실패 계약

| 상황 | 기대 동작 | 검증 |
|---|---|---|
| 정상 실행 | 모든 source가 target에 기록되고 step/job이 `COMPLETED` | normal test |
| processor 예외 + `SkipPolicy.ALL` | 해당 item만 skip, 나머지 write, `COMPLETED_WITH_SKIPS` | skip test |
| writer 일시 실패 + retry | bounded retry/backoff 뒤 성공 | retry test |
| chunk commit timeout | timed-out chunk가 부분 target row 없이 skip/실패 정책 적용 | timeout test |
| writer failure | report와 metadata가 `FAILED`로 남고 예외/attempt가 숨겨지지 않음 | failure test |
| coroutine cancellation | `CancellationException`을 삼키지 않고 cleanup 뒤 caller로 재전파, metadata는 `STOPPED` | cancellation test |
| cancellation 후 restart | 저장된 checkpoint 이후 source만 처리되고 target source key 중복 없음 | restart test |
| 잘못된 options | job name/chunk/page 경계가 즉시 `IllegalArgumentException` | options test |
| schema helper | provider metadata와 source/target table이 생성됨 | schema test |
| connection lifecycle | job helper가 caller-owned database/pool을 닫지 않음 | source read-back와 targeted test |

retry/backoff와 timeout은 짧고 결정적인 test 값을 사용한다. H2 R2DBC를
기본 backend로 삼고 `USE_FAST_DB=true` 환경의 shared convention을 존중한다.
다른 DB matrix와 Docker/Testcontainers는 이 모듈의 기본 DoD가 아니다.

## 테스트 설계

테스트는 `AbstractR2dbcExposedTest`를 확장하고 `runTest`/shared
`withDb`·`withTables` lifecycle을 사용한다. test fixture는 source 8~10건을
seed하고 H2 `R2dbcDatabase`를 caller가 소유한다.

1. 정상 실행: read/write/skip/checkpoint/count와 transformed row를 검증한다.
2. 실패 및 retry: 첫/두 번째 writer call failure, attempt count, metadata
   status를 검증한다.
3. skip: 짝수 source에서 processor 예외를 내고 성공한 홀수만 target에 남긴다.
4. timeout: `delay` writer와 짧은 `commitTimeout`으로 부분 write가 없는지
   검증한다.
5. cancellation/restart: `CompletableDeferred` 두 개로 첫 chunk commit과
   다음 write 진입을 동기화하고, child를 취소한 뒤 `STOPPED`, checkpoint JSON,
   재실행 후 source key 집합을 검증한다. `CancellationException`은 catch-all로
   변환하지 않는다.
6. schema/options: metadata table 존재와 positive option validation을
   고정한다.

## 문서·diagram·등록 범위

- 새 module에 `README.md`와 `README.ko.md`를 source-equivalent section
  순서로 추가한다. 목적, provider API, metadata package compatibility,
  chunk/checkpoint/restart, failure contract, at-least-once 경계, H2 test
  command와 기존 JDBC sibling 링크를 포함한다.
- `13-ecosystem-integrations/README.md/.ko.md`와 root
  `README.md/.ko.md`의 Chapter 13 module map/verification command에 `09`를
  추가한다.
- `.github/workflows/Examples.yml` Chapter 13 H2 job에
  `:09-checkpointable-r2dbc-batch:test`와 report 경로를 추가한다.
- `docs/images/readme-diagrams/`에 동일 topology의 English/Korean
  architecture 및 lifecycle SVG/PNG pair를 추가한다. README에는 PNG만
  embed하고 SVG는 source asset으로 보존한다. diagram node는 caller, job DSL,
  R2DBC reader, processor, writer, metadata checkpoint, source/target DB의
  실제 source contract만 표현한다.
- diagram semantic ledger에는 source anchors, unique IDs, closed edges,
  locale topology equivalence를 기록하고 SVG→PNG scale 2, XML/text/connector/
  arrowhead/geometry/asset-pair audit와 full-size PNG inspection을 수행한다.
- 모든 KDoc와 Korean README prose는 한국어, English README prose는 영어로
  작성하며 API names, paths, commands, URLs, exact exception/status tokens는
  보존한다.

## 호환성·범위 밖

- catalog/BOM이 해석하는 현재 provider 1.12.1에 맞춘다. unreleased local
  provider source package나 #745 workaround를 import/복사하지 않는다.
- Spring Batch, JDBC transaction, `runBlocking`, custom transaction manager,
  external broker, exactly-once side effect, production scheduler, Actuator,
  Testcontainers DB matrix, release/tag는 추가하지 않는다.
- 기존 module `03`, `05`, `06`, `07`, `08`, shared fixture와 root develop branch는
  직접 수정하지 않고 등록 문서/workflow만 필요한 범위에서 변경한다.

## 수용 기준과 DoD

- [ ] `:09-checkpointable-r2dbc-batch`가 `./gradlew projects`에 나타난다.
- [ ] published provider API를 직접 사용하고 모든 DB access가 R2DBC
      `suspendTransaction` 경계를 따른다.
- [ ] 정상, failure, skip, retry/backoff, timeout, cancellation→STOPPED,
      checkpoint restart/no duplicate, schema/options 테스트가 H2에서 통과한다.
- [ ] module/root/chapter README, workflow module map/report, paired diagram
      SVG/PNG와 semantic ledger가 source-equivalent로 등록된다.
- [ ] `git diff --check`, targeted compile/test, Kover/detekt 가능한 검사가
      통과하고 baseline timeout은 별도 validation gap으로 기록된다.
- [ ] Type A review artifact와 Korean lesson이 PR 전에 존재한다.
- [ ] PR body는 Korean metadata mirror(issue #205, assignee `debop`, milestone
      `1.4.0`, labels)를 포함하고 마지막 section이 `## DoD Status`이다.
- [ ] merge는 fresh exact-head approval, green required checks와 함께
      `gh pr merge --rebase --match-head-commit`으로만 수행한다.

## Writer gate (SPW)

- SPW-01: 완료 — Issue, source paths, published artifact, API/package boundary,
  failure/unsupported contract를 기록했다.
- SPW-02: 완료 — 문제, alternatives, architecture, data flow, tests,
  compatibility, acceptance와 DoD를 포함했다.
- SPW-03: 완료 — Korean technical register를 적용하고 identifiers, commands,
  URLs, exact status/exception tokens를 보존했다.
- SPW-04: 완료 — JDBC counterpart, provider artifact/source, shared fixture와
  package decision을 직접 확인한 근거를 남겼다.
- SPW-05: 완료 — Markdown read-back과 placeholder/contradiction/scope scan을
  계획 작성 전에 수행했고 unresolved 항목이 없다.
