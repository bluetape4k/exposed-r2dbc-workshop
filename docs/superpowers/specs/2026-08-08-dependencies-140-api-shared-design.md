# bluetape4k-dependencies 1.4.0 API 및 shared 통합 설계

## 목적

`exposed-r2dbc-workshop`의 예제가 `bluetape4k-dependencies:1.4.0`이 실제로
선택하는 API를 사용하도록 정렬한다. Gradle 해석 결과에서 이 BOM은
`bluetape4k-r2dbc:1.12.1`, `bluetape4k-exposed-r2dbc:1.12.1`,
`org.jetbrains.exposed:*:1.4.0`을 선택한다. 따라서 예제는 존재하지 않는
가상의 1.4.0 라이브러리 API를 가정하지 않고, 1.12.1의 공개 R2DBC DSL과
Exposed v1 API를 기준으로 수정한다.

## 확인된 문제

### 수동 R2DBC 연결/풀 구성 중복

다섯 Spring 예제의 `ExposedR2dbcConfig`가 H2, MySQL, PostgreSQL
`ConnectionFactoryOptions`를 거의 동일하게 직접 만들고 있다. 각 모듈은 풀
크기·수명·테넌트 설정만 다르지만, 드라이버 옵션과 password/timeout 처리까지
복제한다. Ktor 예제에도 동일한 `ConnectionFactoryOptions.builder()`와
`ConnectionPoolConfiguration.builder()` 패턴이 남아 있다.

1.4.0 BOM으로 선택되는 `io.bluetape4k.r2dbc.pool` 공개 API는 다음과 같다.

- `connectionFactoryOptionsOf { driver; protocol; host; port; database; user; password; option(...) }`
- `connectionFactoryOptionsOf(url)` 및 `ConnectionFactoryOptions.mutate()`
- `connectionPoolOf(options) { R2dbcPoolConfig 프로퍼티 설정 }`
- `r2dbcConnectionPool { connection { ... }; pool { ... } }`

### published test fixture와 local shared의 차이

`bluetape4k-exposed-r2dbc-tests:1.12.1`은 `withDb`, `withTables`,
`withSchemas`, `withAutoCommit`, `TestDB`를 이미 제공한다. 그러나 published
`withTables`는 `runCatching`으로 suspend cleanup을 감싸
`CancellationException`을 삼킬 수 있다. 이 workshop의 shared에는 issue #54를
해결한 취소 우선순위와 suppressed-exception 계약 및 회귀 테스트가 이미 있다.
따라서 이 작업에서는 published fixture 전체로 교체하지 않고, local shared의
검증된 취소 계약을 보존한다.

## 설계 결정

1. `00-shared/exposed-r2dbc-shared`에 도메인 중립적인 DB별 옵션 팩토리를 둔다.
   팩토리는 bluetape4k의 `connectionFactoryOptionsOf`를 내부에서 사용하고,
   Testcontainers의 host/port/user/password만 인자로 받는다. Spring/Ktor와
   연결 풀 생명주기는 의존하지 않는다.
2. 모든 Spring/Ktor 예제의 풀 생성은 `connectionPoolOf` 또는
   `r2dbcConnectionPool` DSL로 전환한다. 모듈별 `maxSize`, warm-up, eviction,
   tenant URL은 각 모듈에 남긴다.
3. `ConnectionFactoryOptions`/`ConnectionPoolConfiguration` 직접 builder는
   공통 경로에서 제거한다. 테넌트별 URL을 외부 프로퍼티에서 받아야 하는
   코드는 `connectionFactoryOptionsOf(url)`로 바꾸고, 옵션을 추가할 때는
   `mutate()`를 사용한다.
4. 기존 `R2dbcDatabaseConfig`와 `R2dbcDatabase.connect(connectionPool,
   databaseConfig = config)` 형태는 유지한다. 이 변경은 연결 옵션·풀 API
   정렬이며, Spring Bean 이름이나 transaction dispatcher 계약을 바꾸지 않는다.
5. shared의 `withDb`/`withTables`는 유지하고, 취소·cleanup 테스트를 먼저
   통과시킨다. library 승격 후보는 별도 issue로만 기록하며 이 저장소에서
   외부 library를 직접 수정하지 않는다.

## 대안과 제외 사유

### A안: 각 예제에서 1.4.0 DSL을 직접 호출

변경이 국소적이지만 다섯 Spring 설정과 Ktor 설정에 DB별 옵션이 다시 복제된다.
공통 기능 통합이라는 목표를 충족하지 못하므로 제외한다.

### B안: shared 옵션 팩토리 + 모듈별 pool DSL (채택)

드라이버 옵션의 단일 출처를 만들고, 풀 튜닝과 생명주기는 예제 의도대로
보존한다. Spring/Ktor에 공통 framework 계층을 추가하지 않으며 기존 모듈
경계를 유지한다.

### C안: published `bluetape4k-exposed-r2dbc-tests` 전체로 교체

패키지/API 정렬은 단순하지만 local shared의 cancellation-safe `withTables`
계약을 잃고, 수백 개 예제 import와 테스트를 외부 artifact의 release timing에
결합한다. 이번 범위에서는 채택하지 않는다.

## 테스트 우선 계약

- shared 옵션 팩토리는 H2 driver/protocol/database, MySQL SSL/timeout,
  PostgreSQL prepared-cache/statement-timeout 값을 검증한다.
- 각 변경 Spring 모듈은 `compileKotlin`과 대표 test를 실행한다.
- Ktor/tenant URL 변환은 `connectionFactoryOptionsOf(url)` 결과와 pool
  설정을 단위 테스트 또는 기존 smoke test로 검증한다.
- `WithTablesTest`의 statement failure, cancellation propagation,
  cleanup suppressed-exception 계약은 그대로 통과해야 한다.

## 승격 후보 판단 기준

후보는 다음 네 조건을 모두 만족할 때만 issue로 남긴다.

1. 현재 workshop shared 또는 예제에 실제 구현과 호출자가 있다.
2. `bluetape4k-exposed`의 published 1.12.1 source/JAR와 live GitHub issue에
   같은 기능이 이미 없거나, 기존 기능의 결함을 명확히 개선한다.
3. 도메인 중립이고 다른 R2DBC 소비자가 재사용할 수 있다.
4. 회귀 테스트로 동작·취소·예외 계약을 입증했다.

이번 조사에서 `withTables`의 cancellation-safe cleanup은 published
`withTables`와 중복되지만 동작 결함을 보완하고 local 회귀 테스트가 있으므로
후보로 판정한다. issue 생성 직전에 live duplicate scan을 다시 수행하고,
기존 issue가 발견되면 생성하지 않는다.

## 범위 밖

- PR 생성·merge·auto-merge·tag·release·Maven publication.
- Spring/Ktor 전체 설정을 하나의 framework abstraction으로 합치는 작업.
- published `bluetape4k-exposed-r2dbc-tests`의 API/구현 변경.
- 도메인별 tenant provisioning, repository, cache 정책 변경.
