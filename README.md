# Exposed R2DBC Workshop (Kotlin Exposed R2DBC 학습 자료)

이 저장소는 Kotlin Exposed의 R2DBC 기반 예제와 워크숍을 모아둔 컬렉션입니다. Reactive 환경에서 Exposed를 어떻게 사용하는지 단계별로 살펴볼 수 있습니다.

## Kotlin Exposed R2DBC란?

Kotlin Exposed는 Kotlin 언어로 작성된 SQL 프레임워크입니다. R2DBC 환경에서는 비동기/논블로킹 방식으로 데이터베이스와 통신하며, Kotlin의 타입 안전성과 함께 반응형 데이터 접근을 제공합니다.

### R2DBC의 주요 특징

| 특징                  | 설명                                |
|---------------------|-----------------------------------|
| **논블로킹 I/O**        | 완전한 비동기/논블로킹 데이터베이스 접근            |
| **Reactive Stream** | Reactor, RxJava, Coroutines 완벽 지원 |
| **Backpressure**    | 데이터 스트림의 배압(Backpressure) 제어      |
| **타입 안전성**          | 컴파일 타임에 SQL 오류 감지                 |
| **다양한 DB 지원**       | H2, MySQL, PostgreSQL, MariaDB 등  |

## 주요 라이브러리 버전

| 라이브러리                  | 버전       | 설명                          |
|------------------------|----------|-----------------------------|
| **Kotlin**             | `2.3.10` | 언어 런타임                      |
| **Exposed**            | `1.1.1`  | JetBrains Exposed R2DBC ORM |
| **Spring Boot**        | `3.5.10` | Spring WebFlux 모듈           |
| **kotlinx-coroutines** | `1.10.2` | Kotlin 코루틴 라이브러리            |
| **bluetape4k**         | `1.2.3`  | 내부 유틸리티 라이브러리               |
| **JDK**                | `21+`    | Virtual Threads, ZGC 지원     |

## 학습 가이드

이 워크숍은 다음과 같은 순서로 학습하는 것을 권장합니다:

1. **Spring Boot**: Spring WebFlux + Exposed R2DBC 기본 통합
2. **Exposed 기본**: R2DBC 환경에서의 SQL DSL 사용법
3. **DDL/DML**: 스키마 정의와 데이터 조작
4. **고급 기능**: 암호화, JSON, 커스텀 타입, 날짜/시간 처리
5. **JPA 마이그레이션**: JPA 코드를 Exposed R2DBC로 변환
6. **비동기 처리**: Coroutines, Virtual Threads
7. **Spring 통합**: Repository 패턴, 캐시
8. **멀티테넌시**: 다중 테넌트 아키텍처
9. **고성능**: 캐시 전략, Routing DataSource

## 상세 문서

모든 예제의 상세 설명은 [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)에서 확인할 수 있습니다.

---

## 모듈 목록

### 공유 라이브러리

#### [Exposed R2DBC Shared](00-shared/exposed-r2dbc-shared/README.md)

`exposed-r2dbc-workshop` 프로젝트 전반에서 사용되는 공통 테스트 유틸리티와 리소스를 제공합니다.

**주요 구성 요소:**

| 컴포넌트                              | 설명                                                                            |
|-----------------------------------|-------------------------------------------------------------------------------|
| `AbstractR2dbcExposedTest`        | 모든 테스트 클래스의 기반. UTC 타임존 고정, `enableDialects()` 제공                             |
| `TestDB`                          | 지원 DB enum (H2, H2_MYSQL, H2_PSQL, H2_MARIADB, MARIADB, MYSQL_V8, POSTGRESQL) |
| `Containers`                      | Testcontainers 기반 DB 컨테이너 싱글턴 (MariaDB, MySQL8, PostgreSQL)                   |
| `withDb(testDB) { }`              | 트랜잭션 컨텍스트를 열고 코드 실행, DB별 세마포어로 직렬화                                            |
| `withTables(testDB, *tables) { }` | 테이블 생성 후 코드 실행, 완료 후 자동 정리                                                    |
| `DMLTestData`                     | Cities, Users, Sales 등 공통 테이블 스키마 및 테스트 데이터                                   |
| `BoardSchema`                     | 여러 모듈에서 재사용 가능한 공통 엔티티 스키마                                                    |

