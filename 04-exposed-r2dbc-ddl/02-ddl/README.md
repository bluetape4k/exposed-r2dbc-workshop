# 02 Schema Definition Language (DDL)

Exposed R2DBC로 데이터베이스 스키마를 정의하고 관리하는 방법을 학습합니다. 테이블 생성, 수정, 삭제 등의 DDL 작업을 비동기로 수행합니다.

## 학습 목표

- Exposed DSL로 테이블 정의하는 방법 이해
- 다양한 컬럼 타입과 제약조건 정의
- 인덱스와 시퀀스 생성
- 스키마 마이그레이션 전략
- R2DBC 환경에서의 DDL 실행 패턴

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 핵심 개념

### 테이블 정의

Exposed는 타입 안전한 방식으로 테이블을 정의할 수 있는 DSL을 제공합니다.

```kotlin
object Users: IntIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val isActive = bool("is_active").default(true)
}
```

### 컬럼 타입

| 타입          | Kotlin 타입    | SQL 타입         |
|-------------|--------------|----------------|
| `integer`   | `Int`        | `INTEGER`      |
| `long`      | `Long`       | `BIGINT`       |
| `varchar`   | `String`     | `VARCHAR(n)`   |
| `text`      | `String`     | `TEXT`         |
| `bool`      | `Boolean`    | `BOOLEAN`      |
| `timestamp` | `Instant`    | `TIMESTAMP`    |
| `date`      | `LocalDate`  | `DATE`         |
| `decimal`   | `BigDecimal` | `DECIMAL(p,s)` |

### 제약조건

```kotlin
object Orders: IntIdTable("orders") {
    val userId = reference("user_id", Users)
    val amount = decimal("amount", 10, 2).check { it greater 0.toBigDecimal() }
    val status = enumerationByName("status", 20, OrderStatus::class)

    override val primaryKey = PrimaryKey(id)
}
```

### 인덱스 정의

```kotlin
object Products: IntIdTable("products") {
    val name = varchar("name", 255)
    val categoryId = reference("category_id", Categories)
    val price = decimal("price", 10, 2)

    init {
        // 단일 컬럼 인덱스
        index(isUnique = false, name)
        // 복합 인덱스
        index(isUnique = false, categoryId, price)
    }
}
```

### 스키마 생성

```kotlin
suspendTransaction {
    SchemaUtils.create(Users, Orders)
    // 또는 존재하지 않을 때만 생성
    SchemaUtils.createMissing(Users, Orders)
}
```

### 스키마 삭제

```kotlin
suspendTransaction {
    SchemaUtils.drop(Users, Orders)
}
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :04-exposed-r2dbc-ddl:02-ddl:test

# 특정 테스트만 실행
./gradlew :04-exposed-r2dbc-ddl:02-ddl:test --tests "exposed.r2dbc.examples.ddl.*"
```

## 참고 자료

- [Exposed DDL 가이드](https://github.com/JetBrains/Exposed/wiki/DSL)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
