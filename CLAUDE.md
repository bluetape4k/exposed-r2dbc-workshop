# CLAUDE.md — exposed-r2dbc-workshop

Kotlin Exposed R2DBC 학습 워크샵. Kotlin 전용 (Java 코드 사용 금지).
Kotlin `2.3.20` / Spring Boot `3.5.11` / Exposed `1.1.1` / Bluetape4k `1.5.0-Beta1` / JDK 21.

## Build & Test

```bash
./gradlew build
./gradlew test
./gradlew :exposed-r2dbc-shared:test
./gradlew :spring-webflux-exposed:test
./gradlew :01-dml:test   # 05-exposed-r2dbc-dml/01-dml
./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"
./gradlew clean
repo-status
repo-diff
repo-test-summary -- ./gradlew :05-exposed-dml:01-dml:test
```

모듈 이름은 leaf 디렉토리 이름 = Gradle 프로젝트 이름(`:dir-name`).

## Module Structure

```
00-shared/exposed-r2dbc-shared/         # 공통 테스트 유틸리티
01-spring-boot/spring-webflux-exposed/  # Spring WebFlux + Coroutines + Exposed R2DBC
02-alternatives-to-jpa/                 # JPA 대안 패턴
03-exposed-r2dbc-basic/                 # SQL DSL 기본
04-exposed-r2dbc-ddl/                   # 연결 관리, 스키마 DDL
05-exposed-r2dbc-dml/                   # SELECT/INSERT/UPDATE/DELETE, 타입, 트랜잭션
06-advanced/                            # 암호화, 날짜/시간, JSON, 커스텀 컬럼
07-jpa-convert/                         # JPA → Exposed R2DBC 마이그레이션
08-r2dbc-coroutines/                    # Coroutines, Virtual Threads
09-spring/                              # Repository 패턴, Suspended Cache
10-multi-tenant/                        # Schema-based Multi-tenancy
11-high-performance/                    # 캐시 전략, Routing DataSource
```

## 테스트 인프라 (`exposed-r2dbc-shared`)

```kotlin
class Ex01_Select: AbstractR2dbcExposedTest() {
    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `select example`(testDB: TestDB) = runTest {
        withTables(testDB, MyTable) {
            // Exposed R2DBC DSL
        }
    }
}
```

- `TestDB`: H2, H2_MYSQL, H2_PSQL, MARIADB, MYSQL_V8, POSTGRESQL
- `USE_FAST_DB=true` → H2 only (기본: H2 + PostgreSQL + MySQL V8 + MariaDB)
- `withDb(testDB) { }` + `withTables(testDB, *tables) { }` 헬퍼로 트랜잭션 관리
- 멀티 모듈 테스트: `BuildService` 뮤텍스로 순차 실행 보장 (DB 충돌 방지)
- 모든 테스트: UTC 타임존 고정

## Exposed R2DBC API 패턴

```kotlin
object Cities: IntIdTable("cities") {
    val name = varchar("name", 50)
}

// 트랜잭션 내부
Cities.insert { it[name] = "Seoul" }
Cities.selectAll().toList()  // Flow → collect
```

- 패키지: `org.jetbrains.exposed.v1.core`, `org.jetbrains.exposed.v1.r2dbc`
- 모든 DB 접근: `suspendTransaction` 또는 `withDb`/`withTables` 안에서
- `selectAll()` → `Flow` 반환 → `.toList()`, `.single()` 등으로 수집

## 로깅

```kotlin
companion object: KLogging()        // 일반 클래스
companion object: KLoggingChannel() // 코루틴 환경
```

## 컴파일러 옵션

`-Xcontext-parameters`, `-Xinline-classes`, coroutines 실험적 API opt-in 처리됨.

## 의존성 관리

`buildSrc/src/main/kotlin/Libs.kt` — 신규 의존성은 여기 먼저 정의.

## 프로젝트 스킬 (`.omc/skills/`)

| 스킬 | 용도 |
|------|------|
| `exposed-r2dbc` | `withDb`/`withTables`/`suspendTransaction` 패턴, Table 정의, DML(Flow API), 다중 DB 파라미터화 테스트 |