---

### Spring Boot 통합

#### [Spring WebFlux with Exposed R2DBC](01-spring-boot/spring-webflux-exposed/README.md)

Spring WebFlux + Kotlin Coroutines + Exposed R2DBC를 이용하여 비동기 REST API를 구축하는 방법을 학습합니다.

**학습 내용:**

- 영화(Movie)·배우(Actor) 다대다 관계 모델링
- `Mono`/`Flux` 기반 비동기 Repository 패턴
- WebFlux Controller → Repository → Exposed R2DBC DSL 계층 구조

---

### Exposed R2DBC 기본

#### [Exposed R2DBC SQL Example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)

Exposed의 SQL DSL(Domain Specific Language)을 R2DBC 환경에서 사용하는 방법을 학습합니다.

**학습 내용:**

- `CityTable`, `UserTable` 정의 및 FK 관계
- `innerJoin`, `leftJoin` 등 타입 안전한 JOIN 쿼리
- `where`, `andWhere`, `orWhere` 등 조건절 구성

---

### Exposed R2DBC DDL (스키마 정의)

#### [Connection Management](04-exposed-r2dbc-ddl/01-connection/README.md)

R2DBC 환경에서 데이터베이스 연결 설정, 커넥션 풀링, 타임아웃 등 연결 관리의 핵심 개념을 학습합니다.

**지원 데이터베이스:**

| 데이터베이스     | R2DBC URL 형식                     |
|------------|----------------------------------|
| H2         | `r2dbc:h2:mem:///dbname`         |
| PostgreSQL | `r2dbc:postgresql://host/dbname` |
| MySQL      | `r2dbc:mysql://host/dbname`      |
| MariaDB    | `r2dbc:mariadb://host/dbname`    |

#### [Schema Definition Language (DDL)](04-exposed-r2dbc-ddl/02-ddl/README.md)

Exposed의 DDL 기능을 R2DBC 환경에서 학습합니다. 테이블, 컬럼, 인덱스, 제약조건 정의 방법을 익힙니다.

---

### Exposed R2DBC DML (데이터 조작)

#### [DML 기본 연산](05-exposed-r2dbc-dml/01-dml/README.md)

SELECT, INSERT, UPDATE, DELETE, UPSERT, MERGE, JOIN, UNION, CTE 등 거의 모든 SQL DML 패턴을 **27개 테스트 파일**로 다룹니다.

| 카테고리        | 주요 예제                                                 |
|-------------|-------------------------------------------------------|
| **기본 CRUD** | SELECT(조건/페이징), INSERT(단건/배치), UPDATE, UPSERT, DELETE |
| **집계/정렬**   | EXISTS, DISTINCT ON, COUNT, GROUP BY, ORDER BY        |
| **조인**      | INNER/LEFT/CROSS JOIN, LATERAL JOIN, 다대다 조인           |
| **고급 DML**  | INSERT INTO...SELECT, REPLACE, MERGE, RETURNING       |
| **집합 연산**   | UNION, UNION ALL, INTERSECT, EXCEPT                   |
| **표현식/유틸**  | 동적 쿼리 수정, 산술 연산, 컬럼 변환, 복합 조건                         |
| **CTE/분석**  | 재귀 CTE, EXPLAIN, 배치 조회, DUAL 테이블                      |

#### [컬럼 타입](05-exposed-r2dbc-dml/02-types/README.md)

Boolean, Char, Numeric, Double, Array, Unsigned, Blob, UUID 등 SQL 데이터 타입별로 **9개 테스트 파일**로 학습합니다.

| 타입 카테고리   | 지원 타입                                                         |
|-----------|---------------------------------------------------------------|
| **기본 타입** | Boolean, Char/String, Integer, Long, Float, Double, Decimal   |
| **고급 타입** | Array (PostgreSQL/H2), Unsigned, Blob, Java UUID, Kotlin UUID |

