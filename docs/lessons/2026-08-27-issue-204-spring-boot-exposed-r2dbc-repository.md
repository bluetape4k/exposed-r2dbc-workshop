# Issue #204 Spring Boot Exposed R2DBC repository 교훈

## 결정 요약

Epic #113의 R2DBC 학습 경로에서 provider 기반 Spring Boot 예제를 기존
`09-spring/05-exposed-r2dbc-repository-coroutines`와 분리된
`09-spring/06-exposed-spring-boot-r2dbc-repository` sibling으로 추가했다.
현재 workshop의 05는 수동 `R2dbcRepository`와 R2DBC transaction을 설명하고,
06은 `ExposedR2dbcRepository` interface를 provider scanner가 proxy로 만드는
예제다. 별도 `exposed-workshop`의 JDBC 04/05 관례는 배치 근거로만 사용했으며,
JDBC와 R2DBC 경계를 한 모듈로 섞지 않아 transaction ownership과 mapping
선택이 문서·테스트에서 분명해졌다.

## 배치 조사와 sibling 선택

`exposed-workshop`의 JDBC repository 배치를 먼저 확인했다.

- `09-spring/04-exposed-repository`는 Spring MVC에서 `JdbcRepository` 구현체가
  Exposed DSL/DAO와 `@Transactional`을 사용하는 동기 패턴이다.
- `09-spring/05-exposed-repository-coroutines`는 같은 JDBC 계열을 WebFlux와
  coroutine controller로 노출하고 `newSuspendedTransaction` 및 IO dispatcher를
  설명한다.
- 현재 R2DBC workshop의 `09-spring/05-exposed-r2dbc-repository-coroutines`는
  수동 R2DBC repository 경계를 이미 제공한다. 따라서 provider scanner/proxy를
  설명할 코드는 기존 모듈을 수정하지 않고 06 sibling으로 두었다.

이 선택은 drop-in migration을 주장하지 않는다. README의 side-by-side 표는
직접 mapping/transaction 제어가 필요한 경우에는 05를, scanner/proxy와 표준
Coroutine CRUD 계약을 학습할 때는 06을 선택하도록 안내한다.

## provider와 toolchain을 source로 고정한 방법

catalog에는 versionless
`io.github.bluetape4k.exposed:bluetape4k-exposed-spring-boot-r2dbc` alias만
추가하고 `bluetape4k-dependencies:1.4.0` BOM이 버전을 결정하게 했다. live
dependency insight에서 provider `1.12.1`과 다음 API를 확인한 뒤 mapping을
작성했다.

```kotlin
io.bluetape4k.spring.data.exposed.r2dbc.repository.ExposedR2dbcRepository
io.bluetape4k.spring.data.exposed.r2dbc.repository.config.EnableExposedR2dbcRepositories
```

overlay 문서와 live catalog의 Kotlin/Spring Boot/Exposed/JDK 값이 달랐기 때문에
임의 버전을 build script에 섞지 않고 `./gradlew -version`, catalog,
dependency resolution을 단일 기준으로 사용했다. provider API와 Gradle leaf
project task는 compile 및 `./gradlew projects` 결과로 다시 고정했다.

## transaction·lifecycle 테스트에서 얻은 교훈

1. provider의 단일 CRUD 호출은 proxy가 소유하는 transaction이고, 여러 호출의
   원자성은 app-owned `R2dbcDatabase`를 명시한 outer `suspendTransaction`으로
   별도 설명해야 한다. outer 경계를 생략한 테스트에서 첫 번째 호출이 commit되고
   두 번째 bounded-column 입력이 실패하는 partial commit을 별도 계약으로 고정했다.
2. `streamAll().take(1)`은 Flow 소비와 cancellation을 보여 주지만 SQL
   round-trip이나 upstream demand 성능을 뜻하지 않는다. test-only
   `RecordingConnectionFactory`로 acquire/close/open 균형만 관찰하고 성능 주장은
   하지 않았다.
3. `R2dbcDatabase.connect`가 새 database를 Exposed global registry에 등록한다는
   점이 중요했다. temporary recording database를 pool dispose 전에
   `TransactionManager.closeAndUnregister`하지 않으면 다음 provider 호출이 이미
   닫힌 pool을 primary로 선택해 `PoolShutdownException`을 낸다. 이 정리 순서를
   `ProductR2dbcRepositoryTest`와 `PerformanceStabilityTest`에 고정하고, 테스트
   순서 우연이나 JUnit 병렬 설정을 원인으로 오인하지 않도록 했다.
4. application-owned pool은 `ConnectionPoolLifecycle` 하나만 destroy 경계로
   사용한다. `AtomicBoolean` idempotence, closed-pool 재사용 거부, cancellation
   후 후속 query 성공을 함께 검증해야 graceful shutdown과 coroutine 예외 전파를
   분리해서 볼 수 있다.
5. provider proxy는 Spring context가 만든 application-owned database에 바인딩되므로
   서비스 테스트에서 별도 `RecordingConnectionFactory`를 갈아 끼워 outer/no-outer
   acquire 차이를 재측정할 수 없다. 서비스는 row count·rollback·partial commit으로
   transaction 계약을 검증하고, connection acquire/close 안정성은 repository/성능
   테스트의 temporary database recorder에서 분리해 관찰했다.

## 검증 계약

다음 검증을 모두 fresh 실행했다.

```bash
./gradlew :06-exposed-spring-boot-r2dbc-repository:check -PuseDB=H2 --no-daemon --console=plain
./gradlew test -PuseDB=H2 --no-daemon --console=plain
repo-test-summary -- ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2 --no-daemon --console=plain
```

