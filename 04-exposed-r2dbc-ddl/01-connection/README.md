# 01 Connection Management (커넥션 관리)

Exposed R2DBC에서 데이터베이스 연결을 구성하고 관리하는 방법을 학습합니다. R2DBC 기반의 비동기 연결 관리의 핵심 개념을 다룹니다.

## 학습 목표

- R2DBC ConnectionFactory 설정 방법 이해
- 다양한 데이터베이스(H2, PostgreSQL, MySQL, MariaDB)에 대한 연결 구성
- ConnectionPool 설정 및 튜닝
- 연결 예외 처리 및 타임아웃 설정
- R2DBC 드라이버별 설정 차이점 파악

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 핵심 개념

### R2DBC ConnectionFactory

R2DBC는 반응형 데이터베이스 연결을 위한 표준 스펙입니다. Exposed R2DBC는 이를 기반으로 비동기 데이터베이스 작업을 수행합니다.

```kotlin
// R2DBC Database 연결
val database = R2dbcDatabase.connect(
    url = "r2dbc:h2:mem:///testdb",
    user = "sa",
    password = ""
)
```

### 지원 데이터베이스

| 데이터베이스     | R2DBC URL 형식                     | 드라이버               |
|------------|----------------------------------|--------------------|
| H2         | `r2dbc:h2:mem:///dbname`         | `r2dbc-h2`         |
| PostgreSQL | `r2dbc:postgresql://host/dbname` | `r2dbc-postgresql` |
| MySQL      | `r2dbc:mysql://host/dbname`      | `r2dbc-mysql`      |
| MariaDB    | `r2dbc:mariadb://host/dbname`    | `r2dbc-mariadb`    |

### ConnectionPool 구성

프로덕션 환경에서는 연결 풀을 사용하여 성능을 최적화합니다.

```kotlin
val connectionFactory = ConnectionFactories.get(
    ConnectionFactoryOptions.builder()
        .option(DRIVER, "pool")
        .option(PROTOCOL, "postgresql")
        .option(HOST, "localhost")
        .option(PORT, 5432)
        .option(DATABASE, "testdb")
        .option(USER, "user")
        .option(PASSWORD, "password")
        .option(POOL, poolOptions)
        .build()
)
```

### Pool 설정 옵션

```kotlin
val poolOptions = ConnectionPoolOptions.builder()
    .initialSize(5)           // 초기 커넥션 수
    .maxSize(20)              // 최대 커넥션 수
    .maxIdleTime(Duration.ofMinutes(30))  // 유휴 타임아웃
    .maxLifeTime(Duration.ofHours(1))     // 최대 생존 시간
    .build()
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :04-exposed-r2dbc-ddl:01-connection:test

# 특정 테스트만 실행
./gradlew :04-exposed-r2dbc-ddl:01-connection:test --tests "exposed.r2dbc.examples.connection.*"
```

## 참고 자료

- [R2DBC 스펙](https://r2dbc.io/)
- [Exposed R2DBC 가이드](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
