# Issue #198 JDK25 provider 정합화 구현 검토

## 검토 범위와 근거

- 대상 branch: `feat/issue-198-virtualthread-jdk25`
- 검토 범위: JDK25 provider catalog/runtime, `Ex01_VirtualThreads.kt`,
  execution-count gate, module/root EN·KO README, lesson과 checklist
- 근거: 현재 구현 diff, `verifyVirtualThreadTestExecution` 기본·fast·
  `H2_MARIADB` 실행, malformed property 4건, hostile XML 12종, DOCTYPE/
  XInclude fixture, dependency/artifact ABI, static/Kover, liveness 3회
- 판정 기준: Issue #198 acceptance와 approved plan의 AC-01~AC-08, P0/P1
  blocker 부재, public API와 Kotlin/Exposed R2DBC 규칙 준수

## 관점별 결과

| 관점 | P0 | P1 | P2 | P3 | 판정 | 근거 |
|---|---:|---:|---:|---:|---|---|
| performance | 0 | 0 | 0 | 0 | APPROVE | provider smoke 3회가 timeout 없이 `9.53s/9.71s/9.85s`; production hot path 변경 없음 |
| stability | 0 | 0 | 0 | 0 | APPROVE | fail-fast child failure/interruption, bounded `joinUntil`, aggregate와 `<failure>/<error>` node를 함께 보는 exact XML gate, report 복구 |
| security | 0 | 0 | 1 | 0 | APPROVE (비차단) | DOM secure features, DOCTYPE 거부, XInclude 비확장, malformed 입력 선행 거부; shared test-only `password=test` report 출력은 기존 의존성 범위 |
| operator/Ops | 0 | 0 | 0 | 0 | APPROVE | managed Gradle task, configuration cache 저장, 명시적 report path, aggregate-only gate log와 rerun evidence |
| developer/API | 0 | 0 | 1 | 0 | APPROVE (watch) | versionless catalog alias/BOM, public ServiceLoader contract, JDK21 exclusion; module `detekt` task는 저장소에 미등록 |
| user/caller | 0 | 0 | 0 | 0 | APPROVE | EN/KO command·fact parity, root direct links, JDK25 prerequisite와 MariaDB capability skip 안내 |
| integration | 0 | 0 | 0 | 0 | APPROVE (pre-PR) | 변경 surface가 Issue #198 module/catalog/docs/lesson/checklist로 제한되고 P0/P1=0 |

## 구체 검토 결과

- provider smoke는 implementation class를 직접 고정하지 않고 public
  `StructuredTaskScopeProvider`/`VirtualThreadRuntime` ServiceLoader 결과의
  singleton, 이름, support 상태를 검증한다.
- `verifyVirtualThreadTestExecution`은 `test`를 dependency로 실행하고,
  malformed `useDB`/`useFastDB`를 report parse보다 먼저 거부한다. JUnit XML은
  `testsuite` root, 선언 testcase 수, direct-child skip, exact identity와
  MariaDB capability tuple을 확인하며 aggregate `failures/errors`뿐 아니라
  각 testcase descendant `<failure>/<error>` node도 zero인지 요구한다. 두
  node를 aggregate `0`으로 숨긴 hostile fixture가 각각 non-zero로 거부되고
  원본 report가 byte-identical하게 복구됐다.
- dependency insight와 실제 캐시 artifact의 metadata, SHA-256,
  ServiceLoader descriptor, classfile `major=69/minor=0`가 일치하고
  `bluetape4k-virtualthread-jdk21`은 runtime graph에 없다.
- `projects`, `compileTestKotlin`, module `check`, `koverXmlReport`는
  `BUILD SUCCESSFUL`이다. `detekt`는 `tasks --all`에도 없어 성공으로
  가장하지 않고 capability N/A로 남겼다.
- captured gate/liveness/negative log의 case-insensitive 환경·property·
  credential scan은 0건이다. Testcontainers provider가 JUnit XML과 HTML에
  고정 test credential `testcontainers.*.password=test`를 출력하는
  report-wide 잔여 P2는 shared test-infrastructure hardening으로 분리한다.

## 결론과 남은 경계

**P0=0, P1=0 — APPROVE (pre-PR).** 구현·문서·검증은 현재 local head에서
수렴했다. PR 생성, CI read-back, merge와 local sync는 별도 권한 및 fresh
exact-head 승인 전까지 실행하지 않는다. 다음 변경자는 report-wide
test-only credential 출력 P2를 해결하려면 shared `bluetape4k-testcontainers`
출력 정책을 별도 범위로 다뤄야 하며, Issue #198 gate의 fail-closed 경계를
완화해서는 안 된다.

## Writer gate

- SPW-01: PASS — 현재 구현 diff, 명세/계획, 명령·artifact·report를 source
  ledger로 고정했다.
- SPW-02: PASS — 범위, 관점별 severity, 구체 근거, disposition, verdict와
  PR 전 경계를 기록했다.
- SPW-03: PASS — 한국어 기술 문체를 사용하고 API·경로·명령·수치·정확한
  오류와 `password=test` 관찰값을 그대로 보존했다.
- SPW-04: PASS — AC-01~AC-08 및 fresh verification 결과와 finding을
  대조해 mismatch를 남기지 않았다.
- SPW-05: PASS — 표·코드 토큰·head/authority 경계를 다시 읽고, P0/P1=0과
  P2 disposition을 확인했다.

## 2.0.0-SNAPSHOT consumer migration addendum

`bluetape4k-dependencies:2.0.0-SNAPSHOT`을 사용하는 현재 stacked train에서
기존 #198 consumer가 구 `io.bluetape4k.concurrent.virtualthread` package의
public API를 import해 `compileTestKotlin`이 실패했다. snapshot API의 현재
계약은 `io.bluetape4k.concurrent.virtualthread.api`이며, provider
implementation과 `newVT` 같은 core extension은 기존 경계를 유지한다.

### 변경 및 검증

- `Ex01_VirtualThreads.kt`의 `StructuredTaskScopeProvider`,
  `StructuredTaskScopes`, `VirtualThreadRuntime`, `VirtualThreads` import를
  `.api` namespace로 정렬했다.
- 기존 smoke assertion, bounded failure propagation, ServiceLoader singleton
  검증과 DB transaction 예제는 변경하지 않았다.
- `compileTestKotlin`: `BUILD SUCCESSFUL`
- fast JDK25 test: `tests=5, skipped=0, failures=0, errors=0`
- module `check`: `BUILD SUCCESSFUL`, Kover verify 통과

판정: **APPROVE (snapshot consumer migration)**. 이 addendum은 #198의 기존
완료 의미를 바꾸지 않고, global dependency train 변경으로 드러난 source/API
namespace drift만 보정한다.
