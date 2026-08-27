# Issue #205 PR 전 최종 리뷰

검토일: 2026-08-28
대상: `develop` 기준 `feat/issue-205-checkpointable-r2dbc-batch` 변경

## 독립 리뷰 게이트

| 리뷰 영역 | 결과 | 근거 |
|---|---|---|
| Architecture/API | BLOCKED | published 1.12.1 API 조합은 정확하지만 #747 FAILED checkpoint 수정이 release에 없음 |
| Correctness | BLOCKED | 8개 H2 test는 STOPPED restart를 검증하지만 FAILED 후 restart DoD를 검증하지 않음 |
| Coroutine/stability | PASS | `suspendTransaction`, Flow explicit collection, cancellation 재전파, caller-owned database 경계 |
| Docs/diagram | PASS | module/chapter/root EN/KO 문서와 semantic/visual asset audit |
| Workflow | PASS | Chapter 13 task, changed-task dynamic mapping, artifact wildcard, YAML/actionlint |

## Six-lens 판정

- Security: P0=0, P1=0. production credential/SQL logging이나 exactly-once claim을 추가하지 않았다.
- Correctness/API: P0=0, P1=1. metadata package와 reader signature는 맞지만 1.12.1의 FAILED checkpoint 소실이 DoD를 막는다.
- Performance/resource: P0=0, P1=0. bounded chunk/page와 timeout/부분 write 부재를 확인했다.
- Coroutine/stability: P0=0, P1=0. STOPPED 전파/restart는 확인했지만 writer commit과 checkpoint 저장은 별도 transaction이다.
- Developer/API: P0=0, P1=0. compile-valid README snippet과 options validation을 확인했다.
- User/ops/docs: P0=0, P1=0. JDBC sibling과 R2DBC sibling의 차이, H2-only 경계를 문서화했다.

## 검증 증거

- module `build -PuseDB=H2`: `BUILD SUCCESSFUL`, Kover verify 포함 — PASS
- Chapter 13 six-module H2 smoke: `BUILD SUCCESSFUL` — PASS
- root aggregate `detekt --parallel`: `NO-SOURCE`, `BUILD SUCCESSFUL` — PASS
- root `./gradlew test` baseline: 300초 timeout — known validation gap, not hidden as pass
- module-specific `detekt`: task 없음 — N/A
- global shared-asset strict exposure audit: 범위 불일치 — N/A; targeted pair/README audit는 별도 기록

PR 생성 전 남은 필수 항목은 temporary workflow evidence 파일 제거, fresh
Issue #205 metadata read-back, Korean PR body의 마지막 `## DoD Status`, push
후 live CI/review 확인이다. 다만 현재는 provider release/scope 결정 전
P1 blocker가 있어 PR 생성 자체를 보류한다. 병합은 fresh exact-head `승인`
전에는 수행하지 않는다.

## 차단 근거

- `bluetape4k-exposed-batch:1.12.1`은 2026-08-06 release이고, #747은
  2026-08-27 merge된 `[2.0.0]` 변경이다.
- 1.12.1 bytecode에서 `FAILED` report checkpoint가 `null`이며 R2DBC metadata
  update가 이를 그대로 저장한다.
- 따라서 현재 staged 구현의 failure test는 이전 chunk와 상태만 확인할 뿐
  동일 parameters 재실행의 keyset/no-duplicate를 증명하지 않는다.

## 후속 P2 처분

- retry test는 attempt count만 확인하므로 실제 backoff 지연 검증이 남아 있다.
- timeout test의 느린 writer는 DB write 전에 suspend하므로 transaction rollback
  부분 write 증거가 아니다.
- caller-owned pool fixture는 `ConnectionPool` handle을 명시적으로 dispose하지
  않아 shared lifecycle 규칙과 어긋난다.
- target primary key는 plain `batchInsert`를 멱등으로 만들지 않고 replay를
  duplicate 오류로 관측하게 하는 경계다.
- lifecycle diagram은 수정하여 NonCancellable cleanup이 reader/writer만 닫고
  pool은 caller가 소유한다는 의미로 정렬했다.
