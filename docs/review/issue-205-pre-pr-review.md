# Issue #205 PR 전 최종 리뷰

검토일: 2026-08-29
대상: `develop` 기준 `feat/issue-205-checkpointable-r2dbc-batch` 변경

## 독립 리뷰 게이트

| 리뷰 영역 | 결과 | 근거 |
|---|---|---|
| Architecture/API | PASS | `2.0.0-SNAPSHOT` API 조합과 #747 FAILED checkpoint 보존 계약을 resolved metadata/source로 확인 |
| Correctness | PASS | 9개 H2 test가 STOPPED와 FAILED 후 동일 parameter restart 및 no-duplicate를 검증 |
| Coroutine/stability | PASS | `suspendTransaction`, Flow explicit collection, cancellation 재전파, caller-owned database 경계 |
| Docs/diagram | PASS | module/chapter/root EN/KO 문서와 semantic/visual asset audit |
| Workflow | PASS | Chapter 13 task, changed-task dynamic mapping, artifact wildcard, YAML/actionlint |

## Six-lens 판정

- Security: P0=0, P1=0. production credential/SQL logging이나 exactly-once claim을 추가하지 않았다.
- Correctness/API: P0=0, P1=0. 개발 버전 metadata package와 reader signature가 맞고 FAILED checkpoint 보존·재시작을 회귀 테스트로 확인했다.
- Performance/resource: P0=0, P1=0. bounded chunk/page와 timeout/부분 write 부재를 확인했다.
- Coroutine/stability: P0=0, P1=0. STOPPED 전파/restart는 확인했지만 writer commit과 checkpoint 저장은 별도 transaction이다.
- Developer/API: P0=0, P1=0. compile-valid README snippet과 options validation을 확인했다.
- User/ops/docs: P0=0, P1=0. JDBC sibling과 R2DBC sibling의 차이, H2-only 경계를 문서화했다.

## 검증 증거

- module `test -PuseDB=H2`: 9/9 PASS — PASS
- module `build -PuseDB=H2`: `BUILD SUCCESSFUL`, Kover verify 포함 — PASS
- Chapter 13 six-module H2 smoke: 29 tests, `BUILD SUCCESSFUL` — PASS
- online dependency resolution: `bluetape4k-dependencies:2.0.0-SNAPSHOT`와
  `bluetape4k-exposed-batch:2.0.0-SNAPSHOT` 계열 해석 — PASS
- root aggregate `detekt --parallel`: `NO-SOURCE`, `BUILD SUCCESSFUL` — PASS
- root `./gradlew test` baseline: 300초 timeout — known validation gap, not hidden as pass
- module-specific `detekt`: task 없음 — N/A
- global shared-asset strict exposure audit: 범위 불일치 — N/A; targeted pair/README audit는 별도 기록

PR 생성 전 남은 필수 항목은 temporary workflow evidence 파일 제거, fresh
Issue #205 metadata read-back, Korean PR body의 마지막 `## DoD Status`, push
후 live CI/review 확인이다. 병합은 fresh exact-head `승인` 전에는 수행하지
않는다.

## 재개와 blocker 해소 근거

- 이전 `1.12.1` 경계에서는 #747이 없어 FAILED restart를 보류했지만, 승인된
  개발 버전 재개 후 `2.0.0-SNAPSHOT` metadata/source에 해당 수정이 포함됨을
  확인했다.
- `:09-checkpointable-r2dbc-batch:test`의 새 회귀 테스트는 writer가 첫 chunk
  뒤 실패할 때 `FAILED` checkpoint `3`과 typed metadata를 보존하고, 같은
  parameter 재실행이 key `4..8`을 완료하며 target ID를 중복하지 않음을
  증명한다.

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