최종 module `check`는 28개 테스트를 모두 통과시키고 Kover verify를 포함해
`BUILD SUCCESSFUL`로 끝났다. root H2 regression은 `--rerun-tasks`로 fresh
test XML 187개, 1,220개 test case(1,072개 실행·148개 H2 비대상 assumption skip),
failure/error 0건에서 `BUILD SUCCESSFUL`을 확인했다. 별도
`repo-test-summary` 실행에서도 새 모듈의 28개 테스트가 실패·오류·skip 없이
집계되었다.
`PerformanceStabilityTest`는 maxSize=1 pool에서 5회 CRUD/stream cycle과 8개
bounded caller를 수행해 timeout, 실패, acquire/close 불균형, open connection이
없음을 기록했다. bounded `bootRun` smoke는 ready 로그를 확인한 뒤 process group을
종료했고 application JVM orphan 0을 확인했다.

독립 code review에서 발견한 LOW 관례·테스트 지적도 반영했다. 기존 workshop DTO가
`Serializable`/`serialVersionUID`를 사용하므로 `ProductRecord`와
`ProductCreateRequest`에도 같은 계약을 적용했다. 원래 1.12.1 provider에서 제약
위반 테스트는 Spring suspend repository proxy의 `UndeclaredThrowableException`
경계를 검사했지만, 2.0.0-SNAPSHOT의 `bluetape4k-exposed` direct proxy는
`InvocationTargetException.targetException`을 그대로 재전파한다. 따라서 현재
consumer 테스트는 provider의 공개 `IllegalArgumentException` 계약을 검사하면서
partial commit/rollback 결과를 그대로 고정한다. Kotlin LSP client는 현재 환경에
없어 실행하지 못했지만 `compileKotlin`, `compileTestKotlin`, module `check`, root
H2 회귀로 컴파일·정적 품질·실행 경로를 검증했다.

## 2.0.0 provider 계약 migration

| provider | 예외 표면 | workshop 대응 |
|---|---|---|
| `bluetape4k-exposed:1.12.1` | JDK reflection proxy의 `UndeclaredThrowableException` | Issue #204 당시 검증값 |
| `bluetape4k-exposed:2.0.0-SNAPSHOT` | direct proxy가 target `IllegalArgumentException`을 재전파 | 현재 테스트·문서가 따르는 계약 |

이 migration은 transaction ownership이나 rollback/partial commit 의미를 바꾸지
않고, provider가 공개하는 예외 전달 표면만 major 개발선에 맞춰 갱신한다.

초기 TDD RED 실행의 원시 콘솔 로그는 별도 artifact로 보존하지 못했다. 대신 계획
문서의 RED 명령·실패 원인, source/test read-back, 이후 동일 slice의 fresh GREEN
결과를 기록했다. 다음 Type A 작업부터는 RED 로그 요약과 test report 경로를 같은
review artifact에 즉시 남겨 이 추적성 공백을 반복하지 않는다.

문서·운영 경계도 함께 검증했다.

- en/ko README는 같은 section/code/API 의미를 유지하고 대응 PNG만 embed한다.
- SVG semantic/connector/arrowhead/endpoint/geometry/mixed-corner/visual audit과
  원본 PNG inspection이 통과했다.
- `Examples.yml`의 `chapter-09` H2 job과 push/PR path filter를 추가하고
  `actionlint` 및 YAML parse를 통과했다. 기존 changed-task/Kover/Nightly 경로가
  leaf module을 자동 포괄하므로 외부 DB shard는 추가하지 않았다.
- error response는 일반화된 `ProblemDetail`로 제한하고, application-owned
  configuration/lifecycle log는 whitelist/type-only key로 기록한다. framework/driver
  stack trace 전체 redaction은 이 모듈의 계약으로 주장하지 않는다.
- Issue #204는 실제 06 경로, 검증 task, out-of-scope를 반영하고 title,
  assignee, labels, milestone을 보존한 채 live read-back했다.

## 다음 변경자를 위한 guard

- provider alias에는 별도 버전 override를 넣지 말고 BOM/dependency insight를
  먼저 확인한다.
- temporary `R2dbcDatabase` 또는 recording pool을 만들면 반드시
  `TransactionManager.closeAndUnregister` 후 pool dispose 순서를 유지한다.
- R2DBC repository Flow 테스트는 materialization/cancellation/connection
  cleanup만 주장하고 round-trip·처리량을 과장하지 않는다.
- H2 `regular`와 app-owned fixture의 drop/create/seed 경계를 유지하고,
  `@ResourceLock("issue-204-products-h2")` 없이 병렬 DB-mutating test를 추가하지
  않는다.
- README와 Issue/PR metadata의 경로는 `09-spring/06...` sibling과 실제 Gradle
  task를 기준으로 live read-back한다. PR merge 전에는 exact head에 대한 fresh
  `승인`, green CI, `gh pr merge --rebase`만 허용한다.

## Writer gate

- SPW-01: PASS — Issue, sibling 배치, provider identifier와 범위를 명시했다.
- SPW-02: PASS — 결정·검증·rollback guard를 실행 가능한 명령과 함께 기록했다.
- SPW-03: PASS — reader-facing 설명은 한국어로, API·경로·명령은 원문으로 유지했다.
- SPW-04: PASS — `exposed-workshop` JDBC 04/05 조사와 현재 workshop R2DBC 05/06
  경계를 혼동 없이 연결했다.
- SPW-05: PASS — source/README/plan/review와 fresh verification 결과를 read-back했다.
