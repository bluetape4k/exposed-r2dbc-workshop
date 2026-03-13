# Exposed R2DBC Workshop

Kotlin Exposed의 R2DBC 기반 예제를 단계별로 정리한 멀티 모듈 워크숍입니다.
Reactive SQL DSL, Coroutines, Spring WebFlux, 멀티테넌시, 캐시, 라우팅 같은 실전 패턴을 예제와 테스트 중심으로 학습할 수 있습니다.

상세 설명은 [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)에서 확인할 수 있습니다.

## 핵심 포인트

- Kotlin `2.3.20-RC3`, JDK `21+`, Exposed `1.1.1`, Spring Boot `3.5.10`
- 대부분의 예제가 테스트 중심으로 구성되어 있어, 코드보다 테스트를 따라가며 학습하기 좋습니다.
- H2, PostgreSQL, MySQL 기반 시나리오를 함께 검증합니다.
- Spring/WebFlux 모듈은 REST API, 캐시, 멀티테넌시, 라우팅 예제를 포함합니다.

## 요구사항

- JDK 21 이상
- Docker / Colima 등 Testcontainers 실행 환경
- Gradle Wrapper 사용 권장

## 빠른 시작

```bash
# 전체 테스트
./gradlew test

# H2만 사용한 빠른 테스트
./gradlew test -PuseFastDB=true

# 특정 DB만 선택
./gradlew test -PuseDB=H2,POSTGRESQL

# 특정 모듈 테스트
./gradlew :exposed-r2dbc-09-spring-05-exposed-r2dbc-repository-coroutines:test

# Spring 예제 실행
./gradlew :exposed-r2dbc-09-spring-07-spring-suspended-cache:bootRun
```

## 테스트 가이드

- 기본 회귀는 `./gradlew test`입니다.
- Docker 자원이 부족하거나 DB 기동 시간을 줄이고 싶다면 `-PuseFastDB=true`를 먼저 사용하세요.
- 특정 dialect만 확인하고 싶다면 `-PuseDB=H2,POSTGRESQL`처럼 지정할 수 있습니다.
- DB/Testcontainers 기반 모듈은 리소스를 많이 사용하므로, 개발 중에는 모듈 단위로 먼저 검증하는 편이 효율적입니다.

## 추천 학습 경로

1. Spring 진입: [01-spring-boot/spring-webflux-exposed](01-spring-boot/spring-webflux-exposed/README.md)
2. SQL DSL 기초: [03-exposed-r2dbc-basic/exposed-r2dbc-sql-example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)
3. DDL/DML 패턴: [04-exposed-r2dbc-ddl](04-exposed-r2dbc-ddl/01-connection/README.md), [05-exposed-r2dbc-dml](05-exposed-r2dbc-dml/01-dml/README.md)
4. 확장 기능: [06-advanced](06-advanced/README.md)
5. JPA 변환: [07-jpa-convert/01-convert-jpa-basic](07-jpa-convert/01-convert-jpa-basic/README.md)
6. Coroutines / Virtual Threads: [08-r2dbc-coroutines](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md)
7. Spring Repository / Cache: [09-spring](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)
8. 멀티테넌시 / 고성능: [10-multi-tenant](10-multi-tenant/03-multitenant-spring-webflux/README.md), [11-high-performance](11-high-performance/README.md)

## 모듈 맵

| 그룹 | 설명 | 대표 문서 |
|---|---|---|
| `00-shared` | 공통 테스트 인프라, 스키마, 샘플 repository | [Shared](00-shared/exposed-r2dbc-shared/README.md) |
| `01-spring-boot` | Spring WebFlux + Exposed R2DBC 기본 통합 | [Spring WebFlux](01-spring-boot/spring-webflux-exposed/README.md) |
| `03-exposed-r2dbc-basic` | SQL DSL, 조인, 조건절 등 기본기 | [SQL Example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md) |
| `04-exposed-r2dbc-ddl` | 연결 관리, DDL, 스키마 제어 | [Connection](04-exposed-r2dbc-ddl/01-connection/README.md) |
| `05-exposed-r2dbc-dml` | CRUD, 함수, 타입, 트랜잭션 | [DML](05-exposed-r2dbc-dml/01-dml/README.md) |
| `06-advanced` | 암호화, JSON, Money, Custom Column, Jackson/Tink | [Advanced](06-advanced/README.md) |
| `07-jpa-convert` | JPA 패턴을 Exposed R2DBC로 전환 | [JPA Convert](07-jpa-convert/01-convert-jpa-basic/README.md) |
| `08-r2dbc-coroutines` | Coroutines, Flow, Virtual Threads | [Coroutines](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md) |
| `09-spring` | Repository 패턴, Redis 기반 suspended cache | [Spring Examples](09-spring/05-exposed-r2dbc-repository-coroutines/README.md) |
| `10-multi-tenant` | Schema 기반 멀티테넌시 + WebFlux | [Multi-tenant](10-multi-tenant/03-multitenant-spring-webflux/README.md) |
| `11-high-performance` | 캐시 전략, routing datasource, read/write 분리 | [High Performance](11-high-performance/README.md) |

## 주목할 예제

- [09-spring/05-exposed-r2dbc-repository-coroutines](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)
  Spring WebFlux + Coroutines + Exposed repository 패턴
- [09-spring/07-spring-suspended-cache](09-spring/07-spring-suspended-cache/README.md)
  Lettuce coroutine cache와 Exposed repository 조합