#### [SQL 함수](05-exposed-r2dbc-dml/03-functions/README.md)

문자열/비트 연산, 수학 함수, 통계 함수, 삼각 함수, **Window Function** 등 SQL 내장 함수와 커스텀 함수를 **6개 테스트 파일**로 다룹니다.

| 카테고리    | 함수                                                                  |
|---------|---------------------------------------------------------------------|
| **문자열** | upper, lower, concat, charLength, substring, trim, locate           |
| **수학**  | abs, ceil, floor, round, sqrt, exp, power, sign                     |
| **통계**  | stdDevPop, stdDevSamp, varPop, varSamp                              |
| **삼각**  | sin, cos, tan, asin, acos, atan, cot, degrees, radians, pi          |
| **윈도우** | rowNumber, rank, denseRank, lead, lag, firstValue, lastValue, ntile |

#### [트랜잭션 관리](05-exposed-r2dbc-dml/04-transactions/README.md)

트랜잭션 격리 수준, Raw SQL 실행, 파라미터 바인딩, 쿼리 타임아웃, 중첩 트랜잭션(Savepoint) 등 트랜잭션 제어의 핵심 패턴을 **6개 테스트 파일**로 학습합니다.

---

### 고급 기능

#### [Exposed R2DBC Crypt (투명한 컬럼 암호화)](06-advanced/01-exposed-r2dbc-crypt/README.md)

`exposed-crypt` 확장을 사용하여 R2DBC 환경에서 데이터베이스 컬럼을 투명하게 암호화/복호화하는 방법을 학습합니다.

- 지원 알고리즘: `AES_256_PBE_CBC`, `AES_256_PBE_GCM`, `BLOW_FISH`, `TRIPLE_DES`
- DSL과 DAO 스타일 모두 지원
- **주의**: 비결정적 암호화로 `WHERE` 절 검색 불가 → 검색이 필요한 경우 `10-exposed-r2dbc-jasypt` 참조

#### [Exposed R2DBC JavaTime (java.time 통합)](06-advanced/02-exposed-r2dbc-javatime/README.md)

Java 8의 `java.time` (JSR-310) API와 Exposed R2DBC의 통합 방법을 학습합니다.

| 컬럼 타입                         | Java 타입                    |
|-------------------------------|----------------------------|
| `date(name)`                  | `java.time.LocalDate`      |
| `time(name)`                  | `java.time.LocalTime`      |
| `datetime(name)`              | `java.time.LocalDateTime`  |
| `timestamp(name)`             | `java.time.Instant`        |
| `timestampWithTimeZone(name)` | `java.time.OffsetDateTime` |
| `duration(name)`              | `java.time.Duration`       |

#### [Exposed R2DBC Kotlinx-Datetime](06-advanced/03-exposed-r2dbc-kotlin-datetime/README.md)

`kotlinx.datetime` 라이브러리와 Exposed R2DBC의 통합 방법을 학습합니다. 멀티플랫폼 프로젝트에 적합합니다.

#### [Exposed R2DBC Json (JSON/JSONB 지원)](06-advanced/04-exposed-r2dbc-json/README.md)

`exposed-json` 모듈(`kotlinx.serialization` 기반)을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 다루는 방법을 학습합니다.

- `.extract<T>(path)` - JSON 필드 추출
- `.contains(value)` - JSON 포함 여부 확인
- `.exists(path)` - JSONPath 존재 여부 확인

#### [Exposed R2DBC Money (금융 데이터 처리)](06-advanced/05-exposed-r2dbc-money/README.md)

`exposed-money` 모듈을 사용하여 R2DBC 환경에서 JavaMoney(`javax.money`) API로 통화 값을 안전하게 처리합니다. `compositeMoney`가 금액(`DECIMAL`)과 통화(
`VARCHAR`) 컬럼을 단일 속성으로 관리합니다.

