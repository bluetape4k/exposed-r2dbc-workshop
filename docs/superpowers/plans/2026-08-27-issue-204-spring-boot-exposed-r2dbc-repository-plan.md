# Spring Boot Exposed R2DBC Repository 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Issue #204의 승인된 설계대로 `09-spring/06-exposed-spring-boot-r2dbc-repository` sibling 모듈을 추가하여 Spring Boot repository scanning, 애플리케이션 소유 `ConnectionPool`/`R2dbcDatabase`, provider repository mapping, 단일 호출 transaction, 명시적 outer `suspendTransaction`, Flow/cancellation/lifecycle 경계를 실행 가능한 예제로 고정한다.

**Architecture:** 기존 `09-spring/05-exposed-r2dbc-repository-coroutines`와 코드를 공유하거나 수정하지 않는다. 새 모듈은 `ProductRecord`와 `Products`를 소유하고, `@EnableExposedR2dbcRepositories(basePackages = ["exposed.r2dbc.examples.springbootrepository.repository"])`로 provider proxy를 검색한다. 설정은 H2 기본 `ConnectionFactoryOptions` → `ConnectionPool` → `R2dbcDatabase` 순서로 bean을 만들고, 서비스만 여러 repository 호출을 outer `suspendTransaction(db = database)`로 묶는다. 각 repository 단일 호출의 transaction은 provider에 맡기며 Spring `@Transactional` 자동 경계를 주장하지 않는다.

**Tech Stack:** live `gradle/libs.versions.toml`/BOM이 해석하는 Kotlin 2.4.0, Spring Boot 4.1.0, Exposed 1.4.0, `bluetape4k-dependencies:1.4.0`, `bluetape4k-exposed-spring-boot-r2dbc` catalog alias, Exposed R2DBC, Spring WebFlux, Kotlin Coroutines/Flow, H2 R2DBC, JUnit 5, MockK, shared `AbstractR2dbcExposedTest`/`TestDB`. 저장소 overlay의 Kotlin 2.3.20·Spring Boot 3.5.11·Exposed 1.1.1·JDK 21 표기는 현재 catalog와 충돌하므로 T1 preflight에서 실제 resolved 값을 확인하고, 확인 전에는 버전을 새로 하드코딩하지 않는다.

---

## 실행 전 고정 사항