- [10-multi-tenant/03-multitenant-spring-webflux](10-multi-tenant/03-multitenant-spring-webflux/README.md)
  Reactor Context + Coroutine Context 기반 tenant 전파
- [11-high-performance/03-routing-datasource](11-high-performance/03-routing-datasource/README.md)
  tenant + read/write 분리 라우팅

## 아키텍처 개요

```text
+-------------------------------------------------------------+
|                    Application Layer                         |
|   Spring WebFlux Controller / Coroutine Service             |
+-------------------+-----------------------------------------+
                    | suspendTransaction { }
+-------------------v-----------------------------------------+
|                  Exposed R2DBC DSL Layer                     |
|   Table DSL · Select/Insert/Update/Delete · Joins · CTE     |
|   Column Types: json, money, crypt, datetime, uuid          |
+-------------------+-----------------------------------------+
                    | R2DBC driver
+-------------------v-----------------------------------------+
|            DynamicRoutingConnectionFactory                   |
|        (read/write split, tenant routing)                   |
+--------+-----------------------+----------------------------+
         | write                 | read
    +----+------+          +-----+-----+
    | Primary   |          | Replica   |
    | DB        |          | DB        |
    +-----------+          +-----------+
```

**멀티테넌시 흐름 (Schema-based):**

```text
HTTP Request
  -> TenantFilter (X-TENANT-ID 헤더 추출)
  -> ReactorContext 에 테넌트 ID 저장
  -> Coroutine Context 로 전파 (ReactorContext -> CoroutineContext)
  -> suspendTransactionWithCurrentTenant { SchemaUtils.setSchema(tenantId) }
```

## Exposed v1 주요 변경사항

기존 `org.jetbrains.exposed` 패키지에서 `org.jetbrains.exposed.v1`로 패키지가 이전되었습니다.

| 변경 전 | 변경 후 |
|---------|---------|
| `org.jetbrains.exposed.sql` | `org.jetbrains.exposed.v1.core` |
| `org.jetbrains.exposed.dao` | `org.jetbrains.exposed.v1.dao` |
| `Transaction.exec(...)` | `suspendTransaction { ... }` (R2DBC) |
| `selectAll()` 즉시 결과 | `selectAll()` Flow 반환 |
| `insert { }` 즉시 실행 | `insert { }` suspend 실행 |

**중요:** R2DBC에서는 모든 DB 접근이 `suspendTransaction` 블록 내부에서 이루어져야 합니다.
`withDb(testDB) { }` / `withTables(testDB, *tables) { }` 헬퍼를 활용하면 테스트 코드가 간결해집니다.

## 새 예제 추가 가이드

새로운 예제를 워크숍에 추가하는 방법입니다.

### 1. 테이블 정의

```kotlin
// src/main/kotlin/.../MySchema.kt
object MyTable : IntIdTable("my_table") {
    val name = varchar("name", 100)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
```

### 2. 예제 테스트 작성

```kotlin
// src/test/kotlin/.../Ex01_MyExample.kt
class Ex01_MyExample : AbstractR2dbcExposedTest() {
    companion object : KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `기능 설명`(testDB: TestDB) = runTest {
        withTables(testDB, MyTable) {
            MyTable.insert { it[name] = "test" }
            val result = MyTable.selectAll().toList()
            result shouldHaveSize 1
        }
    }
}
```

### 3. 체크리스트

- [ ] `AbstractR2dbcExposedTest` 상속
- [ ] `companion object : KLoggingChannel()` 추가
- [ ] `@ParameterizedTest @MethodSource(ENABLE_DIALECTS_METHOD)` 사용
- [ ] `withTables(testDB, ...) { }` 로 격리 보장
- [ ] 공개 API에 한국어 KDoc 작성
- [ ] README.md 예제 섹션 업데이트

## 저장소 구조

```text
00-shared/               공통 테스트 인프라, 스키마, 샘플 repository
01-spring-boot/          Spring WebFlux + Exposed R2DBC 진입점
03-exposed-r2dbc-basic/  SQL DSL 기초 예제
04-exposed-r2dbc-ddl/    연결 관리, DDL, 스키마 제어
05-exposed-r2dbc-dml/    SELECT/INSERT/UPDATE/DELETE, 함수, 타입, 트랜잭션
06-advanced/             암호화, 날짜/시간, JSON, Money, 커스텀 컬럼, Jackson, Tink
07-jpa-convert/          JPA -> Exposed R2DBC 마이그레이션 패턴
08-r2dbc-coroutines/     Coroutines, Flow, Virtual Threads
09-spring/               Repository 패턴, Redis 기반 Suspended Cache
10-multi-tenant/         Schema 기반 멀티테넌시 + Spring WebFlux
11-high-performance/     캐시 전략, 읽기/쓰기 분리 라우팅 DataSource
```

## 개발 팁

- 새로운 예제를 추가할 때는 공개 API에 한국어 KDoc을 작성하세요.
- DB 관련 테스트는 공유 상태를 만들지 않도록 테이블 생성/정리 범위를 좁게 유지하세요.
- 회귀 실패 시에는 전체 빌드보다 먼저 해당 모듈의 `:module:test`를 재현하는 편이 빠릅니다.
- `-PuseFastDB=true` 옵션으로 H2 only 모드를 활성화하면 Docker 없이 빠르게 개발할 수 있습니다.
