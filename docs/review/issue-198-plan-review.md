# Issue #198 구현 계획 통합 검토

## 검토 범위와 판정 기준

- 대상: `docs/superpowers/plans/2026-08-28-issue-198-virtualthread-jdk25-plan.md`
- 기준 commit: `a6f5d69aa099a5f2d73a9d75f7a4e0ba8662f442`
- 기준 설계: `docs/superpowers/specs/2026-08-28-issue-198-virtualthread-jdk25-design.md`
- workflow run: `20260827T185619Z-32b9ae70`
- 최신 review receipt: sequence 67, checksum `bfef0ca8dcb90c1b0759edc41f6442029efe4801249a675267f995c1b28d7d84`; plan check 기록 후 현재 receipt sequence 68, checksum `ff0313a3883fcca563529985fb47578965dc0970797b23d052808f34c9824ca3`
- 방법: `step-3r-plan-review.md`와 `review-perspectives.md`에 따라 여섯 관점을 독립 검토하고, 본 문서에서 중복·우선순위·처리 상태를 통합했다.

계획의 실행 stop condition은 P0/P1이 0이고 별도 사용자 계획 `승인`을 받기 전에는 코드 mutation을 시작하지 않는 것이다. 이번 검토 동안 추적 코드·Gradle·README는 변경하지 않았다.

## 여섯 관점 최신 결과

| 관점 | P0 | P1 | P2 | P3 | 판정 | reviewed commit | 핵심 근거 |
|---|---:|---:|---:|---:|---|---|---|
| performance | 0 | 0 | 0 | 0 | APPROVE | `a6f5d69` | XML identity/count, fast/default/MariaDB matrix, 세 번의 no-cache smoke와 run별 XML snapshot |
| stability | 0 | 0 | 0 | 0 | APPROVE | `a6f5d69` | malformed property 선행 검증, direct-child/cardinality 방어, fixture 복구, bounded lifecycle/rerun |
| security | 0 | 0 | 4 | 1 | APPROVE (비차단 hardening) | `a6f5d69` | secure DOM/DOCTYPE/XInclude, exact artifact/service/skip tuple 검증; preflight·artifact binding·ABI·log·trap 보강 권고 |
| operator/Ops | 0 | 0 | 0 | 0 | APPROVE | `a6f5d69` | 명시적 report path, `set -euo pipefail`, raw log/XML, rollback/rerun, PR/merge hold |
| developer/API | 0 | 0 | 1 | 0 | APPROVE (watch) | `a6f5d69` | catalog/provider/module/source/test/docs 순서가 구체적이며 Kotlin authority 표기 차이는 Task 1에서 재확인 |
| user/caller | 0 | 0 | 2 | 1 | APPROVE (비차단 문서 hardening) | `a6f5d69` | AC-01~AC-08 traceability와 EN/KO 계획; 파일별 parity·오류 안내·verify 중복 실행 설명 보강 권고 |

통합 결과: **P0=0, P1=0 — APPROVE, 계획 승인 대기**.

## P2/P3 처리 상태

P0/P1 blocker는 없으므로 계획 review를 차단하지 않는다. 다만 모든 P2/P3는 아래와 같이 구현·문서 단계의 구체적인 처리 지점과 증거를 지정했다. 구현 후 해당 항목이 증명되지 않으면 A-08 pre-PR 수렴으로 이동하지 않는다.

| 출처 | 우선순위 | 처리 상태와 구현 전제 | 증거/재검토 |
|---|---|---|---|
| security | P2 | deferred: malformed `useDB/useFastDB` 입력은 test side effect 전에 검증할 수 있도록 validation boundary를 재확인하고, 불가능하면 `-x test` negative와 실행 순서를 명시적으로 보존한다. | Task 3/5 negative output, stability/security rerun |
| security | P2 | deferred: resolved `testRuntimeClasspath`의 실제 JDK25 artifact coordinate/path와 strict SHA·module metadata·descriptor 검사 대상을 연결하고 mismatch를 실패시킨다. | Task 5-4 dependency output와 artifact evidence |
| security | P2 | deferred: `javap`의 `major version=69`, `minor version=0`을 라벨 검색이 아닌 exact numeric equality로 검증한다. | Task 5-4 ABI command output |
| security | P2 | deferred: valid/default, negative, liveness/gate output을 공통 log directory에 보존하고 case-insensitive secret/property scan을 fail-closed로 실행한다. | Task 5-2a/2b/5 raw logs와 Step 5 scan |
| security | P3 | deferred: hostile XML fixture는 temp 생성 직후 defensive EXIT trap을 설치하고 모든 early-failure 경로에서 backup 복구·`cmp -s`를 보장한다. | Task 5-5a fixture logs와 restore check |
| developer/API | P2 | watch/deferred: repository overlay의 Kotlin `2.3.20` 표기와 현재 catalog/root build의 `2.4.0`을 Task 1 source-of-truth read-back으로 확인하며 #198에서 임의 버전 변경을 하지 않는다. | `gradle/libs.versions.toml`, root `build.gradle.kts`, `AGENTS.md` read-back |
| user/caller | P2 | deferred: module EN/KO와 root EN/KO를 파일별로 provider/JDK/token/command/count/link를 검사하고, malformed property/provider 누락/MariaDB capability 오류 의미를 두 README에 설명한다. | Task 4-2~5 file-by-file parity and link checks |
| user/caller | P3 | deferred: README 권장 명령은 verify task가 test를 포함한다는 사실을 명시해 불필요한 중복 실행을 피한다. raw `test`는 별도 diagnostic command로 남긴다. | Task 4-2 README read-back |

## 통합 검토 및 N/A

- spec AC-01~AC-08은 계획 Task 2~5와 1:1 traceability 표로 연결되어 있다.
- TDD RED/GREEN, JUnit XML execution gate, ServiceLoader singleton, MariaDB capability skip, lifecycle timeout, dependency/ABI/preview, Kover/detekt, EN/KO docs, rollback/rerun이 각각 구체 명령과 실패 조건을 가진다.
- `settings.gradle.kts`와 기존 모듈 registration은 변경하지 않으며 새 모듈이 아니므로 new-module registration/CI aggregation은 N/A다.
- production transaction code, diagram assets, `CHANGELOG.md`, release/tag/push/PR/merge는 범위 밖이며 계획에서 변경 금지 또는 후속 권한 gate로 고정했다.
- security/log 검토에는 production secret을 test JVM에 주입하지 않고 raw environment/system-property dump를 남기지 않는 경계가 포함된다.
- `git diff --check`와 29개 Bash fence `bash -n` 검증은 최신 계획에 대해 통과했다. 계획 review 단계이므로 Gradle/DB 실행은 의도적으로 수행하지 않았다.

## Writer gate와 다음 상태

| 항목 | 결과 |
|---|---|
| SPW-01 source ledger/audience/evidence | PASS |
| SPW-02 exact files/order/tests/docs/hazards/rollback | PASS |
| SPW-03 Korean technical register | PASS |
| SPW-04 AC-01~AC-08 traceability | PASS |
| SPW-05 rendered read-back, fence/header/table scan | PASS |

최신 여섯 관점의 P0/P1이 모두 0이고, P2/P3에는 위 deferred disposition이 있으므로 계획은 사용자 계획 승인 단계로 이동할 수 있다. 사용자의 별도 `승인` 전에는 Task 2 이후 code mutation, implementation commit, push, PR, merge를 수행하지 않는다.