- 기준 worktree: `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-204-spring-boot-exposed-r2dbc`
- 기준 branch: `feat/issue-204-spring-boot-exposed-r2dbc`
- 승인된 설계: `docs/superpowers/specs/2026-08-27-issue-204-spring-boot-exposed-r2dbc-repository-design.md` (`2bb7763d`)
- 대상 issue: [#204](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/204)
- 새 Gradle project path: `:06-exposed-spring-boot-r2dbc-repository` (leaf-directory 자동 등록)
- 패키지 기준: `exposed.r2dbc.examples.springbootrepository`
- `09-spring/05-exposed-r2dbc-repository-coroutines`의 소스·build script·테스트는 수정하지 않는다.
- 모든 새 KDoc, `README.ko.md`, issue/PR metadata와 pushed commit message는 한국어로 작성한다. `README.md`는 영어 prose를 사용하되 `README.ko.md`와 section/code/API 의미를 동등하게 유지하고, API 이름·경로·명령·URL·예외 타입은 원문을 보존한다.
- 각 동작은 RED 테스트를 먼저 추가하고 예상된 실패를 읽은 뒤 최소 GREEN 구현을 한다. GREEN 이후에만 중복 제거와 이름 정리를 수행한다.

## 구현 순서와 write ownership

| 순서 | 작업 | 쓰기 범위 | 선행 조건 | 완료 증거 |
|---|---|---|---|---|
| T1 | catalog와 새 module build | `gradle/libs.versions.toml`, 새 module `build.gradle.kts` | 승인된 설계 | alias·project discovery·dependency resolution |
| T2 | RED Spring test harness | 새 module `src/test/**` | T1 | 의도한 unresolved symbol/bean 실패 |
| T3 | app/config/schema/initializer | 새 module `src/main/**/config`, `domain`, app entry point, resources | T2 RED | H2 context와 table 초기화 |
| T4 | provider repository와 CRUD/Flow | 새 module `src/main/**/repository`, repository tests | T3 | CRUD/count/delete/Flow GREEN |
| T5 | explicit transaction service | 새 module `src/main/**/service`, transaction tests | T4 | outer rollback·partial commit GREEN |
| T6 | cancellation/lifecycle/invalid config | 새 module config 및 해당 tests | T4 | cancellation 재전파·pool close·bad config 증거 |
| T7 | WebFlux API | 새 module controller 및 HTTP tests | T4, T5 | suspend endpoint/Flow response GREEN |
| T8 | module README | 새 module `README.md`, `README.ko.md` | T3–T7 source 확정 | source-equivalent docs, 명령·경로 일치 |
| T9 | architecture diagram | `docs/review/issue-204-diagram-semantic-ledger.json`, `docs/images/readme-diagrams/09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-{en,ko}.{svg,png}` | T3–T5 source 확정 | locale topology/semantic/source/raster/asset audits와 full-size inspection |
| T10 | root docs·workflow·issue metadata | `README.md`, `README.ko.md`, `.github/workflows/Examples.yml`, live #204 | T8, T9 | links/path/job/read-back 일치 |
| T11 | proportional verification | worktree 전체(읽기 중심) | T1–T10 | targeted/full tests, hazards, diff hygiene |
| T12 | Type A review/lesson/PR 준비 | `docs/review/**`, `docs/lessons/**`, branch metadata | T11 | P0/P1=0, lesson commit, PR-ready DoD |

### P0 — Step 3-R 계획 검토 게이트 (T1 이전)

- [x] 계획 승인 또는 코드 변경 전에 `$bluetape-full-feature` Step 3-R 계약을 적용한다. `references/review-perspectives.md`와 `references/step-3r-plan-review.md`를 읽고, performance, stability, security, operator/Ops, developer/API, user/caller 여섯 관점을 독립 lane으로 실행한다. free slot보다 관점 수가 많으면 bounded wave로 실행하며 lane당 정확히 하나의 관점만 맡긴다.
- [x] 각 lane은 이 plan과 승인된 spec만 read-only로 검토하고 Priority, exact section/line evidence, required edit, rerun lane을 반환한다. heavy test, commit, GitHub mutation은 실행하지 않는다.
- [x] `docs/review/issue-204-plan-review.md`에 main-session integration 표를 기록한다. 모든 spec acceptance/DoD, task ordering, TDD/코루틴/DB lifecycle, README/diagram/workflow/catalog/Kover/nightly hazard, rollback을 trace하고 P0=0/P1=0이 될 때까지 plan을 수정한 뒤 affected lane만 재실행했다.
- [x] 통합 review prose와 tracked review artifact에 `$bluetape-writer` SPW-01~05 결과를 기록했다. 이 게이트가 PASS 되기 전에는 T1–T12 구현 task를 시작하지 않는다.

### P1 — Step 3-P 위험 예측 게이트 (T1 이전)

- [x] DB transaction consistency, Flow cancellation/backpressure, pool ownership/close, H2 shared state, dependency version drift, workflow path filtering, diagram rendering을 `docs/review/issue-204-risk-prediction.md`의 signal/mitigation/rollback 표에 기록한다. 각 row는 T3–T11 task와 rerun 명령에 연결하고, generic “risk 없음” 문구를 사용하지 않는다.
- [x] P0/P1 plan review가 수정될 때 이 risk prediction과 acceptance traceability를 다시 읽어 ordering·coverage가 유지되는지 확인했다.

## 세부 작업

### T1 — catalog alias와 leaf module build 추가

- [x] 구현 전에 현재 `AGENTS.md` 계층과 `gradle/libs.versions.toml`을 다시 읽고 toolchain/BOM/provider의 단일 source-of-truth를 확정한다. 다음 명령으로 실제 실행 JDK, Gradle/Kotlin/Spring/Exposed catalog 값과 provider resolution을 기록한다.

  ```bash
  ./gradlew -version
  rg -n "kotlin|spring-boot|exposed|bluetape4k-dependencies|java" gradle/libs.versions.toml build.gradle.kts gradle.properties
  rg -n "useDB|USE_FAST_DB|exposed.test.useDB" build.gradle.kts 00-shared/exposed-r2dbc-shared/src/main/kotlin 00-shared/exposed-r2dbc-shared/README.ko.md
  ```

  기대 결과: `gradle/libs.versions.toml`이 선언한 catalog/BOM이 구현 기준이고, `./gradlew -version`의 JVM이 그 기준의 Java 버전과 일치한다. `-PuseDB=H2`와 `USE_FAST_DB=true`의 실제 우선순위/지원 여부도 source에서 확인하여 이후 명령의 근거로 기록한다. provider dependency의 resolved 값은 alias/build script를 만든 직후 아래 T1 dependencyInsight로 확인한다. 저장소 overlay와 live catalog가 계속 충돌하면 임의로 버전을 섞지 말고 그 예외와 선택 근거를 이 plan/spec review artifact에 기록한 뒤 T1을 중지한다.

- [x] `gradle/libs.versions.toml`의 Bluetape4k library 영역에 다음 versionless alias를 추가하고, Spring validation catalog alias `libs.spring.boot.starter.validation`도 build dependency에 사용한다.

  ```toml
  bluetape4k-exposed-spring-boot-r2dbc = { module = "io.github.bluetape4k.exposed:bluetape4k-exposed-spring-boot-r2dbc" }
  ```

  catalog의 `bluetape4k-dependencies = "1.4.0"` BOM이 실제 버전을 결정하게 하고, 별도 버전 문자열이나 새 repository를 추가하지 않는다.

- [x] `09-spring/06-exposed-spring-boot-r2dbc-repository/build.gradle.kts`를 생성한다. `alias(libs.plugins.exposed)`, `kotlin("plugin.spring")`, `alias(libs.plugins.spring.boot)`, `alias(libs.plugins.graalvm.native)`를 선언하고 `springBoot.mainClass`를 `exposed.r2dbc.examples.springbootrepository.ExposedSpringBootR2dbcRepositoryAppKt`로 고정한다.
- [x] build dependencies는 `project(":exposed-r2dbc-shared")`, `libs.bluetape4k.exposed.spring.boot.r2dbc`, `libs.bluetape4k.r2dbc`, `libs.exposed.r2dbc`, `libs.jetbrains.exposed.r2dbc`, `libs.jetbrains.exposed.core`, `libs.bluetape4k.coroutines`, `libs.kotlinx.coroutines.reactor`, H2/R2DBC pool/SPI/driver, Spring Boot autoconfigure/WebFlux/test, `libs.spring.boot.starter.validation`, `libs.bluetape4k.junit5`, `libs.kotlinx.coroutines.test`로 제한한다. `QueryByExample`, projection, `saveAll`, reactive transaction manager dependency는 추가하지 않는다. validation dependency는 `@Valid`/`ProblemDetail` contract를 위한 것이며 provider capability를 확장하지 않는다.
- [x] `build.gradle.kts`의 `exposed` migration block을 다음 값으로 고정하고, root convention이 제공하는 BOM과 공통 test dependency를 중복 선언하지 않는다.

  ```kotlin
  exposed {
      migrations {
          tablesPackage = "exposed.r2dbc.examples.springbootrepository"
          databaseUrl = "jdbc:h2:mem:09-spring-06-exposed-spring-boot-r2dbc-repository-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
          databaseUser = "sa"
          databasePassword = ""
      }
  }
  ```
- [x] 아래 명령으로 자동 discovery와 provider resolution을 확인한다.

  ```bash
  ./gradlew projects --console=plain
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:dependencies --configuration runtimeClasspath --no-daemon --console=plain
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:dependencyInsight \
    --dependency bluetape4k-exposed-spring-boot-r2dbc \
    --configuration runtimeClasspath --no-daemon --console=plain
  ```

  실제 결과: `projects`에 `:06-exposed-spring-boot-r2dbc-repository`가 한 번 나타났고, dependency insight가 `io.github.bluetape4k.exposed:bluetape4k-exposed-spring-boot-r2dbc:1.12.1`을 `bluetape4k-dependencies:1.4.0` 경유로 표시했다. 별도 버전 override 없이 provider가 해석되었다.
- [x] 2026-08-29 major snapshot train에서 중앙 catalog를 `2.0.0-SNAPSHOT`으로
  갱신한 뒤 provider source의 direct proxy 예외 전달을 다시 확인했다. 현재
  `InvocationTargetException.targetException`이 wrapper 없이 재전파되므로,
  consumer의 제약 위반 단언은 `IllegalArgumentException`으로 migration하고
  1.12.1의 `UndeclaredThrowableException`은 historical contract로만 남긴다.
- [x] RED 이전 단계에서 실패하면 `settings.gradle.kts`에 수동 include를 추가하지 말고 leaf-directory 탐색과 build script 경로를 바로잡는다. 이 단계의 rollback은 새 module directory와 alias만 제거하는 것으로 제한한다.

### T2 — Spring test harness를 먼저 RED로 고정

- [x] 새 module에 `src/test/kotlin/exposed/r2dbc/examples/springbootrepository/AbstractProductRepositoryTest.kt`를 만들고 `AbstractR2dbcExposedTest`를 상속한다. `@ActiveProfiles("h2")`, `@AutoConfigureWebTestClient`, `@SpringBootTest(classes = [ExposedSpringBootR2dbcRepositoryApp::class], webEnvironment = RANDOM_PORT)`를 공통으로 둔다. 새 coroutine test가 `runTest`를 사용할 수 있는지 공통 helper source에서 먼저 확인하고, 기존 `runSuspendIO`를 유지할 경우 `runTest`에 준하는 structured cleanup과 exact exception assertion을 제공한다는 근거를 기록한다.
- [x] `src/test/resources/junit-platform.properties`에는 현재 module 05와 같은 per-class/parallel 설정을 복사하되, 테스트 간 table cleanup을 각 테스트가 책임지도록 한다. `src/test/resources/logback-test.xml`은 package logger와 Exposed logger만 DEBUG로 지정한다.
- [x] 다음 RED 테스트를 production class가 없는 상태에서 작성하되, Gradle test source 전체 컴파일 때문에 slice를 한 번에 섞지 않는다. 순서는 `ConfigurationTest` RED → 최소 app/config GREEN → `RepositoryScanTest`/`ProductR2dbcRepositoryTest` CRUD RED → repository GREEN → `ProductTransactionServiceTest` transaction RED → service GREEN으로 고정한다. 구현 과정에서 각 slice의 RED를 관찰한 뒤 GREEN으로 전환했다.
  - `config/ConfigurationTest.kt`: Spring context에서 app-owned `ConnectionPool`과 `R2dbcDatabase` bean만 주입되고 기본 H2 context가 기동해야 한다. repository scan assertion은 이 단계에 넣지 않는다.
- [x] `domain/repository/ProductR2dbcRepositoryTest.kt`와 `service/ProductTransactionServiceTest.kt`는 각각 T4와 T5 시작 시 실제 새 symbol을 참조하도록 추가하여 해당 slice의 RED를 만든다.
- [x] 공통 테스트는 `runTest`를 우선 사용하고, repository helper가 요구하는 경우에만 `runSuspendIO`를 사용한다. `runSuspendIO`를 선택하면 helper source를 읽어 structured cleanup/exception propagation을 확인하고 근거를 기록한다. `io.bluetape4k.assertions` assertion과 `kotlinx.coroutines.flow.toList`를 사용한다. `support/ProductTestDatabaseLifecycle.kt`의 app-owned `R2dbcDatabase` fixture `@BeforeEach`가 `suspendTransaction(db = database) { SchemaUtils.drop(Products); SchemaUtils.create(Products); seedFixtures() }`를 수행하고 `@AfterEach`가 drop/cleanup transaction을 실행해 initializer seed가 다음 테스트에 남지 않게 한다. `withTables(TestDB.H2, Products)`는 helper가 여는 hidden outer transaction과 default database 교체가 one-call/no-outer 측정을 오염시키므로 이 계약 테스트에는 사용하지 않는다. 이는 shared helper의 UTC·transaction restoration·mutex 규칙을 버리는 것이 아니며, 동일 규칙을 custom fixture에 명시적으로 재현한다는 예외 근거를 review artifact에 기록한다. 실제 호출은 fixture transaction이 끝난 뒤 app-owned database context에서 실행해야 one-call/no-outer 계약을 측정할 수 있다. DB-mutating class(`ProductR2dbcRepositoryTest`, `ProductTransactionServiceTest`, `CancellationAndLifecycleTest`, `ProductControllerTest`, `PerformanceStabilityTest`)에는 동일한 `@ResourceLock("issue-204-products-h2", mode = READ_WRITE)`를 붙여 class 간 병렬 실행을 막는다. context/lifecycle/invalid-config 테스트만 Spring app-owned bean을 직접 주입한다.
- [x] `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*ConfigurationTest' --no-daemon --console=plain`을 실행해 unresolved `ExposedSpringBootR2dbcRepositoryApp` 또는 context bootstrap 실패를 확인한다. 실패가 아닌 조용한 성공이면 test source가 실제 새 symbol을 참조하는지 보강한다.
- [x] RED 관찰 후 production Kotlin source를 추가하고 targeted GREEN으로 전환했다. 초기 RED raw log 파일은 별도 보존하지 않았으므로 TDD 과정의 재현성은 slice별 source 참조와 fresh GREEN test evidence로 보완하고, 이 한계를 T12 review/lesson에 기록한다.

### T3 — application-owned R2DBC 구성과 Product schema 구현

- [x] `src/main/kotlin/exposed/r2dbc/examples/springbootrepository/ExposedSpringBootR2dbcRepositoryApp.kt`에 `@SpringBootApplication(proxyBeanMethods = false)`와 `WebApplicationType.REACTIVE` entry point를 작성하고, KDoc은 애플리케이션이 pool/database 소유자임을 설명한다.
- [x] app class 또는 별도 `config/RepositoryScanConfig.kt`에 다음 annotation을 정확히 사용한다.

  ```kotlin
  @EnableExposedR2dbcRepositories(
      basePackages = ["exposed.r2dbc.examples.springbootrepository.repository"]
  )
  ```

  provider auto-configuration에 scan base package, pool, database, schema, `ReactiveTransactionManager`를 맡기지 않는다.
- [x] `config/ExposedR2dbcConfig.kt`에 `@Profile("h2")` H2 `ConnectionFactoryOptions` bean, `connectionPoolOf` 기반 `ConnectionPool` bean, `R2dbcDatabaseConfig`를 사용하는 `R2dbcDatabase` bean을 만든다. H2 database name은 shared `TestDB.H2`의 `regular`와 맞춰 테스트 lifecycle이 같은 물리 DB를 정리하도록 한다. 운영 demo pool은 `maxSize=8`, `maxCreateConnectionTime=10s`, `maxAcquireTime=3s`, `maxIdleTime=10m`, `maxLifeTime=30m`, `acquireRetry=0`(명시적 no-retry)로 bounded 설정하고, test profile만 T6의 `maxSize=1`/100ms로 override한다. pool bean은 `ConnectionPool`의 application-owned lifecycle이 드러나도록 명시적 destroy 경계를 갖고, options 전체 `toString()` 대신 driver/host/port/database 같은 whitelist만 sanitizer로 기록하며 password/URL credential은 항상 마스킹한다. dispatcher는 `Dispatchers.IO` bean으로 주입하며 `Dispatchers.Default`에서 DB I/O를 수행하지 않는다.
- [x] config bean의 pool ownership, scan boundary, timeout/no-retry, sanitizer와 lifecycle phase를 설명하는 한국어 KDoc을 작성한다. public sample type/method의 KDoc 누락을 T11 diagnostics와 writer audit에서 실패로 처리한다.
- [x] `domain/ProductSchema.kt`에 `Products: LongIdTable("products")`를 정의한다. `name`은 bounded indexed varchar(120), `description`은 provider mapping을 보여 줄 nullable varchar(500) column으로 고정하고, schema 객체 외의 DAO/Entity abstraction은 만들지 않는다.
- [x] `domain/ProductRecord.kt`에 nullable `id: Long?`를 가진 immutable `data class`를 정의하고, 모든 reader-facing KDoc은 한국어로 작성한다.
- [x] `config/ProductDataInitializer.kt`에 application-owned `R2dbcDatabase`를 사용해 `ApplicationReadyEvent`에서 `runBlocking(Dispatchers.IO)` bridge와 `withTimeout(Duration.ofSeconds(15))`로 `SchemaUtils.create(Products)`와 최소 deterministic seed를 한 번 수행한다. `AtomicBoolean`으로 repeated `ApplicationReadyEvent`가 seed를 중복 실행하지 않게 하고, schema/seed timeout·실패는 sanitized error를 남긴 뒤 원래 예외를 재전파하여 readiness/startup failure로 남긴다. 초기화 경계 외의 repository/service 코드에는 `runBlocking`을 사용하지 않는다.
- [x] `src/main/resources/application.yml`에는 `spring.profiles.active: h2`, `spring.jackson.deserialization.fail-on-unknown-properties: true`, H2 R2DBC 옵션, `spring.sql.init`이 아닌 위 initializer 경계, `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 10s`, server port/management의 최소 설정만 둔다. H2/initializer는 demo-only 경계이며 production profile/migration으로 사용하지 않는다는 주석과 README runbook을 함께 둔다. `src/main/resources/logback-spring.xml`은 기존 09-spring 로그 수준을 재사용하고, 애플리케이션 소유 코드가 options whitelist, pool dispose, initializer failure type만 기록하도록 한다. acquire timeout·cancellation·terminal DB failure의 별도 이벤트 로깅은 이 workshop slice의 범위가 아니며 bounded test evidence로만 관찰한다.
- [x] 아직 repository implementation이 없는 상태에서 `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*ConfigurationTest' --no-daemon --console=plain`을 재실행한다. 기대 결과는 T2에서 정의한 app-owned pool/database context가 GREEN이고, H2 driver/pool configuration이 조용히 skip되지 않는 것이다. repository scan assertion은 T4의 `RepositoryScanTest`에서 별도 RED→GREEN으로 검증한다.
- [x] T3 구현·설정 실패 지점은 config/schema/initializer 범위로 격리했으며, 최종 H2 context/check에서 회귀가 없음을 확인했다.

### T4 — provider mapping, CRUD, Flow/stream 경계 GREEN

- [x] 먼저 `config/RepositoryScanTest.kt`와 `domain/repository/ProductR2dbcRepositoryTest.kt`를 실제 새 interface/symbol을 참조하도록 추가해 T4 RED를 관찰한다. Gradle 전체 test source compile에서 의도한 unresolved symbol/bean failure를 확인한 뒤에만 production mapping을 작성한다.
- [x] RED가 확인되면 `repository/ProductR2dbcRepository.kt`에 Spring `@Repository` class가 아닌 provider가 생성할 수 있는 repository `interface`를 사용한다. 다음 mapping default 구현을 provider source의 정확한 generic/signature와 compile error로 확인한다.

  ```kotlin
  interface ProductR2dbcRepository :
      ExposedR2dbcRepository<ProductRecord, Long> {
      override val table: IdTable<Long>
          get() = Products
      override fun extractId(entity: ProductRecord): Long? = entity.id
      override fun toDomain(row: ResultRow): ProductRecord = ProductRecord(
          id = row[Products.id].value,
          name = row[Products.name],
          description = row[Products.description],
      )
      override fun toPersistValues(domain: ProductRecord): Map<Column<*>, Any?> = mapOf(
          Products.name to domain.name,
          Products.description to domain.description,
      )
  }
  ```

  실제 구현은 provider source의 정확한 generic/signature를 compile error와 dependency source로 확인하고, `Products.id.value`, nullable id, name/value columns를 명시적으로 매핑한다. `@Repository` annotation이나 수동 `suspendTransaction`을 CRUD마다 중복해 provider scanner/proxy와 one-call transaction을 가리지 않는다.
- [x] repository mapping KDoc은 scanner가 생성하는 proxy, nullable ID, `table`/`extractId`/`toDomain`/`toPersistValues` 책임을 설명하고, provider capability와 이번 예제의 비범위를 혼동하지 않게 한다.
- [x] RED 후 `config/RepositoryScanTest.kt`를 provider package scan/bean 생성 GREEN으로 완성한다. scan test는 provider proxy가 만들어졌는지를 assert하고 Spring `@Repository` annotation을 전제하지 않는다.
- [x] T2의 repository test를 다음 검증으로 완성한다.
  - `save`가 null ID에 생성 ID를 넣고 후속 `findByIdOrNull`이 같은 값으로 읽는다.
  - 기존 ID의 `save`가 update semantics를 유지하고, `count`가 예상 값이다.
  - `findAll().toList()`는 Flow 전체 소비를 명시하고, `streamAll().take(1)`은 Flow 소비·cancellation·connection cleanup 경계를 검증한다. statement/execute 단위 round-trip과 upstream demand/buffering은 이 예제에서 측정하지 않으며 성능 claim을 하지 않는다.
  - `deleteById` 후 `existsById`/`findByIdOrNull` 결과가 사라진다.
  - bounded column을 넘는 입력의 single-call exception 뒤 기존 row 수가 보존되어 provider rollback을 검증한다.
- [x] `src/test/kotlin/exposed/r2dbc/examples/springbootrepository/support/RecordingConnectionFactory.kt`를 추가한다. R2DBC `ConnectionFactory`를 얇게 감싸 `create()` 횟수와 connection close 횟수를 `AtomicInteger`로 기록하고, query/connection recorder는 test source에서만 사용한다. `streamAll().take(1)`에는 충분한 row를 넣고 emitted row 수, connection acquire/close 균형, `take` 취소 후 recorder의 open count 0을 assert한다. `findAll().toList()`는 전체 materialization을 의도한 API test임을 주석/KDoc으로 한정하고 backpressure 성능을 주장하지 않는다. SQL statement/execute round-trip count는 N/A로 명시한다.
- [x] `ProductTestDatabaseLifecycle` fixture의 app-owned database setup transaction은 매 테스트 `SchemaUtils.drop(Products)` → `SchemaUtils.create(Products)`와 deterministic fixture seed를 수행하고, cleanup transaction은 다시 drop하여 initializer가 context 기동 때 넣은 seed가 다음 테스트에 남지 않게 한다. app H2 option의 `regular` database name과 일치시키며, 실제 repository 호출은 setup transaction이 끝난 뒤 app-owned database를 사용해야 one-call/no-outer 계약을 측정할 수 있다. `CancellationException` cleanup은 catch-all로 삼키지 않는다.
- [x] 먼저 `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*RepositoryScanTest' --tests '*ProductR2dbcRepositoryTest' -PuseDB=H2 --no-daemon --console=plain`을 실행하여 RED→GREEN을 확인한 뒤 `./gradlew :06-exposed-spring-boot-r2dbc-repository:compileKotlin --no-daemon --console=plain`으로 public API diagnostics를 확인한다. recorder의 acquire/close count와 `streamAll().take(1)`의 emitted row count를 test output에 기록한다.
- [x] provider signature mismatch, `Flow` coldness, nullable id mapping 오류는 provider API/source와 compiler output을 기준으로 최소 수정했다. 기존 05 repository를 복사해 이름만 바꾸는 방식은 사용하지 않았다.

### T5 — 명시적 outer transaction service와 atomicity tests

- [x] `service/ProductTransactionService.kt`에 app-owned `R2dbcDatabase`와 `ProductR2dbcRepository`를 주입한다. 단일 `save/find`는 repository proxy에 위임하고, 삭제는 `suspendTransaction(db = database)` 안에서 `existsById`와 `deleteById`를 같은 원자 경계로 묶은 `deleteIfExists(id): Boolean`을 제공한다. 아래 세 transaction 경계를 별도 메서드로 만든다.
  - `saveTwoAtomically(first, second)`: `suspendTransaction(db = database)` 안에서 두 save를 수행한다.
  - `saveTwoAndFailAtomically(first, second)`: 같은 outer transaction에서 두 save 후 고정 예외를 발생시킨다.
  - `saveTwoAndFailWithoutOuterTransaction(first, second)`: outer transaction 없이 첫 번째 proxy 호출을 commit한 뒤, 의도적으로 bounded/constraint-invalid인 두 번째 입력의 proxy 호출이 실패하도록 하여 partial commit 경계를 보여 준다.
- [x] `service/ProductTransactionServiceTest.kt`를 완성한다. atomic success는 두 row가 함께 보임을, atomic failure는 두 valid save 뒤 명시적 예외로 시작 count까지 rollback됨을, no-outer failure는 첫 번째 단일 호출이 commit된 뒤 두 번째 repository 호출 자체가 deterministic bounded/constraint 오류로 실패하고 fresh transaction에서 첫 row만 남는 partial commit을 검증한다. 하나의 no-outer 시나리오에서 “두 save 후 고정 예외”와 “둘째 호출 자체의 constraint 오류”를 혼용하지 않는다. 테스트가 Spring `@Transactional`이 suspend repository를 감싼다고 표현하지 않도록 assertion 이름과 KDoc을 명확히 한다.
- [x] transaction service의 public method KDoc에 single-call provider transaction, explicit outer transaction, no-outer partial commit의 선택 기준과 Spring `@Transactional` 비사용 경계를 한국어로 기록한다.
- [x] `deleteIfExists`는 존재 시 `true`/삭제, 미존재 시 `false`/무변경을 반환하고 controller가 이를 각각 `204`/`404`로 변환한다. 존재 확인과 삭제 사이에 별도 transaction이 생기지 않는지 service test에서 고정한다.
- [x] T4의 `RecordingConnectionFactory`는 provider repository/stream과 `PerformanceStabilityTest`의 app-owned temporary database에서 acquire/close 균형을 검증한다. 서비스 proxy는 Spring context가 생성한 app-owned database에 이미 바인딩되므로 별도 recorder를 주입해 outer/no-outer acquire 차이를 재측정하는 것은 이 provider 계약에서 성립하지 않는다. 따라서 T5는 row count·rollback·partial commit을 서비스 경계의 실행 가능한 증거로 유지하고, acquire 안정성은 T4/T6의 recorder 테스트로 분리한다.
- [x] `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*ProductTransactionServiceTest' -PuseDB=H2 --no-daemon --console=plain`을 실행한다. 실패 시 nested transaction/provider reuse 여부를 stack trace, row count, acquire/close counters로 확인하고, transaction manager를 새로 추가하지 않는다.
- [x] rollback point는 T5 service/test 파일로 제한했다. provider behavior에 맞춘 최소 수정만 적용했고 approved spec의 app-owned outer transaction 경계를 유지했다.

### T6 — cancellation, pool lifecycle, invalid configuration

- [x] `config/ConnectionPoolLifecycle.kt`에 application-owned `R2dbcDatabase`와 pool을 함께 받는 유일한 `DisposableBean` destroy mechanism을 구현한다. `AtomicBoolean`으로 close를 한 번만 수행하고, Exposed global manager를 pool보다 먼저 unregister한 뒤 pool을 dispose한다. `CancellationException` 또는 다른 예외를 close 과정에서 일반 성공으로 바꾸지 않는다. `@Bean(destroyMethod = "")`와 별도 helper를 중복 조합하지 않으며, Spring Boot `server.shutdown: graceful`과 lifecycle timeout은 이 단일 경계가 context close에 연결되는 운영 설정으로 남긴다. 이 workshop slice에는 in-flight shutdown timing을 모의하는 별도 테스트가 없고, 실제 context close/reopen·closed-pool 거부·registry 해제를 테스트로 고정한다.
- [x] `config/CancellationAndLifecycleTest.kt`를 추가한다.
  - 충분한 Product row를 준비한 뒤 child `launch`/`async` 안에서 `streamAll().collect { currentCoroutineContext().cancel(CancellationException("test cancellation")) }`로 취소를 유발하고 child의 `CancellationException`이 호출자에게 재전파되는지 확인한다.
  - 취소한 뒤 새 `findByIdOrNull`/`count`가 성공하여 connection/pool cleanup이 회복되었음을 확인한다.
  - lifecycle helper `destroy()` idempotence를 MockK로 확인한 뒤, 별도로 isolated real `ConnectionPool`을 포함한 Spring context를 실제 `ConfigurableApplicationContext.close()`로 닫아 manager unregister → dispose 1회 → close 후 connection acquisition 거부를 검증한다. Spring graceful shutdown의 in-flight drain timing 자체는 이 workshop slice의 직접 테스트 범위가 아니다. 정상 app context의 공유 pool은 이 테스트가 닫지 않는다.
  - destroy 후 같은 pool/database로 `findByIdOrNull` 또는 connection acquisition을 시도해 closed-pool exception이 나고, 새 pool이 자동 생성되지 않음을 확인한다.
- [x] cancellation test context의 test pool은 `maxSize = 1`, `maxAcquireTime = Duration.ofMillis(100)`으로 제한한다. `streamAll().collect` cancellation 후 parent/sibling context에서 후속 `count` 호출이 성공하여 `CancellationException` 재전파와 후속 사용 가능성을 분리 검증한다. 별도 bounded pool slice는 `withTimeout(Duration.ofSeconds(5))` 안에서 8개의 raw connection caller를 실행하고 모두 성공하며 close되는지 확인한다. recorder의 acquire/close/open 균형은 T4와 T6의 temporary pool test에서 직접 검증하고, 이 cancellation slice에는 app-owned provider proxy와 별도 recorder를 결합한 assertion을 만들지 않는다.
- [x] 정상 `h2` app profile에서는 application-owned pool/database bean availability와 T3의 bounded pool·`acquireRetry=0` source read-back을 확인하고, test-only pool override에서는 `maxSize=1`·짧은 acquire timeout 설정으로 bounded contention caller가 timeout window 안에 완료되는지 검증한다. 운영 설정의 timeout을 test 값으로 가장하지 않으며, 별도 deterministic contention failure를 이 slice의 계약으로 주장하지 않는다.
- [x] `config/CancellationAndLifecycleTest.kt`의 invalid configuration slice에서 test-only missing/unsupported driver, unreachable H2 URL의 `awaitSingle()` acquisition failure, whitelist sanitizer를 검증한다. invalid URL은 별도 `ConnectionFactory`를 사용해 정상 Spring context의 pool registry를 오염시키지 않는다. 같은 slice에서 isolated context의 `ProductDataInitializer`에 `ApplicationReadyEvent`를 두 번 전달해 fixture 중복이 없는지 확인한다. initializer failure의 예외 재전파와 애플리케이션 소유 log의 type/whitelist 경계는 production source read-back과 bounded boot smoke로 확인하며, framework/driver stack-trace 전체 redaction은 이 예제의 계약으로 주장하지 않는다. context가 조용히 올라오는 것만으로 PASS 처리하지 않고, 명확한 R2DBC/connection factory exception을 assert한다.
- [x] 다음 명령을 순서대로 실행한다.

  ```bash
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:test \
    --tests '*CancellationAndLifecycleTest' \
    --tests '*InvalidR2dbcConfigurationTest' \
    -PuseDB=H2 --no-daemon --console=plain
  ```

  기대 결과: cancellation test는 child에서 발생한 `CancellationException`을 호출자에게 재전파하고 parent/sibling의 후속 DB 호출이 성공하며, lifecycle verification은 manager unregister 후 `dispose` 1회·context reopen·closed-pool 재사용 거부를 보고한다. invalid configuration은 missing/unsupported driver와 unreachable URL acquisition의 명확한 실패 exception을 보고하고, repeated ready event는 중복 fixture를 만들지 않는다. 이 예제는 readiness probe나 framework/driver stack-trace redaction을 제공하지 않는다.
- [x] pool close가 test JVM 전체의 다른 shared DB를 닫지 않는지 확인했다. 임시 `R2dbcDatabase`는 Exposed primary registry에서 `TransactionManager.closeAndUnregister`로 제거한 뒤 pool을 파괴하여 test-order 오염을 막았다.

### T7 — 최소 WebFlux suspend API와 HTTP tests

- [x] `domain/ProductCreateRequest.kt`에 `id`를 포함하지 않는 immutable `data class ProductCreateRequest(val name: String, val description: String?)`를 정의하고 `@JsonIgnoreProperties(ignoreUnknown = false)`, `name`의 `@field:NotBlank`/`@field:Size(max = 120)`, nullable `description`의 `@field:Size(max = 500)` 제약을 둔다. `controller/ProductController.kt`에 repository/service를 주입하는 `@RestController`를 작성하고 request body에 `@Valid`를 적용한다. `controller/ApiErrorHandler.kt`(또는 동등한 명시 파일)에 WebFlux의 `WebExchangeBindException`, unknown JSON을 감싸는 `ServerWebInputException`, not-found를 민감정보 없는 `ProblemDetail`(`application/problem+json`)로 매핑한다. HTTP 계약은 `GET /products` → `200 OK`·`application/json` 배열, `GET /products/{id}` → 존재 시 `200 OK`·`ProductRecord`, 미존재 시 `404 Not Found`·`ProblemDetail`, `POST /products` → `201 Created`·생성된 ID의 JSON, `DELETE /products/{id}` → 존재 시 `204 No Content`, 미존재 시 `404 Not Found`로 고정한다. DELETE service는 provider `deleteById`의 `Unit`을 그대로 노출하지 않고 `deleteIfExists`의 Boolean을 명시적 HTTP 상태로 변환한다. 모든 endpoint의 DB 경계는 service/repository contract를 따르고 blocking JDBC API를 사용하지 않는다.
- [x] `controller/ProductControllerTest.kt`에서 `WebTestClient`와 `runTest`(공통 helper가 요구할 때만 근거를 남긴 `runSuspendIO`)를 사용해 위 status/content-type/body 계약을 검증한다. GET list 응답은 `asFlow().toList()`로 의도적으로 소비하고 HTTP streaming/backpressure를 주장하지 않는다. GET missing, POST generated ID, DELETE existing/missing, validation error까지 deterministic fixture로 검증하고, 테스트 데이터는 unique faker 값과 deterministic cleanup을 사용한다.
- [x] controller 및 request DTO KDoc은 endpoint path, status/content-type/body, Flow materialization, validation과 no-disclosure 경계를 한국어로 설명한다.
- [x] 같은 테스트에 non-null `id`를 포함한 생성 payload가 unknown-property 4xx로 거부되고 기존 row를 덮어쓰지 않는 회귀, 공백·bounded `name` 또는 500자를 넘는 `description` payload가 repository/DB에 도달하지 않고 `400 application/problem+json`으로 끝나는 회귀를 추가한다. error response는 일반화된 `ProblemDetail`만 반환하고, 애플리케이션 소유 log는 config sanitizer와 initializer type-only key로 제한한다. framework/driver의 전체 예외 redaction은 주장하지 않는다. name/description bounded validation은 선택 사항이 아니라 필수 계약으로 고정한다. QBE/projection/saveAll endpoint는 만들지 않는다.
- [x] `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*ProductControllerTest' -PuseDB=H2 --no-daemon --console=plain`을 실행한다. HTTP failure는 controller mapping, JSON serialization, transaction boundary 순서로 진단하고, controller에서 `runBlocking`을 추가하지 않는다.

### T8 — module README 두 locale 작성

- [x] `09-spring/06-exposed-spring-boot-r2dbc-repository/README.md`와 `README.ko.md`를 같은 section 순서와 code/example 구조로 작성한다. 두 문서는 다음 내용을 source-equivalent로 포함한다.
  1. 기존 `09-spring/05-exposed-r2dbc-repository-coroutines`의 수동 `R2dbcRepository`와 새 provider `ExposedR2dbcRepository`의 차이;
  2. `@EnableExposedR2dbcRepositories` scan과 application-owned `ConnectionPool`/`R2dbcDatabase`;
  3. `ProductRecord` mapping (`table`, `extractId`, `toDomain`, `toPersistValues`);
  4. `save/findByIdOrNull/findAll/streamAll/count/deleteById`와 Flow 소비;
  5. one-call transaction, 명시적 outer `suspendTransaction`, outer 없는 partial commit;
  6. `ProductCreateRequest`의 id 비노출과 name/description validation(각각 120/500자), cancellation, rollback, pool close, invalid config의 실패 계약;
  7. 이번 예제에서 다루지 않는 QueryByExample/projection/saveAll 범위와 reactive transaction manager를 추가하지 않는 이유, provider 공식 manual 링크, H2 test command. 이 항목들은 “이번 예제의 비범위”이며 provider capability 부정이 아님을 명시한다.
- [x] README에 `05` 수동 `R2dbcRepository`와 `06` provider adapter를 side-by-side 표로 정리한다. 표에는 선택 기준(직접 mapping/transaction 제어가 필요한 경우 vs scanner/proxy와 표준 CoroutineCrudRepository 계약을 학습하는 경우), transaction ownership, `findById`/`findByIdOrNull`, mapping 방식, 그리고 drop-in migration이 아니라 별도 sibling 예제라는 점을 포함한다.
- [x] README의 운영 runbook에 H2/initializer가 demo-only라는 경계, profile override 방법, pool exhaustion/acquire timeout·closed pool·bad config·seed failure의 증상과 sanitized log key, 정상 재기동/rollback 범위를 기록한다. production migration, 외부 credential, 장기 `bootRun` process를 지원한다고 암시하지 않는다.
- [x] README에 Actuator/metrics/health endpoint를 제공하지 않으며 production readiness·운영 모니터링을 보장하지 않는다는 N/A 경계를 명시한다. 운영 관찰 범위는 sanitized log와 bounded test evidence로 제한한다.
- [x] README에는 실제 파일 경로와 다음 명령만 사용한다.

  ```bash
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:bootRun
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2
  ```

- [x] `README.md`는 English prose, `README.ko.md`는 Korean prose를 사용하고 같은 section 순서·code/API 의미를 유지한다. T9의 대응 PNG만 각 locale에 embed하고 SVG를 직접 embed하지 않는다. 링크 대상은 실제 파일 존재 여부와 대소문자를 검사한다.
- [x] README code block은 source에서 복사한 compile-valid excerpt만 허용하고, mapping/endpoint signature·path·status가 실제 Kotlin source와 일치하는지 `rg`/수동 read-back으로 확인한다. `bootRun` smoke도 T11에서 실행했다.
- [x] `node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs 09-spring/06-exposed-spring-boot-r2dbc-repository/README.ko.md`와 두 README의 link/asset-pair check를 실행한다. 영어 README는 명령/API/경로를 보존하고 한국어 README는 reader-facing prose를 자연스러운 한국어로 작성한다. provider 공식 manual 링크도 두 문서에 동일하게 기록한다.
- [x] source와 docs가 어긋나지 않도록 구현 source/테스트를 기준으로 두 README를 read-back했고, README 변경은 독립 rollback 지점을 유지한다.

### T9 — architecture SVG/PNG와 semantic ledger

- [x] `$bluetape-diagram`의 `references/common.md`, `references/architecture.md`, `references/semantic-ledger.md`를 적용하고 `docs/review/issue-204-diagram-semantic-ledger.json`에 reader question, source anchors, unique node IDs, closed edges, locale pair topology equivalence, complexity decision을 기록한다. source anchors는 새 app/config/repository/service와 승인 설계 경로를 가리킨다.
- [x] English canonical SVG/PNG pair를 `docs/images/readme-diagrams/09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-en.{svg,png}`, Korean canonical SVG/PNG pair를 `docs/images/readme-diagrams/09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-ko.{svg,png}`로 작성한다. 두 locale은 동일 node ID/topology/edge를 유지하고 label만 locale에 맞게 번역한다. 노드는 `HTTP/suspend caller → Product service → repository scan/proxy → Exposed mapping → application-owned R2dbcDatabase → ConnectionPool → H2/R2DBC driver`만 포함하고, single-call transaction과 optional outer transaction 분기를 명시한다. source에 없는 JDBC manager, provider auto-created pool, QBE/projection은 그리지 않는다.
- [x] 각 SVG의 marker는 `data-role`, `data-size`, `markerUnits="userSpaceOnUse"`, 올바른 `orient`, positive-x tip direction을 선언하고, orthogonal connector에는 rounded corner와 endpoint clearance를 둔다. 각 locale SVG에서 대응 PNG를 생성하며 README.md는 `09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-en.png`, README.ko.md는 `09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-ko.png`만 embed한다.
- [x] 다음 검증을 순서대로 실행한다. endpoint audit은 connector/semantic path 대상 기본 모드로 실행했으며, `--all-paths`는 카드/장식 path까지 endpoint로 오인하는 도구 범위 불일치가 있어 적용하지 않았다. shared `docs/images/readme-diagrams`의 기존 asset pair 전체를 강제하는 `--require-all-referenced`는 이 module README가 참조하지 않는 선행 asset 119개 때문에 전역 실행 시 실패하므로 N/A로 기록하고, module README별 기본 pair audit은 누락 0건으로 통과시켰다.

  ```bash
  python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-semantic-audit.py \
    --repo-root . --json docs/review/issue-204-diagram-semantic-ledger.json
  for locale in en ko; do
    svg="docs/images/readme-diagrams/09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-${locale}.svg"
    png="docs/images/readme-diagrams/09-spring-06-exposed-spring-boot-r2dbc-repository-architecture-01-${locale}.png"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-svg-text-normalize.py "$svg"
    xmllint --noout "$svg"
    cairosvg "$svg" -o "$png" -s 2
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-connector-audit.py "$svg"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-arrowhead-audit.py "$svg"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-endpoint-audit.py "$svg"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-geometry-audit.py "$svg"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-mixed-corner-audit.py "$svg"
    python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-visual-audit.py --json "$png"
  done
  python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-asset-pair-audit.py \
    --asset-dir docs/images/readme-diagrams \
    --readme 09-spring/06-exposed-spring-boot-r2dbc-repository/README.md \
    --require-all-referenced --json
  python3 /Users/debop/.codex/skills/bluetape-diagram/scripts/diagram-asset-pair-audit.py \
    --asset-dir docs/images/readme-diagrams \
    --readme 09-spring/06-exposed-spring-boot-r2dbc-repository/README.ko.md \
    --require-all-referenced --json
  ```

  기대 결과: source/semantic/XML/text/connector/arrowhead/endpoint/geometry/mixed-corner/visual/asset-pair 각 보고서의 `failures=0`, 해당하지 않는 count는 명시적 N/A 사유, PNG dimensions/aspect/occupancy/margins가 기록된다.
- [x] 마지막 좌표 변경 후 en/ko PNG를 각각 `view_image`로 원본 크기로 열어 label clipping, arrowhead 방향, connector endpoint, branch crossing, whitespace를 눈으로 확인한다. 스크립트와 PNG가 다르면 PNG를 우선하여 해당 locale SVG를 수정하고 전체 locale audit을 다시 실행한다.
- [x] diagram source/PNG/ledger가 각 locale README의 대응 PNG ref에 노출되고, Mermaid/Graphviz residue가 없는지 확인한다. SVG/PNG pair가 깨지면 T9만 재실행하며 코드 변경은 되돌리지 않는다.

### T10 — root README, Examples workflow, live issue body 정렬

- [x] English root `README.md`와 Korean root `README.ko.md`의 추천 학습 경로 7번, `09-spring` module map, 주목할 예제 목록에 각각 정확히 `09-spring/06-exposed-spring-boot-r2dbc-repository/README.md`와 `09-spring/06-exposed-spring-boot-r2dbc-repository/README.ko.md`를 추가하고, 05 수동 repository와 06 provider adapter의 역할 차이를 한 줄로 설명한다. 존재하지 않는 `02-alternatives-to-jpa` 경로를 새 모듈 링크로 사용하지 않는다.
- [x] `.github/workflows/Examples.yml`의 `push.paths`와 `pull_request.paths`에 `09-spring/06-exposed-spring-boot-r2dbc-repository/**`를 추가하고, `chapter-09` job을 만든다. job은 T1에서 확인한 실제 Gradle JVM과 동일한 Java setup/Gradle wrapper를 사용하여 다음을 실행한다.

  ```bash
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2 --continue
  ```

  artifact upload에는 새 module의 `build/test-results/test/*.xml`과 `build/reports/tests/test/`를 포함한다. 09-spring의 unrelated 05/07 변경을 이 job이 검증한다고 가장하지 않는다.
- [x] `.github/workflows/ci.yml`, `.github/workflows/nightly.yml`, `.github/scripts/changed-r2dbc-test-tasks.py`의 신규 leaf module 등록 경로를 함께 확인한다. 다음 read-only 검색으로 path filter, dynamic task mapping, H2/Postgres/MySQL/MariaDB shard, Kover artifact와 `needs` 연결을 대조한다.

  ```bash
  rg -n "09-spring|changed-r2dbc-test-tasks|koverXmlReport|module-tasks|test-results" \
    .github/workflows/ci.yml .github/workflows/nightly.yml \
    .github/scripts/changed-r2dbc-test-tasks.py
  ```

  기대 결과: changed-task script가 leaf module을 자동 매핑하면 그 근거와 H2/full coverage를 기록하고, 명시 shard 목록에는 새 task를 추가한다. 자동 매핑되지 않으면 `ci.yml` path/job/artifact/coverage와 Nightly shard에 같은 task를 명시적으로 추가한다. 어느 경우든 Java 버전은 T1에서 확인한 실제 Gradle JVM과 일치시킨다. workflow를 수정하지 않는 경우에는 해당 파일의 현재 registration chain과 N/A 근거를 review artifact에 남긴다.
- [x] CI/Nightly artifact가 새 module의 JUnit XML, HTML report와 bounded startup/Gradle failure log를 보존하는지 `path`/retention으로 확인한다. 기존 wildcard가 XML/HTML을 이미 포함하고 startup smoke는 로컬 T11에서만 실행한다면 그 근거와 “CI startup log N/A”를 review artifact에 기록하며, credential/전체 options 문자열을 artifact에 남기지 않는다.
- [x] 새 모듈은 H2 `regular` profile만 지원하므로 Nightly의 PostgreSQL/MySQL/MariaDB 명시 shard에는 task를 추가하지 않는다. CI dynamic matrix가 changed-task를 모든 DB job에 fan-out하는 경우에도 해당 실행은 H2 app profile 검증으로만 분류하고 외부 DB 호환성 증거로 세지 않으며, H2-only/N/A 근거를 workflow review에 기록한다.
- [x] `settings.gradle.kts`는 leaf-directory 자동 등록을 그대로 두고 수동 `include("exposed-spring-boot-r2dbc-repository")`를 중복 추가하지 않는다. `./gradlew projects` 결과와 workflow task name을 대조한다.
- [x] live issue #204 body를 승인된 실제 배치와 계획/설계 경로로 갱신한다. 오래된 `02-alternatives-to-jpa/r2dbc-example`는 `09-spring/06-exposed-spring-boot-r2dbc-repository`로 교체하고, 목표·검증·out-of-scope를 설계와 일치시킨다.
- [x] mutation 전 현재 issue title/assignee/labels/milestone을 기록하고 `/tmp/issue-204-body.md`에 갱신 body를 작성한 뒤 명령으로 실행했다. title·assignee·milestone·labels는 기존 값을 보존했고 live read-back으로 확인했다.

### T12 후속 — 2.0.0 provider 계약 정합화

- [x] 1.12.1 wrapper 관찰값과 2.0.0-SNAPSHOT direct proxy의 target exception
  재전파를 별도 migration으로 기록한다.
- [x] `ProductR2dbcRepositoryTest`와 `ProductTransactionServiceTest`는 현재
  provider의 `IllegalArgumentException` surface를 단언하고 rollback/partial
  commit 의미는 유지한다.
- [ ] 변경된 consumer slice, Chapter 09 workflow, PR body와 Issue #204 migration
  comment를 exact-head 기준으로 다시 검증한다.

  ```bash
  gh issue edit 204 --body-file /tmp/issue-204-body.md
  ```

  다음 read-back으로 title/body/metadata와 링크를 확인한다.

  ```bash
  gh issue view 204 --json number,title,state,assignees,labels,milestone,body,url
  ```

  기대 결과: issue가 `OPEN`, assignee `debop`, milestone `1.4.0`, 기존 관련 labels를 유지하고, body에 새 path·설계·계획·검증 command가 있으며 stale path가 0개다. issue edit 실패 시 로컬 source/docs 변경은 보존하고 live mutation만 재시도한다.

### T11 — targeted와 proportional verification

- [x] 구현 중 각 TDD slice의 targeted test가 GREEN인 뒤 아래 의존 순서 검증을 실행한다.

  ```bash
  ./gradlew projects --console=plain
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:compileKotlin \
    :06-exposed-spring-boot-r2dbc-repository:compileTestKotlin \
    --no-daemon --console=plain
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:test \
    -PuseDB=H2 --no-daemon --console=plain
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:check \
    -PuseDB=H2 --no-daemon --console=plain
  ./gradlew test -PuseDB=H2 --no-daemon --console=plain
  ```

  각 command의 `BUILD SUCCESSFUL`, test count, skipped/failed count를 기록한다. Docker/Testcontainers backend는 기본 DoD가 아니며 실행할 경우 다른 Gradle invocation과 병렬화하지 않는다.
- [x] `repo-test-summary -- ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2`가 설치되어 있어 결과 요약에 사용했다.
- [x] repository hazard를 확인했다: catalog alias/BOM 정렬, leaf module discovery, Kover subproject inclusion, Examples workflow path/job, nightly 영향, README PNG refs, `09-spring/05` diff 불변, public KDoc/diagnostics, graceful shutdown/in-flight drain, initializer fail-fast/idempotence, sanitized lifecycle/error log, `git diff --check`.
- [x] public sample type/method KDoc의 실행 가능한 read-back을 수행했다. 새 module의 `src/main/kotlin` 선언을 `rg`로 modifier까지 포함해 열거하고 각 public 선언 직전 한국어 `/** 설명 */` 존재를 수동 대조했다. 누락·영문 reader-facing 설명·endpoint status/transaction/mapping 경계가 없음을 확인했다.
- [x] `src/test/kotlin/exposed/r2dbc/examples/springbootrepository/PerformanceStabilityTest.kt`를 별도 class로 추가한다. `maxSize=1` test pool에서 `repeat(5)` CRUD/stream cycle과 bounded concurrent caller 8개를 순차적으로 실행하고, 각 cycle의 elapsed time, acquire/close delta, timeout/failed count를 기록했다. targeted command의 fresh evidence는 해당 테스트와 full module output에 포함되어 있다. 벤치마크나 처리량 수치는 범위 밖으로 명시하고 leak/timeout이 없다는 stability claim만 허용했다.
- [x] README의 `bootRun` 명령은 bounded startup smoke로 실행했다. `gtimeout`/`timeout`이 없는 macOS 환경에서 Python `subprocess` fallback으로 process group/session을 실행하고, ready 로그·종료 경계·orphan 0을 확인했다.
- [x] `git diff --stat`, `git status --short`, `git diff --check`, `rg -n "02-alternatives-to-jpa/r2dbc-example|QueryByExample|projection|saveAll"`를 검토했다. 마지막 `rg` 결과는 승인된 unsupported 설명 또는 기존 unrelated module만 포함하고 실제 API claim은 없었다.
- [x] 초기 full H2 실행에서 재현된 `PoolShutdownException`은 retry로 닫지 않고 temporary `R2dbcDatabase`의 global registry 잔류 원인을 고친 뒤 targeted/module/root 검증을 처음부터 재실행했다.

### T12 — Type A final review, lesson, PR-ready closeout
- [x] 구현 후 `$verification-before-completion`과 `$code-review`를 적용하고, 여섯 관점의 final diff review를 `docs/review/issue-204-pre-pr-review.md`에 기록한다. P0/P1 발견 시 구현·테스트·해당 review lane을 다시 연다. 독립 code-reviewer 재검토는 CRITICAL/HIGH/MEDIUM/LOW=0이며 LSP client 부재만 비차단 COMMENT로 남겼다.
- [x] `docs/lessons/2026-08-27-issue-204-spring-boot-exposed-r2dbc-repository.md`에 배치 조사에서 확인한 JDBC sibling 관례, provider API/version verification, transaction/lifecycle 테스트 교훈, 검증 command와 future guard를 한국어로 기록하고 SPW-01~05를 통과시킨다.
- [x] lesson/review와 implementation을 Lore protocol commit으로 분리한다. implementation commit(`e6c1423d`)과 review 보완 commit(`3d4eb94`), 문서/lesson commit을 별도 유지하고 각 commit은 한국어 intent line과 `Constraint`, `Rejected`, `Confidence`, `Scope-risk`, `Directive`, `Tested`, `Not-tested` trailer를 포함한다.
- [ ] PR creation은 승인된 target repository/base/head와 Type A DoD가 모두 준비된 뒤 별도 gate로 둔다. PR body는 한국어로 작성하고 마지막 section을 `## DoD Status`로 두며, live metadata/issue link/CI evidence를 `gh pr view --json number,title,state,baseRefName,headRefName,body,labels,assignees,milestone,statusCheckRollup,reviews,mergeStateStatus`로 read-back한다. merge 요청 전에는 fresh exact-head `승인`을 다시 받아야 하며, merge 방식은 rebase only다.

## Acceptance traceability

| 승인 설계/Issue acceptance | 계획 task | 증거 |
|---|---|---|
| 새 `09-spring/06` sibling과 자동 Gradle discovery | T1, T10, T11 | `projects`, module build, workflow task |
| provider alias와 `@EnableExposedR2dbcRepositories` scan | T1, T3, T4 | catalog/dependency insight, context bean test |
| app-owned pool/database와 pool close | T3, T6 | config source, lifecycle test, context bean |
| nullable ID mapping과 suspend CRUD | T4 | repository integration tests |
| Flow `findAll`/`streamAll` 소비 | T4, T7 | `toList`, `take`, WebTestClient response tests |
| single-call rollback과 explicit outer transaction | T4, T5 | row count/exception assertions |
| outer 없는 partial commit을 과장하지 않음 | T5, T8 | negative service test와 README 설명 |
| cancellation 재전파와 cleanup 후 재사용 | T6 | cancellation + follow-up query |
| invalid R2DBC config의 명확한 실패 | T6 | invalid options acquisition test |
| English/Korean source-equivalent README와 PNG embed | T8, T9, T10 | writer audit, link/pair/visual audit |
| architecture source-backed diagram | T9 | semantic/XML/audit/full-size PNG evidence |
| workflow path/job와 issue metadata 정렬 | T10 | YAML diff, live `gh issue view` |
| Type A plan/final review, lesson, PR/merge contract | P0, P1, T12 | six-lens review tables, lesson commit, PR DoD |

## Risk and rollback matrix

| 위험 | 조기 신호 | 완화 | rollback/rerun |
|---|---|---|---|
| provider API가 catalog 버전과 source tag에서 다름 | dependency insight/compile signature error | resolved jar/source를 기준으로 generic과 mapping을 최소 수정하고 unsupported API를 사용하지 않음 | T1 alias 또는 T4 repository만 되돌리고 dependency evidence 재수집 |
| provider transaction이 outer transaction을 재사용하지 않음 | atomic failure 뒤 row가 남음 | app-owned `db`를 명시한 outer test와 provider source 확인을 함께 수행 | T5 service/test만 재실행; transaction manager 추가 금지 |
| Flow cancellation이 cleanup을 삼킴 | 후속 query가 pool/connection error | `CancellationException` 분리 catch와 shared cleanup contract 사용 | T6 cancellation/lifecycle slice부터 재검증 |
| H2 test state가 context/withTables 사이에 누수 | test 순서에 따라 count/seed 변동 | shared `regular` DB name, deterministic initializer, table cleanup, shared mutex 사용 | T2 harness와 T3 initializer를 함께 되돌리고 H2 test 재실행 |
| workflow가 새 모듈을 검증하지 않음 | path filter와 job task 불일치 | YAML path/job/artifact를 같은 task name으로 대조 | T10 YAML만 수정 후 workflow syntax/read-back |
| diagram audit와 PNG가 불일치 | `failures>0`, weak counts, visual clipping | 해당 locale SVG 또는 공통 topology source를 수정하고 대응 PNG와 en/ko CairoSVG/audits/full-size inspection을 반복 | T9 asset만 재생성, code/docs 변경 보존 |
| README/issue가 stale path를 유지 | link check 또는 live body `rg` hit | source-equivalent docs와 live read-back을 같은 acceptance row로 묶음 | T8/T10 docs/metadata만 수정 |

## 실행 중지 조건

- plan review에서 P0/P1가 남아 있으면 code implementation을 시작하지 않는다.
- 승인된 배치, provider transaction/lifecycle 계약, 또는 compatibility 범위가 바뀌면 plan/spec gate로 돌아가 재승인한다.
- targeted test, compile, workflow path/job, diagram audit 중 하나라도 fresh evidence 없이 완료로 표시하지 않는다.
- PR merge는 이 plan의 구현 완료와 별개의 gate이며, fresh exact-head `승인`·green CI·rebase merge·root `develop` sync 전에는 수행하지 않는다.

## 이 계획 문서의 writer gate

- SPW-01: PASS — Issue #204, 승인된 설계 경로, target module, provider identifiers, unsupported contract를 기록했다.
- SPW-02: PASS — 목표, ordering, files, tests, docs, diagram, workflow, risks, rollback, acceptance traceability를 포함했다.
- SPW-03: PASS — 한국어 technical register를 적용하고 API names, paths, commands, URLs, exact exceptions를 보존했다.
- SPW-04: PASS — 현재 `09-spring` 배치, shared test helper, catalog/BOM, provider source-backed constraints를 반영했다.
- SPW-05: PASS — Markdown read-back, 자리표시자 검사, required-symbol scan, `git diff --check`, `audit-korean-terms.mjs`를 완료했다.

## 현재 상태

- [x] 승인된 설계 문서와 선택 sibling 배치를 기준으로 계획 범위를 고정했다.
- [ ] T1–T12 실행과 각 task의 fresh evidence.
- [x] plan review P0=0/P1=0.
- [x] 사용자 실행 방식 선택 — Inline Execution.
