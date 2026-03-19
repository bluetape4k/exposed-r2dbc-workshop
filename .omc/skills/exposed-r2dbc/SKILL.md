---
name: exposed-r2dbc
description: JetBrains Exposed R2DBC 워크샵 전용 스킬. withDb/withTables/suspendTransaction 패턴, Table 정의, DML(Flow API), 다중 DB 파라미터화 테스트, Kotlin Coroutines 통합. bluetape4k 프로젝트 기반.
license: MIT
metadata:
  author: extracted from bluetape4k/exposed-r2dbc-workshop
  version: "1.0.0"
  domain: database
  triggers: Exposed, R2DBC, withTables, withDb, suspendTransaction, IntIdTable, selectAll, TestDB, AbstractR2dbcExposedTest, SchemaUtils, R2dbcDatabase, Flow ResultRow
  role: specialist
  scope: implementation
  output-format: code
  related-skills: kotlin-expert, coroutines-kotlin, kotest, bluetape4k-patterns
---

# Exposed R2DBC Specialist

이 프로젝트(bluetape4k/exposed-r2dbc-workshop) 전용 스킬.
JetBrains Exposed를 R2DBC + Kotlin Coroutines 환경에서 사용하는 패턴을 다룹니다.

## 패키지 임포트 경로

```kotlin
// Core DSL
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.IntIdTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Schema

// R2DBC DML
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction

// 트랜잭션
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
```

---

## Table 정의 패턴

```kotlin
// 자동 증가 INT ID 포함
object Cities: IntIdTable("cities") {
    val name = varchar("name", 50).index()
    val countryId = reference("country_id", Countries, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(countryId, name)  // 복합 고유 인덱스
    }
}

// ID 없는 관계 테이블
object UserToCityTable: Table("user_to_city") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val cityId = reference("city_id", Cities, onDelete = ReferenceOption.CASCADE)
}
```

### 주요 컬럼 타입

| Kotlin DSL | SQL 타입 |
|-----------|---------|
| `varchar(name, length)` | VARCHAR |
| `integer(name)` | INT |
| `long(name)` | BIGINT |
| `bool(name)` | BOOLEAN |
| `text(name)` | TEXT |
| `decimal(name, precision, scale)` | DECIMAL |
| `datetime(name)` | DATETIME/TIMESTAMP |
| `reference(name, table)` | FK |

---

## 테스트 인프라

### AbstractR2dbcExposedTest 구조

```kotlin
abstract class AbstractR2dbcExposedTest {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))  // UTC 고정
    }
    companion object: KLogging() {
        @JvmStatic
        fun enableDialects() = TestDB.enabledDialects()
        const val ENABLE_DIALECTS_METHOD = "enableDialects"
        val faker = Fakers.faker
    }
}
```

### TestDB enum

| 값 | 설명 | Docker 필요 |
|----|------|-----------|
| `H2` | H2 기본 모드 | X |
| `H2_MYSQL` | H2 MySQL 호환 | X |
| `H2_PSQL` | H2 PostgreSQL 호환 | X |
| `H2_MARIADB` | H2 MariaDB 호환 | X |
| `H2_ORACLE` | H2 Oracle 호환 | X |
| `H2_SQLSERVER` | H2 SQL Server 호환 | X |
| `POSTGRESQL` | PostgreSQL (기본 활성) | O |
| `MYSQL_V8` | MySQL 8.x (기본 활성) | O |
| `MARIADB` | MariaDB | O |

**활성 DB 그룹:**
```kotlin
TestDB.ALL_H2            // 모든 H2 variant
TestDB.ALL_MYSQL_LIKE    // MySQL + H2_MySQL
TestDB.ALL_POSTGRES_LIKE // PostgreSQL + H2_Psql
```

**환경변수 제어:**
```bash
USE_FAST_DB=true   # H2만 사용 (빠른 개발 반복)
USE_FAST_DB=false  # 기본값: H2 + PostgreSQL + MySQL V8
```

---

## 테스트 패턴

### 기본 구조

```kotlin
class Ex01_Select: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()  // 코루틴 환경 전용

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `select example`(testDB: TestDB) = runTest {
        withTables(testDB, Cities, Users) {
            // INSERT
            val cityId = Cities.insertAndGetId { it[name] = "Seoul" }

            // SELECT → Flow 수집
            val city = Cities.selectAll()
                .where { Cities.id eq cityId }
                .single()

            city[Cities.name] shouldBeEqualTo "Seoul"
        }
    }
}
```

### withDb — 트랜잭션만 열기

```kotlin
withDb(testDB) {
    SchemaUtils.create(MyTable)
    MyTable.insert { it[col] = value }
    // 테이블 정리는 수동으로
    SchemaUtils.drop(MyTable)
}
```

### withTables — 테이블 자동 생성/정리