#### [커스텀 컬럼 타입](06-advanced/06-exposed-r2dbc-custom-columns/README.md)

사용자 정의 컬럼 타입을 구현하는 방법을 학습합니다.

| 기능             | 설명                                    |
|----------------|---------------------------------------|
| **커스텀 ID 생성기** | Snowflake, KSUID, TimebasedUUID 자동 생성 |
| **투명한 압축**     | LZ4, Snappy, Zstd를 이용한 자동 압축/해제       |
| **결정적 암호화**    | AES 등 결정적 암호화로 `WHERE` 절 검색 가능        |
| **바이너리 직렬화**   | Kryo/Fory 기반 객체 직렬화 + 압축 조합           |

#### [커스텀 Entity (ID 생성 전략)](06-advanced/07-exposed-r2dbc-custom-entities/README.md)

Snowflake, KSUID, Time-based UUID 등 다양한 ID 생성 전략을 캡슐화한 커스텀 기반 테이블/엔티티 클래스를 구현합니다.

| 기반 클래스                     | ID 타입            | 생성 전략               |
|----------------------------|------------------|---------------------|
| `SnowflakeIdTable`         | `Long`           | Snowflake 알고리즘      |
| `KsuidTable`               | `String (27자)`   | KSUID               |
| `KsuidMillisTable`         | `String (27자)`   | 밀리초 정밀도 KSUID       |
| `TimebasedUUIDTable`       | `java.util.UUID` | 시간 기반(버전 1) UUID    |
| `TimebasedUUIDBase62Table` | `String (22자)`   | 시간 기반 UUID + Base62 |

#### [Exposed R2DBC Jackson (Jackson 기반 JSON)](06-advanced/08-exposed-r2dbc-jackson/README.md)

Jackson 2.x 라이브러리를 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리합니다. `@Serializable` 어노테이션 없이 표준 Kotlin 데이터 클래스를 사용할 수 있습니다.

#### [Exposed R2DBC Fastjson2](06-advanced/09-exposed-r2dbc-fastjson2/README.md)

Alibaba Fastjson2 라이브러리를 사용하여 JSON 컬럼을 처리하는 방법을 학습합니다. 뛰어난 JSON 직렬화 성능이 필요한 애플리케이션에 적합합니다.

#### [Exposed R2DBC Jasypt (결정적 암호화)](06-advanced/10-exposed-r2dbc-jasypt/README.md)

Jasypt를 사용하여 R2DBC 환경에서 **결정적(검색 가능한)** 암호화를 구현합니다. 동일 평문이 항상 동일 암호문을 생성하므로 `WHERE` 절에서 직접 쿼리할 수 있습니다.

- `jasyptVarChar(name, length, encryptor)` - 검색 가능한 암호화 문자열
- `jasyptBinary(name, length, encryptor)` - 검색 가능한 암호화 바이트 배열

#### [Exposed R2DBC Jackson 3](06-advanced/11-exposed-r2dbc-jackson3/README.md)

Jackson 3.x 버전을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리하는 방법을 학습합니다.

---

### JPA 마이그레이션

#### [JPA 기본 기능 변환](07-jpa-convert/01-convert-jpa-basic/README.md)

JPA의 기본 패턴을 Exposed R2DBC DSL/DAO로 변환하는 방법을 학습합니다.

| JPA                          | Exposed DSL (R2DBC)   | Exposed DAO                         |
|------------------------------|-----------------------|-------------------------------------|
| `@Entity`                    | `Table` / `IdTable`   | `Entity` + `EntityClass`            |
| `@Id` + `@GeneratedValue`    | `LongIdTable`         | `LongEntity`                        |
| `@Enumerated(STRING)`        | `enumerationByName()` | `enumerationByName()`               |
| `@Convert`                   | Custom `ColumnType`   | Custom `ColumnType`                 |
| `@OneToOne`                  | `reference()`         | `referencedOn` / `backReferencedOn` |
| `@OneToMany`                 | FK `reference()`      | `referrersOn`                       |
| `@ManyToMany` + `@JoinTable` | 중간 테이블 정의             | `via`                               |

