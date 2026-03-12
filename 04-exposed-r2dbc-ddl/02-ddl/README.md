# 02 Schema Definition Language (DDL)

Exposed R2DBC로 데이터베이스 스키마를 정의하고 관리하는 방법을 학습합니다. 테이블 생성, 수정, 삭제 등의 DDL 작업을 비동기로 수행합니다.

---

## 학습 목표

- Exposed DSL로 테이블 정의하는 방법 이해
- 다양한 컬럼 타입과 제약조건 정의
- 인덱스 전략과 시퀀스 생성
- 스키마 마이그레이션 전략 (`createMissing`, `addMissingColumnsStatements`)
- R2DBC 환경에서의 DDL 실행 패턴

---

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

---

## 프로젝트 구조

```
src/test/kotlin/exposed/r2dbc/examples/ddl/
├── Ex01_CreateDatabase.kt             # 데이터베이스 연결 및 생성
├── Ex02_CreateTable.kt                # 테이블 생성 (SchemaUtils.create)
├── Ex03_CreateMissingTableAndColumns.kt # 누락된 테이블·컬럼 자동 추가
├── Ex04_ColumnDefinition.kt           # 다양한 컬럼 타입 정의
├── Ex05_CreateIndex.kt                # 인덱스 생성 전략
├── Ex06_Sequence.kt                   # 시퀀스 생성 및 활용
├── Ex07_CustomEnumeration.kt          # 커스텀 열거형 컬럼
├── Ex09_JavaUUIDColumnType.kt         # Java UUID 컬럼 타입
├── Ex10_KotlinUUIDColumnType.kt       # Kotlin UUID 컬럼 타입
└── Ex10_DDL_Examples.kt               # DDL 종합 예제 모음
```

---

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

---

### 컬럼 타입 매핑 (Kotlin DSL → SQL)

#### 숫자 타입

| Exposed DSL          | Kotlin 타입    | H2 / PostgreSQL    | MySQL / MariaDB     |
|----------------------|--------------|--------------------|--------------------|
| `byte("col")`        | `Byte`       | `TINYINT`          | `TINYINT`          |
| `short("col")`       | `Short`      | `SMALLINT`         | `SMALLINT`         |
| `integer("col")`     | `Int`        | `INT`              | `INT`              |
| `long("col")`        | `Long`       | `BIGINT`           | `BIGINT`           |
| `float("col")`       | `Float`      | `FLOAT`            | `FLOAT`            |
| `double("col")`      | `Double`     | `DOUBLE PRECISION` | `DOUBLE`           |
| `decimal("col",p,s)` | `BigDecimal` | `DECIMAL(p,s)`     | `DECIMAL(p,s)`     |
| `ubyte("col")`       | `UByte`      | `TINYINT UNSIGNED` | `TINYINT UNSIGNED` |
| `ushort("col")`      | `UShort`     | `SMALLINT UNSIGNED`| `SMALLINT UNSIGNED`|
| `uinteger("col")`    | `UInt`       | `INT UNSIGNED`     | `INT UNSIGNED`     |
| `ulong("col")`       | `ULong`      | `BIGINT UNSIGNED`  | `BIGINT UNSIGNED`  |

#### 문자열 타입

| Exposed DSL           | Kotlin 타입     | H2 / PostgreSQL | MySQL / MariaDB |
|-----------------------|---------------|-----------------|-----------------|
| `varchar("col", n)`   | `String`      | `VARCHAR(n)`    | `VARCHAR(n)`    |
| `char("col", n)`      | `String`      | `CHAR(n)`       | `CHAR(n)`       |
| `text("col")`         | `String`      | `TEXT`          | `TEXT`          |
| `mediumText("col")`   | `String`      | `TEXT`          | `MEDIUMTEXT`    |
| `largeText("col")`    | `String`      | `TEXT`          | `LONGTEXT`      |
| `binary("col", n)`    | `ByteArray`   | `BINARY(n)`     | `BINARY(n)`     |
| `blob("col")`         | `ExposedBlob` | `BLOB`          | `BLOB`          |

#### 날짜/시간 타입

> `exposed-java-time` 또는 `exposed-kotlin-datetime` 의존성 필요