```kotlin
withTables(testDB, Table1, Table2) {
    // Table1, Table2 자동 생성됨
    // 블록 종료(예외 포함) 후 자동 DROP
}
```

---

## DML 패턴

### INSERT

```kotlin
// 기본 INSERT
Table.insert { it[column] = value }

// ID 반환 INSERT
val id: EntityID<Int> = Table.insertAndGetId { it[name] = "value" }

// Batch INSERT
Table.batchInsert(itemList) { item ->
    this[Table.name] = item.name
    this[Table.age] = item.age
}

// INSERT IGNORE (MySQL/MariaDB/H2 전용)
Table.insertIgnore { it[name] = "value" }
```

### SELECT + Flow 수집

```kotlin
// Flow → List
val rows: List<ResultRow> = Table.selectAll().toList()

// Flow → 단일 행 (없으면 예외)
val row: ResultRow = Table.selectAll().single()

// Flow → 단일 행 (없으면 null)
val row: ResultRow? = Table.selectAll().singleOrNull()

// WHERE 조건
val rows = Table.selectAll()
    .where { Table.name eq "Seoul" }
    .toList()

// AND/OR 조건
Table.selectAll()
    .where { Table.age greaterEq 18 }
    .andWhere { Table.status eq "active" }

Table.selectAll()
    .where { (Table.type eq "A") or (Table.type eq "B") }

// 특정 컬럼만 조회
val names: List<String> = Table.select(Table.name)
    .where { Table.id inList listOf(1, 2, 3) }
    .map { it[Table.name] }
    .toList()

// COUNT
val count: Long = Table.selectAll().count()
```

### UPDATE

```kotlin
Table.update(where = { Table.id eq targetId }) {
    it[Table.name] = "new name"
    it[Table.updatedAt] = LocalDateTime.now()
}
```

### DELETE

```kotlin
Table.deleteWhere { Table.id eq targetId }
Table.deleteWhere { Table.status eq "inactive" }
```

---

## suspendTransaction 직접 사용

```kotlin
// withDb 블록 외부에서 직접 트랜잭션
suspend fun getUserById(id: Int): ResultRow? = suspendTransaction {
    Users.selectAll().where { Users.id eq id }.singleOrNull()
}

// 재시도 포함 트랜잭션
inTopLevelSuspendTransaction(
    transactionIsolation = db.transactionManager.defaultIsolationLevel!!,
    db = db
) {
    maxAttempts = 20
    Table.insert { it[col] = value }
}
```

---

## 로깅 패턴

```kotlin
// 코루틴 환경 (테스트 클래스)
companion object: KLoggingChannel()

// 일반 환경
companion object: KLogging()

// 사용 — 항상 람다 블록으로 (lazy evaluation)
log.debug { "inserted cityId=$cityId" }
log.error(e) { "failed to insert" }
```

---

## MUST DO

- `withTables` / `withDb` 블록 안에서만 DML 실행
- `@ParameterizedTest + @MethodSource(ENABLE_DIALECTS_METHOD)` 로 다중 DB 테스트
- `runTest { }` 코루틴 테스트 빌더 사용
- `selectAll()` 결과는 반드시 `.toList()`, `.single()`, `.count()` 등으로 수집
- 로깅은 람다 블록 `log.debug { }` 형태 사용
- 임포트는 `org.jetbrains.exposed.v1.*` 패키지 사용
- `companion object: KLoggingChannel()` — 코루틴 테스트 환경

## MUST NOT DO

- ❌ `withDb`/`withTables` 없이 `Table.selectAll()` 직접 호출 — 트랜잭션 컨텍스트 없음
- ❌ `runBlocking` 사용 — `runTest` 또는 `suspendTransaction` 사용
- ❌ `Flow.first()` / `Flow.last()` — Exposed Query는 Kotlin Flow API 사용
- ❌ `log.debug("msg" + variable)` — 문자열 결합 로깅 (람다 블록 사용)
- ❌ MySQL/MariaDB에서 `LIMIT` 없이 `OFFSET` 단독 사용
- ❌ `suspendTransaction` 중첩 — 동일 트랜잭션 내 이미 실행 중이면 불필요
- ❌ Oracle 테스트에서 `prepareSchemaForTest()` 누락

---

## 모듈 구조 참조

```
00-shared/exposed-r2dbc-shared/   # AbstractR2dbcExposedTest, TestDB, withDb, withTables
03-exposed-r2dbc-basic/           # SQL DSL 기본
04-exposed-r2dbc-ddl/             # Schema DDL
05-exposed-r2dbc-dml/             # SELECT/INSERT/UPDATE/DELETE
06-advanced/                      # 암호화, JSON, 커스텀 컬럼
08-r2dbc-coroutines/              # suspendTransaction, 고급 코루틴 패턴
09-spring/                        # Repository 패턴
```