**예제 도메인:** Simple Entity, Blog (1:1/1:N/N:M 관계), Person (Many-to-One), Task (Enum 매핑), Custom Column (value class 기반)

---

### 코루틴 & 가상 스레드

#### [Coroutines 기본](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md)

Exposed R2DBC를 Kotlin Coroutines 환경에서 사용하는 방법을 학습합니다.

```kotlin
// 순차 트랜잭션
suspend fun getUsers() = suspendTransaction {
    Users.selectAll().toList()
}

// 병렬 트랜잭션
val (users, orders) = awaitAll(
    suspendTransactionAsync { Users.selectAll().toList() },
    suspendTransactionAsync { Orders.selectAll().toList() }
)
```

- `Ex01_Coroutines`: 순차/병렬 트랜잭션, 중첩 트랜잭션, 비동기 작업 조합
- `Ex02_CoroutinesFlow`: Flow 기반 결과 수집, 병렬 실행 패턴

#### [Virtual Threads 기본](08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md)

Exposed R2DBC를 Java 21 Virtual Threads 환경에서 사용하는 방법을 학습합니다. 블로킹 코드 스타일을 유지하면서 고성능 비동기 처리를 구현합니다.

| 특성        | 플랫폼 스레드  | Virtual Threads |
|-----------|----------|-----------------|
| 생성 비용     | 높음       | 매우 낮음           |
| 메모리 사용    | ~1MB/스레드 | ~1KB/스레드        |
| 최대 동시 스레드 | 수천 개     | 수백만 개           |

---

### Spring 통합

#### [Exposed R2DBC Repository (코루틴)](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)

`ExposedR2dbcRepository<T, ID>` 인터페이스를 구현하여 코루틴 환경에서 비동기 Repository 패턴을 적용합니다.

**기술 스택:** Spring Boot WebFlux + Exposed R2DBC + Coroutines + SpringDoc OpenAPI (Swagger UI)

**API 엔드포인트:**

- `GET/POST/DELETE /movies` - 영화 CRUD
- `GET/POST/DELETE /actors` - 배우 CRUD
- `GET /movie-actors/{movieId}` - 영화별 배우 목록

**핵심 패턴:** `Flow`의 `bufferUntilChanged`를 활용한 다대다 Join 결과 그룹핑

#### [Suspended Cache](09-spring/07-spring-suspended-cache/README.md)

Lettuce(Redis Coroutines API) 기반의 Suspended Cache를 코루틴 환경에서 Exposed R2DBC와 함께 사용하는 방법을 학습합니다.

**기술 스택:** Spring Boot WebFlux + Exposed R2DBC + Lettuce Redis + Fory/Kryo5 직렬화 + LZ4/Snappy/Zstd 압축

**캐시 패턴:** Decorator 패턴으로 DB 직접 조회(`Default`)와 Redis 캐시 적용(`Cached`) 구현 분리

```
[Controller] → [CachedRepository] → [Redis Cache]
                      ↓ (cache miss)
              [DefaultRepository] → [R2DBC Database]
```

---

### 멀티테넌시

#### [Spring WebFlux + Multitenant](10-multi-tenant/03-multitenant-spring-webflux/README.md)

WebFlux와 Coroutines를 이용하여 Schema 기반 멀티테넌시를 구현합니다.

**동작 원리:**

1. `TenantFilter`가 HTTP 헤더 `X-TENANT-ID`를 읽어 `ReactorContext`에 저장
2. `TenantId`(`CoroutineContext.Element`)를 통해 코루틴 내에서 테넌트 전파
3. `suspendTransactionWithCurrentTenant`가 트랜잭션 시작 시 `SET SCHEMA` 실행

```bash
# 한국어 테넌트
curl -H "X-TENANT-ID: korean" http://localhost:8080/actors

# 영어 테넌트
curl -H "X-TENANT-ID: english" http://localhost:8080/actors
```

