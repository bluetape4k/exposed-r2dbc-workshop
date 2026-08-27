# Issue #205 구현 계획 통합 검토

검토일: 2026-08-28
대상: `feat/issue-205-checkpointable-r2dbc-batch`
범위: Type A 새 sibling 모듈, published batch provider 조합, H2 R2DBC
검증, EN/KO README, paired diagram, Chapter 13 workflow 등록과 PR 전 DoD

## 독립 관점 결과

| 관점 | 확인한 위험 | 계획·구현 반영 | 최종 상태 |
|---|---|---|---|
| Security | 실패 메시지나 provider 경계를 과장하면 운영 오용 가능 | caller-owned database, demo-only H2, exactly-once 비주장, 외부 broker/credential 범위 제외를 문서화 | P0/P1 없음 |
| Correctness/API | unreleased `batch.r2dbc.tables` 또는 #745 workaround를 사용하면 published 계약과 어긋남; 1.12.1에는 #747 수정이 없음 | `bluetape4k-exposed-batch:1.12.1`의 `jdbc.tables` metadata와 `ExposedR2dbcBatch*` API를 직접 조합하되 FAILED restart는 release 전 보류 | P1 blocker |
| Performance/resource | page/chunk 경계와 timeout에서 partial write·checkpoint drift 위험 | keyset reader, chunk commit 뒤 checkpoint, 짧은 timeout과 H2 target PK를 테스트 | P0/P1 없음 |
| Coroutine/stability | cancellation을 삼키거나 caller pool을 job이 닫을 위험 | `suspendTransaction`, `CancellationException` 재전파, provider `close`와 database lifecycle 분리 | P0/P1 없음 |
| Developer/API | 예제 코드가 실제 bytecode signature와 다를 위험 | reader의 `rowMapper`·`keyExtractor`·`keyClass`를 포함한 compile-valid snippet과 KDoc 제공 | P0/P1 없음 |
| User/ops/docs | JDBC 예제를 기계적으로 복사하거나 locale asset이 drift할 위험 | R2DBC sibling을 별도 모듈로 만들고 source-equivalent README 및 EN/KO diagram pair를 등록 | P0/P1 없음 |

## 통합 추적

| 확인 항목 | 근거 | 결과 |
|---|---|---|
| sibling 선택 | `exposed-workshop/13-ecosystem-integrations/11-checkpointable-batch`의 JDBC 구현과 현재 Chapter 13 번호 | PASS |
| provider 경계 | resolved `bluetape4k-exposed-batch:1.12.1`와 published source/bytecode | PASS |
| Gradle 등록 | leaf directory 자동 discovery와 `./gradlew projects`의 `:09-checkpointable-r2dbc-batch` | PASS |
| transaction/Flow | source schema와 read-back 모두 `suspendTransaction`; Flow는 `map`/`toList`/`single`로 명시 소비 | PASS |
| 실패 계약 | normal/failure/skip/retry/timeout/cancellation→STOPPED/restart/schema/options 8 tests | STOPPED PASS; FAILED restart BLOCKED |
| 문서/도식 | module·chapter·root EN/KO 문서, semantic ledger, SVG/PNG pair | PASS |
| CI | Examples Chapter 13 H2 task와 wildcard test artifact, dynamic changed-task mapping | PASS |

## Writer gate

- SPW-01: PASS — Issue #205, 승인된 sibling 경계, published artifact와 비범위를 기록했다.
- SPW-02: PASS — API, schema, 실패/재시작, 검증 명령과 수용 기준을 추적했다.
- SPW-03: PASS — English README와 Korean README/KDoc 언어 계약을 분리했다.
- SPW-04: PASS — JDBC sibling과 provider source/bytecode를 실제로 대조했다.
- SPW-05: PASS — plan/spec/readme를 재독하고 placeholder·경로 drift·`git diff --check`를 확인했다.

## 비차단 처분

- H2 `MODE=PostgreSQL`은 provider metadata의 `BIGINT AUTO_INCREMENT` DDL과 충돌했다. 테스트 URL을 regular H2로 고정하고 PostgreSQL compatibility를 주장하지 않는다.
- root `./gradlew test`는 baseline에서 300초 timeout으로 중단됐다. module build와 Chapter 13 six-module H2 smoke를 fresh evidence로 사용하고 root full test는 validation gap으로 남긴다.
- module 전용 `detekt` task는 존재하지 않는다. root aggregate `detekt`가 `NO-SOURCE`로 성공했으므로 module detekt는 N/A다.
- shared diagram directory의 모든 asset을 README에 노출하라는 global strict audit는 기존 자산 범위 때문에 이 sibling의 증거가 아니다. pair/geometry/visual/endpoint audit와 module README link audit만 해당 범위로 판정한다.

## 판정

컴파일·문서·workflow read-back은 통과했지만, provider release boundary가
틀렸다는 P1이 발견됐다. 1.12.1은 #747 수정 이전 artifact이므로 FAILED
restart를 PASS로 판정할 수 없다. 현재 통합 상태는 `P0=0, P1=1,
BLOCKED`이며 provider backport/release 또는 명시적 scope 변경이 필요하다.
