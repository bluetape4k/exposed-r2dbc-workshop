# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Exposed R2DBC 학습 워크샵 — JetBrains Exposed 프레임워크를 R2DBC(Reactive Relational Database Connectivity) 환경에서 사용하는 예제 컬렉션입니다. 모든 코드는 Kotlin으로 작성하며 Java는 사용하지 않습니다.

## Build & Test Commands

```bash
# 전체 빌드
./gradlew build

# 전체 테스트
./gradlew test

# 특정 모듈 테스트 (프로젝트 이름은 디렉토리 이름 기준)
./gradlew :01-dml:test
./gradlew :exposed-r2dbc-shared:test
./gradlew :spring-webflux-exposed:test

# 특정 테스트 클래스만 실행
./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"

# 빌드 캐시 제거
./gradlew clean
```

모듈 이름은 `settings.gradle.kts`의 `includeModules()` 함수로 결정됩니다. 각 하위 디렉토리 이름이 곧 Gradle 프로젝트 이름(`:dir-name`)이 됩니다.

## Module Structure

```
00-shared/exposed-r2dbc-shared/   # 공통 테스트 유틸리티 (모든 모듈에서 참조)
01-spring-boot/spring-webflux-exposed/   # Spring WebFlux + Coroutines + Exposed R2DBC
03-exposed-r2dbc-basic/           # SQL DSL 기본
04-exposed-r2dbc-ddl/             # 연결 관리, 스키마 DDL
05-exposed-r2dbc-dml/             # SELECT/INSERT/UPDATE/DELETE, 타입, 함수, 트랜잭션
06-advanced/                      # 암호화, 날짜/시간, JSON, 커스텀 컬럼/엔티티
07-jpa-convert/                   # JPA → Exposed R2DBC 마이그레이션
08-r2dbc-coroutines/              # Coroutines, Virtual Threads
09-spring/                        # Repository 패턴, Suspended Cache
10-multi-tenant/                  # Schema-based Multi-tenancy
11-high-performance/              # 캐시 전략, Routing DataSource
```

## Architecture & Key Patterns

### 의존성 관리

모든 라이브러리 버전은 `buildSrc/src/main/kotlin/Libs.kt`에 중앙 집중 관리됩니다. 새 의존성 추가 시 이 파일에 먼저 정의하고 `build.gradle.kts`에서 참조합니다.

### 공통 테스트 인프라 (`exposed-r2dbc-shared`)

모든 테스트 모듈이 `:exposed-r2dbc-shared`를 `testImplementation`으로 참조합니다.

**핵심 컴포넌트:**

- `AbstractR2dbcExposedTest` — 모든 테스트 클래스의 기반. UTC 타임존 고정, `enableDialects()` 제공
- `TestDB` — 지원 DB enum (H2, H2_MYSQL, H2_PSQL, H2_MARIADB, MARIADB, MYSQL_V8, POSTGRESQL)
- `Containers` — Testcontainers 기반 DB 컨테이너 싱글턴 (MariaDB, MySQL8, PostgreSQL)
- `withDb(testDB) { }` — 트랜잭션 컨텍스트를 열고 코드 실행, DB별 세마포어로 직렬화
- `withTables(testDB, *tables) { }` — 테이블 생성 후 코드 실행, 완료 후 자동 정리

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

- `@ParameterizedTest @MethodSource("enableDialects")` 로 활성화된 DB 전체 반복
- 기본 활성 DB: H2, PostgreSQL, MySQL V8, MariaDB (`USE_FAST_DB=false`)
- `USE_FAST_DB=true`로 변경하면 H2 in-memory만 사용 (빠른 개발 반복용)

### Exposed R2DBC API 패턴

```kotlin
// 테이블 정의
object Cities: IntIdTable("cities") {
    val name = varchar("name", 50)
}

// 트랜잭션 내부 (suspendTransaction 또는 withTables 블록 내)
Cities.insert { it[name] = "Seoul" }
Cities.selectAll().toList()
Cities.select { Cities.name eq "Seoul" }.single()
```

- Exposed v1 패키지: `org.jetbrains.exposed.v1.core`, `org.jetbrains.exposed.v1.r2dbc`
- 모든 DB 접근은 `suspendTransaction` 또는 `withDb`/`withTables` 헬퍼 안에서 수행
- Flow API: `selectAll()` 등은 `Flow`를 반환하므로 `.toList()`, `.single()` 등으로 수집

### 로깅

```kotlin
companion object: KLogging()        // 일반 클래스
companion object: KLoggingChannel() // 코루틴 환경 (채널 기반)
```

`bluetape4k-logging` 모듈 기반. `log.debug { "message" }` 형태로 사용.

## Key Libraries

| 라이브러리                                                                 | 용도                                                                |
|-----------------------------------------------------------------------|-------------------------------------------------------------------|
| `exposed-core`, `exposed-r2dbc`                                       | Exposed R2DBC 핵심                                                  |
| `exposed-java-time`, `exposed-json`, `exposed-crypt`, `exposed-money` | Exposed 확장                                                        |
| `bluetape4k-*`                                                        | 내부 유틸리티 (coroutines, jdbc, r2dbc, testcontainers, idgenerators 등) |
| `kotlinx-coroutines`                                                  | 비동기 처리                                                            |
| `spring-boot 3.5.x`                                                   | Spring WebFlux 모듈                                                 |
| `kluent`                                                              | assertion 라이브러리 (`shouldBeEqualTo`, `shouldHaveSize` 등)           |
| `datafaker`, `random-beans`                                           | 테스트 데이터 생성                                                        |

## Development Conventions

- **언어**: Kotlin 2.3.x, JDK 21 필수 (Virtual Threads, ZGC 활용)
- **Kotlin 옵션**: `-Xcontext-parameters`, `-Xinline-classes`, coroutines 실험적 API 전부 opt-in 처리됨
- **KDoc 주석**: 코드에 KDoc 형식 주석 포함
- **커밋 메시지**: 한국어, conventional commit 형식 (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- **테스트 동시성**: 멀티 모듈 테스트는 `BuildService` 뮤텍스로 순차 실행 보장 (DB 충돌 방지)
- **타임존**: 모든 테스트는 UTC 고정

## Environment Requirements

- JDK 21+
- Gradle 8.x+ (gradlew 래퍼 사용 권장)
- Docker (Testcontainers 사용 — PostgreSQL, MySQL 8, MariaDB 컨테이너 자동 기동)