| Exposed DSL              | Kotlin 타입       | H2 / PostgreSQL            | MySQL / MariaDB  |
|--------------------------|-----------------|----------------------------|-----------------|
| `date("col")`            | `LocalDate`     | `DATE`                     | `DATE`          |
| `time("col")`            | `LocalTime`     | `TIME`                     | `TIME`          |
| `datetime("col")`        | `LocalDateTime` | `TIMESTAMP`                | `DATETIME`      |
| `timestamp("col")`       | `Instant`       | `TIMESTAMP WITH TIME ZONE` | `TIMESTAMP`     |
| `timestampWithTimeZone`  | `OffsetDateTime`| `TIMESTAMP WITH TIME ZONE` | `DATETIME`      |
| `duration("col")`        | `Duration`      | `BIGINT` (나노초)            | `BIGINT` (나노초) |

#### 기타 타입

| Exposed DSL                         | Kotlin 타입  | 설명                                          |
|-------------------------------------|------------|-----------------------------------------------|
| `bool("col")`                       | `Boolean`  | `BOOLEAN` / `TINYINT(1)` (DB별 상이)           |
| `uuid("col")`                       | `UUID`     | `UUID` / `CHAR(36)` (DB별 상이)                |
| `enumerationByName("col", len, E)`  | `Enum<E>`  | `VARCHAR(len)` — 이름 문자열로 저장               |
| `enumeration("col", E)`             | `Enum<E>`  | `INTEGER` — ordinal로 저장                     |
| `customEnumeration(...)`            | `Enum<E>`  | DB 네이티브 ENUM 타입 (MySQL/MariaDB)            |
| `array("col", E)`                   | `List<E>`  | `ARRAY` (PostgreSQL 전용)                      |
| `json("col")`                       | `String`   | `JSON` (exposed-json 필요)                     |
| `jsonb("col")`                      | `String`   | `JSONB` (PostgreSQL + exposed-json 필요)        |

---

### 제약조건

```kotlin
object Orders: IntIdTable("orders") {
    val userId = reference("user_id", Users)
    val amount = decimal("amount", 10, 2).check { it greater 0.toBigDecimal() }
    val status = enumerationByName("status", 20, OrderStatus::class)

    override val primaryKey = PrimaryKey(id)
}
```

Foreign Key 삭제/업데이트 옵션:

```kotlin
val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
```

---

### 인덱스 전략

Exposed에서 인덱스를 정의하는 방법은 크게 두 가지입니다.

#### 1. 컬럼 레벨 인덱스 (단일 컬럼)

```kotlin
object Products: IntIdTable("products") {
    val name = varchar("name", 255).index()         // 일반 인덱스
    val sku  = varchar("sku", 50).uniqueIndex()     // 유니크 인덱스
    val email = varchar("email", 100)
        .uniqueIndex("uidx_products_email")         // 이름 지정 유니크 인덱스
}
```

#### 2. 테이블 레벨 인덱스 (복합 인덱스)

```kotlin
object OrderItems: IntIdTable("order_items") {
    val orderId   = reference("order_id", Orders)
    val productId = reference("product_id", Products)
    val quantity  = integer("quantity")

    init {
        // 복합 유니크 인덱스 (주문당 상품 중복 방지)
        uniqueIndex("uidx_order_product", orderId, productId)
        // 복합 일반 인덱스 (조회 최적화)
        index("idx_order_items_product", false, productId, quantity)
    }
}
```

#### 인덱스 생성 시 고려 사항

| 시나리오                     | 권장 방법                                  |
|--------------------------|----------------------------------------|
| 단일 컬럼 유니크 제약             | `.uniqueIndex()` — FK 참조 시 필수          |
| WHERE 절 자주 사용되는 컬럼        | `.index()` 또는 `index(false, col)`       |
| 다중 컬럼 복합 조회              | `init { index(false, col1, col2) }`    |
| 복합 유니크 (비즈니스 키)          | `init { uniqueIndex("name", col1, col2) }` |
| 조회 없는 단순 저장용 컬럼          | 인덱스 불필요 (쓰기 오버헤드 방지)              |

---

### 시퀀스 사용

시퀀스(Sequence)는 데이터베이스에서 고유한 숫자를 순차적으로 생성하는 객체입니다. PostgreSQL, H2에서 지원하며 MySQL/MariaDB에서는 `AUTO_INCREMENT`로 대체합니다.

#### 시퀀스 정의

