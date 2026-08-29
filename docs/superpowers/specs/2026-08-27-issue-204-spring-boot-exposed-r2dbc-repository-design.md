# Issue #204 Spring Boot Exposed R2DBC Repository 배치·설계

## 설계 상태

- Issue: [#204](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/204)
- 분류: Type A — 새 Gradle 모듈·의존성·README·diagram을 추가하는 full feature
- 선택 배치: `09-spring/06-exposed-spring-boot-r2dbc-repository`
- 승인 경계: 이 문서 이후 구현 계획과 코드 변경을 시작한다.

## 문제와 목표

`bluetape4k-exposed:1.12.1`에는 Spring Data repository scanning과 Exposed R2DBC
transaction을 연결하는 `bluetape4k-exposed-spring-boot-r2dbc` 모듈이 있다.
현재 workshop에는 `io.bluetape4k.exposed.r2dbc.repository.R2dbcRepository`를
직접 구현하는 `09-spring/05-exposed-r2dbc-repository-coroutines`만 있어, 두
repository 접근 방식의 책임 경계와 선택 기준을 나란히 학습하기 어렵다.

이번 변경은 provider adapter의 최소 운영 패턴을 별도 sibling으로 추가한다.
애플리케이션이 `ConnectionPool`과 `R2dbcDatabase`를 소유하고,
`@EnableExposedR2dbcRepositories`가 repository interface를 검색하며,
repository 호출과 명시적 outer `suspendTransaction`의 범위를 테스트로 고정하는
것이 목표다.

### 2.0.0 provider 계약 migration

이 설계의 최초 기준은 `bluetape4k-exposed:1.12.1`이었다. 현재 중앙 catalog가
해석하는 `2.0.0-SNAPSHOT` 개발선은 direct JDK proxy에서
`InvocationTargetException.targetException`을 wrapper 없이 재전파한다. 따라서
제약 위반의 현재 consumer 관찰값은 `IllegalArgumentException`이며, 당시
`UndeclaredThrowableException` 단언은 1.12.1 historical surface로만 보존한다.
transaction ownership, rollback, partial commit 의미는 그대로 유지한다.

## 배치 조사 결과

`exposed-workshop`의 live `develop`와 로컬 checkout을 대조했다.

| 근거 | 확인 결과 | 이 저장소에 대한 의미 |
|---|---|---|
| `09-spring/04-exposed-repository` | Spring MVC에서 `io.bluetape4k.exposed.jdbc.repository.JdbcRepository` 사용 | 동기 Exposed JDBC repository의 Spring 챕터 배치 |
| `09-spring/05-exposed-repository-coroutines` | WebFlux coroutine에서도 JDBC `JdbcRepository`와 `newSuspendedTransaction` 사용 | blocking JDBC를 coroutine으로 감싼 변형의 sibling 배치 |
| `09-spring/06-spring-cache`, `09-spring/07-spring-suspended-cache` | repository 다음에 cache 변형 배치 | `09-spring/06`은 현재 R2DBC workshop에서 비어 있어 새 repository sibling에 사용할 수 있음 |
| 정확한 adapter 검색 | `ExposedR2dbcRepository`, `EnableExposedR2dbcRepositories`, `bluetape4k-exposed-spring-boot-r2dbc` 결과 없음 | `exposed-workshop`에는 이 Spring Boot Exposed R2DBC adapter 예제가 아직 없음 |
| 현재 workshop | `09-spring/05-exposed-r2dbc-repository-coroutines`는 수동 R2DBC repository, `09-spring/07-spring-suspended-cache`는 R2DBC cache | 기존 모듈을 확장하지 않고 `09-spring/06`에 의미가 드러나는 sibling을 추가 |

## 결정

새 모듈을 `09-spring/06-exposed-spring-boot-r2dbc-repository`에 둔다.
이름은 Spring Boot adapter와 provider artifact를 명시하면서 기존 `05`의 수동
R2DBC repository와 구별한다. `settings.gradle.kts`의 leaf-directory 자동 등록을
사용하므로 별도 include 문은 추가하지 않는다.

기존 `09-spring/05-exposed-r2dbc-repository-coroutines`의 Movie/Actor 도메인과
직접 구현한 `R2dbcRepository`는 변경하지 않는다. 새 모듈은 작은 Product 도메인을
사용해 adapter 계약과 transaction 소유권만 보여 준다.

## 대안과 선택 사유

### A안: `02-alternatives-to-jpa`에 sibling 추가

Issue 본문의 오래된 경로와 장 제목에는 맞지만, `exposed-workshop`의 Exposed
JDBC repository 관례는 `09-spring`에 있다. Spring Data R2DBC 자체를 비교하는
`02-alternatives-to-jpa/r2dbc-example`와 repository adapter를 한 챕터에 두면
framework 비교와 Exposed repository 통합의 학습 목적이 섞인다. **기각한다.**

### B안: 기존 `09-spring/05`에 adapter를 함께 추가

새 모듈은 없지만 직접 구현한 `R2dbcRepository`와 Spring Data-style
`ExposedR2dbcRepository`의 transaction 및 mapping 계약이 한 모듈에 섞인다.
README와 테스트가 서로 다른 adapter를 설명하게 되어 책임 경계가 약해진다.
**기각한다.**

### B′안: `09-spring/06`에 새 sibling 추가

`exposed-workshop`의 `04` 동기 repository → `05` coroutine repository라는
순서를 계승하고, 현재 workshop의 비어 있는 `06`과 다음 `07` cache 사이에
Spring Boot Exposed R2DBC adapter를 배치한다. 기존 모듈을 보존하면서 두
접근 방식을 직접 비교할 수 있으므로 **채택한다.**

## 모듈 경계와 구성

### Gradle·의존성

- `gradle/libs.versions.toml`에 버전 없는
  `bluetape4k-exposed-spring-boot-r2dbc` alias를 추가한다.
- 새 모듈은 Spring Boot plugin, Kotlin Spring plugin, Exposed plugin과
  `:exposed-r2dbc-shared`를 사용한다.
- provider adapter, `bluetape4k-r2dbc`, Exposed R2DBC, Spring WebFlux,
  coroutines, H2 및 기존 shared test fixture만 추가한다.
- 새로운 외부 dependency나 repository abstraction은 추가하지 않는다.

### 애플리케이션·인프라

- `@SpringBootApplication(proxyBeanMethods = false)`와
  `@EnableExposedR2dbcRepositories(basePackages = [...repository...])`로
  애플리케이션을 기동한다.
- `ConnectionFactoryOptions`와 `ConnectionPool`은 모듈 설정이 만들고 종료한다.
- `R2dbcDatabase`는 해당 pool을 사용해 애플리케이션 bean으로 등록한다.
- provider auto-configuration에 pool, database, schema, 또는
  `ReactiveTransactionManager` 생성을 맡기지 않는다.
- H2 기본 경로와 기존 `TestDB`/shared lifecycle을 사용하되, Docker가 필요한
  DB matrix를 기본 검증으로 강제하지 않는다.

### 도메인·repository

- `Products: LongIdTable`와 nullable ID를 가진 `ProductRecord`를 정의한다.
- `ProductR2dbcRepository`는
  `io.bluetape4k.spring.data.exposed.r2dbc.repository.ExposedR2dbcRepository<ProductRecord, Long>`를
  확장한다.
- `table`, `extractId`, `toDomain`, `toPersistValues`를 명시적으로 구현한다.
- 핵심 호출은 `save`, `findByIdOrNull`, `findAll`/`streamAll`, `count`,
  `deleteById`로 제한한다. `QueryByExample`, projection, provider가 안정화하지
  않은 advanced query를 추측하지 않는다.

### transaction·lifecycle 서비스

- 단일 repository 호출은 provider의 내부 `suspendTransaction`으로 commit 또는
  rollback 된다.
- 여러 repository 호출을 한 원자 단위로 묶어야 하는 서비스 메서드는
  애플리케이션이 소유한 `R2dbcDatabase`를 사용해 명시적 outer
  `suspendTransaction` 안에서 호출한다.
- pool close는 application lifecycle의 책임이며 adapter가 pool을 닫는다고
  문서화하지 않는다.
- cancellation은 `CancellationException`을 일반 오류로 변환하지 않고
  cleanup 후 재전파한다.

## 데이터 흐름

```text
HTTP/suspend caller
        |
        v
Product service --(optional outer suspendTransaction)--> repository proxy
                                                          |
                           @EnableExposedR2dbcRepositories |
                                                          v
                                          ExposedR2dbcRepository mapping
                                                          |
                                                          v
                                   application-owned R2dbcDatabase
                                                          |
                                                          v
                                      ConnectionPool -> H2/R2DBC driver
```

`findAll()`과 `streamAll()`은 Flow 소비 경계를 보여 주고, 여러 쓰기 호출의
원자성은 outer transaction 테스트에서만 주장한다. Spring `@Transactional`이
이 provider의 suspend repository 호출을 자동으로 감싼다고 설명하지 않는다.

## 오류·실패 계약

| 상황 | 기대 동작 | 검증 위치 |
|---|---|---|
| repository package scan | context 기동과 repository bean 등록 성공 | Spring context test |
| 단일 저장 성공 | ID가 반영되고 후속 조회에서 보임 | repository test |
| 단일 저장/삭제 예외 | 해당 호출 transaction만 rollback | repository test |
| 복합 호출 예외 | outer `suspendTransaction` 전체 rollback | transaction service test |
| outer transaction 없이 두 호출 | 하나의 원자 단위로 취급하지 않음 | negative transaction test |
| Flow cancellation | `CancellationException` 재전파, 후속 DB 사용 가능 | cancellation test |
| pool close | 애플리케이션이 명시적으로 close하고 재사용을 거부 | lifecycle/config test |
| 잘못된 R2DBC 설정 | context 또는 connection 획득이 명확히 실패 | negative configuration test |

## 테스트 설계

1. **Context/scan** — `@SpringBootTest`에서 `ProductR2dbcRepository` bean과
   application-owned `R2dbcDatabase`를 확인한다.
2. **CRUD/Flow** — `save`, nullable ID 조회, `findAll().toList()`,
   `streamAll().take(...)`, `count`, `deleteById`를 H2에서 검증한다.
3. **Transaction** — 단일 호출 rollback, outer transaction 안의 성공/실패
   복합 호출, outer transaction 없이 호출한 경우의 partial-commit 경계를
   각각 고정한다.
4. **Cancellation** — 취소된 Flow/호출에서 `CancellationException`이
   유지되고 cleanup 뒤 새 호출이 가능함을 검증한다.
5. **Lifecycle/configuration** — pool close를 한 번만 수행하고 잘못된 URL 또는
   필수 설정 누락이 조용히 성공하지 않음을 확인한다.

테스트는 테스트 간 table state와 `R2dbcDatabase`를 격리하며, cleanup 중
`CancellationException`을 삼키지 않는다. 실제 Testcontainers backend는
기본 DoD가 아니라 명시적 추가 검증으로 둔다.

## 문서·diagram 범위

- 새 모듈에 `README.md`와 `README.ko.md`를 source-equivalent 구조로 추가한다.
- 두 README에는 모듈 목적, 기존 `05` 수동 repository와의 차이, application-owned
  pool/database, repository scanning, transaction 예제, 지원하지 않는 범위,
  테스트 명령을 포함한다.
- `docs/images/readme-diagrams/`에 동일 source에서 생성한 English/Korean
  architecture SVG/PNG pair를 추가하고 README에는 PNG만 삽입한다.
- 다이어그램은 caller → repository scan/proxy → Exposed mapping →
  `R2dbcDatabase` → pool/driver의 책임 경계와 single-call/outer-transaction
  분기를 보여 준다. 로고나 source에 없는 component는 그리지 않는다.
- 루트 `README.md`/`README.ko.md`와 필요한 chapter/module index 및
  `.github/workflows/Examples.yml` path/job을 새 모듈에 맞게 갱신한다.

## 호환성·범위 밖

- Kotlin/Gradle/Spring Boot/Exposed 버전은 현재 catalog를 기준으로 하며
  repository-local AGENTS의 오래된 버전 문구를 새로 복제하지 않는다.
- Spring Data R2DBC repository를 제거하거나 기존 `05` module API를 바꾸지 않는다.
- provider library source 수정, `QueryByExample`/projection/saveAll 계약 확장,
  Spring reactive transaction manager 통합, 외부 DB credential, release/tag,
  기존 unrelated README 정리는 범위 밖이다.

## 수용 기준과 DoD

- 새 `09-spring/06` Gradle module이 자동 discovery되고 애플리케이션 context가
  repository bean을 등록한다.
- suspend CRUD와 Flow/streaming 경계, 단일/복합 transaction 경계,
  cancellation/rollback/pool close/invalid config가 테스트로 고정된다.
- 기존 `09-spring/05` 수동 R2DBC repository와 adapter의 차이를 양쪽 README에
  명시한다.
- English/Korean README와 source-equivalent SVG/PNG diagram pair가 존재하고
  README PNG refs가 실제 asset을 가리킨다.
- `./gradlew projects`, 새 모듈 targeted test/compile, 영향받은 Examples workflow
  path 검증, `git diff --check`가 통과한다.
- 설계·계획·구현·review·verification evidence가 Issue #204와 연결되고,
  PR 본문 마지막에는 `## DoD Status`가 온다.
- PR merge는 fresh exact-head approval과 CI green을 확인한 뒤 **rebase merge**로
  수행하고, merge 후 root `develop`을 `origin/develop`과 동기화한다.

## 근거 원장

- `exposed-workshop/09-spring/04-exposed-repository`
- `exposed-workshop/09-spring/05-exposed-repository-coroutines`
- `exposed-r2dbc-workshop/09-spring/05-exposed-r2dbc-repository-coroutines`
- `exposed-r2dbc-workshop/09-spring/07-spring-suspended-cache`
- `exposed-r2dbc-workshop/settings.gradle.kts`
- `exposed-r2dbc-workshop/gradle/libs.versions.toml`
- provider source tag `1.12.1`:
  `spring-boot/r2dbc/src/main/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/repository/ExposedR2dbcRepository.kt`
- provider source tag `1.12.1`:
  `spring-boot/r2dbc/src/main/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/repository/config/EnableExposedR2dbcRepositories.kt`
- provider source tag `1.12.1`:
  `spring-boot/r2dbc/src/main/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/repository/support/SimpleExposedR2dbcRepository.kt`
- 공식 manual:
  https://github.com/bluetape4k/bluetape4k-exposed/blob/1.12.1/docs/manual/en/modules/bluetape4k-exposed-spring-boot-r2dbc.md

현재 개발선 provider source:
`https://github.com/bluetape4k/bluetape4k-exposed/blob/develop/spring-boot/r2dbc/src/main/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/repository/support/ExposedR2dbcRepositoryFactory.kt`

## Writer gate

- SPW-01: 완료 — Issue #204, reader, source paths, provider tag, unsupported
  contracts, and exact identifiers are recorded.
- SPW-02: 완료 — problem, alternatives, boundaries, data flow, failures,
  compatibility, tests, acceptance criteria, and DoD are present.
- SPW-03: 완료 — Korean technical register pass; API names, paths, commands,
  URLs, and uncertainty are preserved.
- SPW-04: 완료 — exposed-workshop placement, current workshop gaps, and
  provider API claims are source-backed.
- SPW-05: 완료 — rendered Markdown read-back completed; no placeholder,
  contradictory placement, or unresolved scope remains.