> **참고**: R2DBC 환경에서 MySQL은 스키마 생성 권한 문제로 지원되지 않습니다. H2 또는 PostgreSQL을 사용하세요.

---

### 고성능

#### [캐시 전략 (코루틴)](11-high-performance/02-cache-strategies-r2dbc/README.md)

Redisson + Exposed R2DBC를 활용한 캐싱 전략의 **Kotlin Coroutines 기반 비동기 버전**입니다.

| 캐시 전략             | 설명                    |
|-------------------|-----------------------|
| **Read Through**  | 캐시 미스 시 DB 조회 후 캐시 적재 |
| **Write Through** | 캐시 저장 시 DB에 즉시 동기 반영  |
| **Write Behind**  | 캐시 저장 후 DB에 비동기 배치 반영 |
| **Read-Only**     | 읽기 전용 데이터 캐시          |

**기술 스택:** Spring Boot WebFlux + Exposed R2DBC + Redisson (MapCache, Near Cache) + Fory/Kryo5 + LZ4/Snappy/Zstd

#### [Routing DataSource (WebFlux + Exposed R2DBC)](11-high-performance/03-routing-datasource/README.md)

Spring WebFlux 요청 컨텍스트를 기반으로 **테넌트 + 읽기/쓰기 분리** 라우팅을 적용하는 Exposed R2DBC 예제입니다.

**라우팅 규칙:**

- tenant 키: `X-Tenant-Id` 헤더 (미지정 시 `default`)
- read-only 키: `/routing/marker/readonly` 경로 또는 `X-Read-Only: true` 헤더
- 최종 라우팅 키: `<tenant>:<rw|ro>`

---

## 시작하기

### 사전 요구사항

- JDK 21 이상
- Gradle 8.x 이상
- Docker (Testcontainers 사용 시)

### 프로젝트 빌드

```bash
# 전체 프로젝트 빌드
./gradlew build

# 특정 모듈 테스트 실행
./gradlew :01-dml:test

# 특정 테스트 클래스만 실행
./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"
```

### 테스트 대상 DB 선택

기본값은 **H2, PostgreSQL, MySQL V8** 3가지를 대상으로 테스트합니다.
Gradle 프로퍼티로 테스트 범위를 조절할 수 있습니다.

```bash
# H2 계열만 테스트 (빠른 로컬 개발용)
./gradlew test -PuseFastDB=true

# 특정 DB만 지정해서 테스트
./gradlew test -PuseDB=H2,POSTGRESQL
./gradlew test -PuseDB=H2,POSTGRESQL,MYSQL_V8,MARIADB

# 기본값으로 테스트 (H2 + PostgreSQL + MySQL V8)
./gradlew test
```

`-PuseDB`에 사용 가능한 값 (`TestDB` enum 이름):

| 값            | 설명                          |
|--------------|------------------------------|
| `H2`         | H2 (인메모리, 기본 모드)         |
| `H2_MYSQL`   | H2 (MySQL 호환 모드)           |
| `H2_MARIADB` | H2 (MariaDB 호환 모드)         |
| `H2_PSQL`    | H2 (PostgreSQL 호환 모드)      |
| `MARIADB`    | MariaDB (Testcontainers)     |
| `MYSQL_V8`   | MySQL 8.x (Testcontainers)   |
| `POSTGRESQL` | PostgreSQL (Testcontainers)  |

> [!NOTE]
> 우선순위: `-PuseDB` > `-PuseFastDB` > 기본값 (H2, POSTGRESQL, MYSQL_V8)

### 테스트 패턴

```kotlin
class Ex01_Select: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `select example`(testDB: TestDB) = runTest {
        withTables(testDB, MyTable) {
            // Exposed R2DBC DSL 사용
        }
    }
}
```

### IDE 설정

IntelliJ IDEA를 권장합니다. Kotlin 플러그인이 설치되어 있어야 합니다.

## 기여하기

이 프로젝트는 학습 목적으로 제작되었습니다. 오타 수정, 예제 추가, 번역 개선 등 모든 기여를 환영합니다.

## 라이선스

Apache License 2.0