```kotlin
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.nextIntVal

// 기본 시퀀스 정의 (1부터 1씩 증가)
val mySequence = Sequence("my_seq")

// 상세 옵션 지정
val customSequence = Sequence(
    name       = "order_seq",
    startWith  = 1000L,    // 시작값
    incrementBy = 10L,     // 증가량
    minValue   = 1000L,    // 최솟값
    maxValue   = 999999L,  // 최댓값
    cycle      = false,    // 최댓값 도달 시 순환 여부
    cache      = 50L       // 캐시 크기 (성능 최적화)
)
```

#### 시퀀스를 컬럼 기본값으로 사용

```kotlin
object Invoices: Table("invoices") {
    val id     = integer("id").autoIncrement(mySequence)
    val number = varchar("number", 20)

    override val primaryKey = PrimaryKey(id)
}
```

#### 시퀀스 직접 사용 (nextVal)

```kotlin
// 시퀀스 생성
suspendTransaction {
    SchemaUtils.createSequence(mySequence)
}

// 다음 값 조회
suspendTransaction {
    val nextId: Int = mySequence.nextIntVal().value()   // SELECT nextval('my_seq')
}

// 시퀀스 삭제
suspendTransaction {
    SchemaUtils.dropSequence(mySequence)
}
```

#### 지원 데이터베이스

| 데이터베이스     | 시퀀스 지원      | 비고                                           |
|------------|-------------|----------------------------------------------|
| PostgreSQL | 네이티브 지원     | `CREATE SEQUENCE`, `nextval()` 완전 지원         |
| H2         | 네이티브 지원     | `CREATE SEQUENCE` 지원                         |
| MySQL 8    | 미지원         | `AUTO_INCREMENT` 사용. 시퀀스 생성 시 오류 발생       |
| MariaDB    | 10.3+ 지원    | `CREATE SEQUENCE` 가능하나 Exposed 지원 제한적       |

---

### 스키마 생성 및 마이그레이션

#### 전체 생성

```kotlin
suspendTransaction {
    SchemaUtils.create(Users, Orders, Products)
}
```

#### 증분 마이그레이션 (누락된 테이블/컬럼만 추가)

```kotlin
suspendTransaction {
    // 존재하지 않는 테이블만 생성
    SchemaUtils.createMissing(Users, Orders)

    // 누락된 컬럼 추가 SQL 문 조회 (실행 전 검토)
    val statements = SchemaUtils.addMissingColumnsStatements(Users)
    statements.forEach { log.debug { it } }

    // 누락된 컬럼 자동 추가
    SchemaUtils.addMissingColumns(Users)
}
```

#### 스키마 삭제

```kotlin
suspendTransaction {
    SchemaUtils.drop(Users, Orders)
}
```

---

## 예제 상세

| 파일                                   | 주요 내용                                         |
|--------------------------------------|------------------------------------------------|
| `Ex01_CreateDatabase.kt`             | R2DBC 연결, DB 존재 확인                            |
| `Ex02_CreateTable.kt`                | `SchemaUtils.create` / `drop`                  |
| `Ex03_CreateMissingTableAndColumns.kt` | `createMissing`, `addMissingColumnsStatements` |
| `Ex04_ColumnDefinition.kt`           | 컬럼 타입, nullable, default, check 제약           |
| `Ex05_CreateIndex.kt`                | 단일/복합 인덱스, uniqueIndex                        |
| `Ex06_Sequence.kt`                   | Sequence 생성, nextVal, autoIncrement 연동        |
| `Ex07_CustomEnumeration.kt`          | `customEnumeration` (MySQL/MariaDB ENUM)       |
| `Ex09_JavaUUIDColumnType.kt`         | `java.util.UUID` 컬럼 타입                        |
| `Ex10_KotlinUUIDColumnType.kt`       | `kotlin.uuid.Uuid` 컬럼 타입 (Kotlin 2.0+)       |
| `Ex10_DDL_Examples.kt`               | DDL 종합 예제 (외래키, CHECK, 트랜잭션 격리 수준 등)     |

---

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :02-ddl:test

# H2만 빠르게 테스트
./gradlew :02-ddl:test -PuseFastDB=true

# 특정 테스트만 실행
./gradlew :02-ddl:test --tests "exposed.r2dbc.examples.ddl.Ex05_CreateIndex"
./gradlew :02-ddl:test --tests "exposed.r2dbc.examples.ddl.Ex06_Sequence"
```

---

## 참고 자료

- [Exposed DDL 가이드](https://github.com/JetBrains/Exposed/wiki/DSL)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
- [R2DBC 스펙](https://r2dbc.io/)
