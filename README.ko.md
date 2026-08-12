> English version: [README.md](README.md)

# Exposed R2DBC Workshop

[![CI](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/workflows/ci.yml/badge.svg)](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-25-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

![Exposed R2DBC workshop 작업대 일러스트](./docs/assets/exposed-r2dbc-workshop-workbench.png)

Kotlin Exposed의 R2DBC 기반 예제를 단계별로 정리한 멀티 모듈 워크숍입니다.
Reactive SQL DSL, Coroutines, Spring WebFlux, 멀티테넌시, 캐시, 라우팅 같은 실전 패턴을 예제와 테스트 중심으로 학습할 수 있습니다.

## 프로젝트 목적

`exposed-r2dbc-workshop`은 coroutine-first, test-backed 예제로 Kotlin Exposed R2DBC를 학습하는 워크숍입니다.
Reactive database access, WebFlux 통합, schema lifecycle, DDL/DML, multi-tenancy, cache, routing 패턴을 다룹니다.

## 제공 기능

- **Reactive SQL 학습 경로** — shared test infrastructure부터 high-performance routing까지
- **Coroutine/R2DBC 예제** — `suspendTransaction`, Flow collection, WebFlux, Ktor request handling 포함
- **Multi-database 검증** — H2, PostgreSQL, MySQL, MariaDB
- **운영형 패턴** — repository, cache, multi-tenant schema, routing datasource,
  realtime outbox, HTTP client outbox/idempotency, observability/readiness 예제

상세 설명은 [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)에서 확인할 수 있습니다.

<!-- README_VISUAL_OVERVIEW:START -->
## Overview Diagram

![Exposed R2DBC Workshop overview diagram](docs/images/readme-diagrams/root-readme-overview-01.png)

## Module Composition Chart

![Exposed R2DBC Workshop module composition chart](docs/images/readme-charts/root-readme-module-chart-01.png)
<!-- README_VISUAL_OVERVIEW:END -->

## 핵심 포인트

- Kotlin `2.3.20`, JDK `25+`, Exposed `1.1.1`, Spring Boot `3.5.11`, Bluetape4k `1.5.0-Beta1`
- 대부분의 예제가 테스트 중심으로 구성되어 있어, 코드보다 테스트를 따라가며 학습하기 좋습니다.
- H2, PostgreSQL, MySQL 기반 시나리오를 함께 검증합니다.
- Spring/WebFlux와 Ktor 모듈은 REST API, 캐시, 멀티테넌시, 라우팅 예제를 포함합니다.

## 요구사항

- JDK 25 이상
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
./gradlew :05-exposed-r2dbc-repository-coroutines:test
./gradlew :07-multitenant-ktor:test
./gradlew :06-routing-datasource-ktor-r2dbc:test

# Spring 예제 실행
./gradlew :07-spring-suspended-cache:bootRun
```

## 테스트 가이드

- 기본 회귀는 `./gradlew test`입니다.
- Docker 자원이 부족하거나 DB 기동 시간을 줄이고 싶다면 `-PuseFastDB=true`를 먼저 사용하세요.
- 특정 dialect만 확인하고 싶다면 `-PuseDB=H2,POSTGRESQL`처럼 지정할 수 있습니다.
- DB/Testcontainers 기반 모듈은 리소스를 많이 사용하므로, 개발 중에는 모듈 단위로 먼저 검증하는 편이 효율적입니다.

## 추천 학습 경로

![exposed r2dbc workshop Architecture diagram](docs/images/readme-diagrams/exposed-r2dbc-workshop-architecture-01.png)

1. Spring 진입: [01-spring-boot/spring-webflux-exposed](01-spring-boot/spring-webflux-exposed/README.md)
2. SQL DSL 기초: [03-exposed-r2dbc-basic/exposed-r2dbc-sql-example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)
3. DDL/DML 패턴: [04-exposed-r2dbc-ddl](04-exposed-r2dbc-ddl/01-connection/README.md), [05-exposed-r2dbc-dml](05-exposed-r2dbc-dml/01-dml/README.md)
4. 확장 기능: [06-advanced](06-advanced/README.md)
5. JPA 변환: [07-jpa-convert/01-convert-jpa-basic](07-jpa-convert/01-convert-jpa-basic/README.md)
6. Coroutines / Virtual Threads: [08-r2dbc-coroutines](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md)
7. Spring Repository / Cache: [09-spring](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)
8. 멀티테넌시 / 고성능: [10-multi-tenant](10-multi-tenant/README.ko.md), [11-high-performance](11-high-performance/README.ko.md)
9. Production integration: [12-production-integration](12-production-integration/README.ko.md)
10. Ecosystem integrations: [13-ecosystem-integrations](13-ecosystem-integrations/README.ko.md)

## 모듈 맵

| 그룹                       | 설명                                            | 대표 문서                                                                         |
|--------------------------|-----------------------------------------------|-------------------------------------------------------------------------------|
| `00-shared`              | 공통 테스트 인프라, 스키마, 샘플 repository                | [Shared](00-shared/exposed-r2dbc-shared/README.md)                            |
| `01-spring-boot`         | Spring WebFlux + Exposed R2DBC 기본 통합          | [Spring WebFlux](01-spring-boot/spring-webflux-exposed/README.md)             |
| `02-alternatives-to-jpa` | JPA 대안 패턴 비교 (JDBC Template, JOOQ 등)          | 현재 checkout에는 없음                                                         |
| `03-exposed-r2dbc-basic` | SQL DSL, 조인, 조건절 등 기본기                        | [SQL Example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)     |
| `04-exposed-r2dbc-ddl`   | 연결 관리, DDL, 스키마 제어                            | [Connection](04-exposed-r2dbc-ddl/01-connection/README.md)                    |
| `05-exposed-r2dbc-dml`   | CRUD, 함수, 타입, 트랜잭션                            | [DML](05-exposed-r2dbc-dml/01-dml/README.md)                                  |
| `06-advanced`            | 암호화, JSON, Money, Custom Column, Jackson/Tink | [Advanced](06-advanced/README.md)                                             |
| `07-jpa-convert`         | JPA 패턴을 Exposed R2DBC로 전환                     | [JPA Convert](07-jpa-convert/01-convert-jpa-basic/README.md)                  |
| `08-r2dbc-coroutines`    | Coroutines, Flow, Virtual Threads             | [Coroutines](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md) |
| `09-spring`              | Repository 패턴, Redis 기반 suspended cache       | [Spring Examples](09-spring/05-exposed-r2dbc-repository-coroutines/README.md) |
| `10-multi-tenant`        | Schema, connection-factory, authorization, onboarding 멀티테넌시 + WebFlux/Ktor | [Multi-Tenant Strategies](10-multi-tenant/README.ko.md) |
| `11-high-performance`    | 캐시 전략, routing datasource, read/write 분리 + Ktor 비교 | [High Performance](11-high-performance/README.ko.md)                          |
| `12-production-integration` | Spring Boot 4/Ktor production service patterns, realtime replay, HTTP client outbox/idempotency, request correlation/readiness diagnostics | [Production Integration](12-production-integration/README.ko.md)              |
| `13-ecosystem-integrations` | PostgreSQL 호환 R2DBC 경계를 사용하는 CockroachDB retry handling | [Ecosystem Integrations](13-ecosystem-integrations/README.ko.md)              |

## 주목할 예제

- [09-spring/05-exposed-r2dbc-repository-coroutines](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)
  Spring WebFlux + Coroutines + Exposed repository 패턴
- [09-spring/07-spring-suspended-cache](09-spring/07-spring-suspended-cache/README.md)
  Lettuce coroutine cache와 Exposed repository 조합
- [10-multi-tenant](10-multi-tenant/README.ko.md)
  schema, connection-factory, authorization, onboarding 전략 비교
- [10-multi-tenant/03-multitenant-spring-webflux](10-multi-tenant/03-multitenant-spring-webflux/README.md)
  Reactor Context + Coroutine Context 기반 tenant 전파
- [10-multi-tenant/06-tenant-onboarding-spring-webflux](10-multi-tenant/06-tenant-onboarding-spring-webflux/README.ko.md)
  런타임 tenant metadata 예약, R2DBC pool provisioning, 실패 cleanup
- [10-multi-tenant/07-multitenant-ktor](10-multi-tenant/07-multitenant-ktor/README.ko.md)
  Ktor call attributes 기반 schema-per-tenant 요청 흐름
- [10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux](10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md)
  토큰·유효 시간 기반 테넌트 온보딩 수명주기와 재시작 복구
- [11-high-performance/03-routing-datasource](11-high-performance/03-routing-datasource/README.ko.md)
  Reactor Context 기반 tenant/read-write routing datasource
- [11-high-performance/04-cache-strategies-ktor-r2dbc](11-high-performance/04-cache-strategies-ktor-r2dbc/README.ko.md),
  [11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines](11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines/README.ko.md),
  [11-high-performance/06-routing-datasource-ktor-r2dbc](11-high-performance/06-routing-datasource-ktor-r2dbc/README.ko.md)
  Ktor R2DBC cache, coroutine single-flight cache, routing datasource 비교
- [12-production-integration](12-production-integration/README.ko.md)
  Spring Boot 4/Ktor production service boundary 비교
- [12-production-integration/01-spring-production-integration](12-production-integration/01-spring-production-integration/README.ko.md),
  [12-production-integration/02-ktor-production-integration](12-production-integration/02-ktor-production-integration/README.ko.md)
  HTTP client outbox/idempotency와 request-correlation/readiness diagnostics
- [13-ecosystem-integrations](13-ecosystem-integrations/README.ko.md)
  PostgreSQL 호환 R2DBC를 통한 CockroachDB retry handling. BigQuery, Trino, StarRocks, DuckDB는 R2DBC 범위 밖으로 문서화

## 아키텍처 개요

![Exposed R2DBC Workshop runtime architecture](docs/images/readme-diagrams/root-readme-runtime-architecture-02.png)

워크숍 README는 예제를 PNG 다이어그램으로 먼저 설명하는 방향을 따릅니다. 루트 아키텍처 다이어그램은 다음 흐름을 한 화면에서 보여줍니다.

- Spring WebFlux와 Ktor adapter가 tenant, auth, correlation ID 같은 request context를 수집합니다.
- Coroutine service 계층은 repository, cache, multi-tenant, production integration 패턴을 적용합니다.
- 모든 DB 접근은 `suspendTransaction`, SQL DSL/Flow collection, R2DBC `ConnectionFactory` routing을 통과합니다.
- 인프라 예제는 primary/replica DB, Redis suspended cache, outbox/idempotency storage를 다룹니다.

## exposed-workshop 예제 parity

![Example parity map with exposed-workshop](docs/images/readme-diagrams/issue-89-example-parity-map-01.png)

Issue [#89](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/89)는
[`exposed-workshop`](https://github.com/bluetape4k/exposed-workshop)과의 개념 수준 parity를 추적합니다.
목표는 모듈 이름을 1:1로 맞추는 것이 아니라, 같은 학습 주제가 양쪽 워크숍에서 설명되는지 확인하는 것입니다.
R2DBC 전용 구조와 JDBC 전용 구조는 API 모델이 다르면 별도 예제로 유지합니다.

| `exposed-workshop` 주제 | R2DBC counterpart | 결정 |
|------------------------|-------------------|------|
| Ktor examples epic `#45`, multi-tenant `#46` | Closed R2DBC issues `#32`, `#33`; module `10-multi-tenant/07-multitenant-ktor` | counterpart 있음 |
| Ktor cache/routing issues `#47`, `#48`, `#49`, `#50` | Closed R2DBC issues `#34`, `#35`, `#36`, `#69`; modules `11-high-performance/04-06-*` | counterpart 있음 |
| Spring Boot tenant strategy issues `#51`, `#55`, `#56` | Closed R2DBC issues `#37`-`#42`; modules `10-multi-tenant/03-06-*` | counterpart 있음 |
| Chapter 12 production integration epic `#57` | Closed R2DBC issues `#43`-`#49`; modules `12-production-integration/01-*`, `02-*` | counterpart 있음 |
| Chapter 13 database adapters | R2DBC issue `#115`; module `13-ecosystem-integrations/03-cockroachdb-retry` | CockroachDB는 PostgreSQL 호환 R2DBC로 cover; BigQuery/Trino/StarRocks/DuckDB는 JDBC/HTTP/native-client 중심이라 제외 |
| R2DBC connection-factory-per-tenant | Closed R2DBC issue `#39`; exact JDBC equivalent 없음 | 플랫폼 전용, 중복 issue 없음 |
| JDBC DAO/entities, transaction template, benchmark | `exposed-workshop`의 blocking/JDBC 전용 모듈 | 플랫폼 전용, 중복 issue 없음 |

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

![Repository learning structure](docs/images/readme-diagrams/root-readme-repository-structure-03.png)

## 개발 팁

- 새로운 예제를 추가할 때는 공개 API에 한국어 KDoc을 작성하세요.
- DB 관련 테스트는 공유 상태를 만들지 않도록 테이블 생성/정리 범위를 좁게 유지하세요.
- 회귀 실패 시에는 전체 빌드보다 먼저 해당 모듈의 `:module:test`를 재현하는 편이 빠릅니다.
- `-PuseFastDB=true` 옵션으로 H2 only 모드를 활성화하면 Docker 없이 빠르게 개발할 수 있습니다.

## Claude Code 지원

이 프로젝트는 [Claude Code](https://claude.ai/code) + [oh-my-claudecode](https://github.com/Yeachan-Heo/oh-my-claudecode) 사용자를 위한 전용 스킬을 포함합니다.

### 프로젝트 전용 스킬

`.omc/skills/exposed-r2dbc/` 에 위치하며, 이 저장소를 clone하면 자동으로 적용됩니다.

- `withDb` / `withTables` / `suspendTransaction` 사용 패턴
- `Table` 정의 및 컬럼 타입 참조
- `TestDB` enum 및 다중 DB 파라미터화 테스트 구조
- DML (INSERT / SELECT+Flow / UPDATE / DELETE) 패턴
- MUST DO / MUST NOT DO 안티패턴

### 빠른 시작

```bash
# oh-my-claudecode 설치
claude /oh-my-claudecode:omc-setup

# 스킬 확인
claude /oh-my-claudecode:skill list
```
