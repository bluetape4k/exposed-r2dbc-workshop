# 01 Connection Management (커넥션 관리)

Exposed R2DBC에서 데이터베이스 연결을 구성하고, 연결 메타데이터를 조회하고, 커넥션 풀을 활용하는 방법을 학습합니다.

---

## 학습 목표

- `R2dbcDatabase.connect(url)`로 R2DBC 데이터베이스 연결 구성
- `connection().metadata { }` API로 컬럼 메타데이터·테이블 제약조건 조회
- `r2dbc:pool:` URL 스킴으로 내장 커넥션 풀 활성화
- 풀 크기를 초과하는 동시 `suspendTransaction` 실행 시 커넥션 재활용 동작 검증

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
src/test/kotlin/exposed/r2dbc/examples/connection/
├── Ex01_Connection.kt            # 컬럼 메타데이터 및 테이블 제약조건 조회
└── h2/
    ├── Ex01_H2_ConnectionPool.kt # H2 R2DBC 커넥션 풀 동작 검증
    └── Ex02_H2_MultiDatabase.kt  # H2 다중 데이터베이스 연결
```

---

## 예제 상세

### `Ex01_Connection` — 메타데이터 조회

R2DBC 연결에서 `connection().metadata { }` API를 통해 테이블 컬럼과 제약조건 정보를 읽어옵니다.

#### 테이블 정의

```kotlin
object People: LongIdTable() {
    val firstName: Column<String?> = varchar("firstname", 80).nullable()
    val lastName: Column<String>   = varchar("lastname", 42).default("Doe")
    val age: Column<Int>           = integer("age").default(18)
}
```

#### 컬럼 메타데이터 조회

```kotlin
withTables(TestDB.H2, People) {
    val columnMetadata = connection().metadata {
        columns(People)[People]!!
    }.toSet()

    // 예상 ColumnMetadata: id(BIGINT), firstName(VARCHAR 80, nullable),
    //                      lastName(VARCHAR 42, default="Doe"), age(INT, default=18)
    columnMetadata shouldContainSame expected
}
```

#### 테이블 제약조건 조회

```kotlin
withTables(testDB, parent, child) {
    val constraints = connection().metadata {
        tableConstraints(listOf(child))
    }
    // parent(unique), child(FK → parent.scale) 2개 키 반환
    constraints.keys shouldHaveSize 2
}
```

---

### `Ex01_H2_ConnectionPool` — H2 커넥션 풀

`r2dbc:pool:h2:mem:///` URL 스킴을 사용하면 R2DBC 내장 커넥션 풀이 자동으로 활성화됩니다.

```kotlin
private val h2PoolDB1 by lazy {
    R2dbcDatabase.connect("r2dbc:pool:h2:mem:///poolDB1?maxSize=10")
}
```

#### 풀 크기 초과 시 커넥션 재활용 검증

```kotlin
@Test
fun `suspend transactions exceeding pool size`() = runSuspendIO {
    val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)

    val jobs = List(exceedsPoolSize) { index ->
        launch {
            suspendTransaction {
                delay(100)
                TestTable.insertAndGetId { it[testValue] = "test$index" }
            }
        }
    }
    jobs.joinAll()

    // 풀 크기(10)를 초과해도 커넥션이 재활용되어 모든 insert가 완료됨
    suspendTransaction(db = h2PoolDB1) {
        TestTable.selectAll().count() shouldBeEqualTo exceedsPoolSize.toLong()
    }
}
```

---

## 지원 DB 및 R2DBC URL 형식

| 데이터베이스     | R2DBC URL 형식                             | 풀 지원               |
|------------|------------------------------------------|--------------------|
| H2 인메모리    | `r2dbc:h2:mem:///dbname`                 | `r2dbc:pool:h2:...` |
| PostgreSQL | `r2dbc:postgresql://host/dbname`         | `r2dbc:pool:postgresql:...` |
| MySQL      | `r2dbc:mysql://host/dbname`              | `r2dbc:pool:mysql:...` |
| MariaDB    | `r2dbc:mariadb://host/dbname`            | `r2dbc:pool:mariadb:...` |

---

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :01-connection:test

# H2만 빠르게 테스트
./gradlew :01-connection:test -PuseFastDB=true

# 특정 테스트만 실행
./gradlew :01-connection:test --tests "exposed.r2dbc.examples.connection.Ex01_Connection"
./gradlew :01-connection:test --tests "exposed.r2dbc.examples.connection.h2.Ex01_H2_ConnectionPool"
```

---

## H2 인메모리 vs 파일 DB 연결 차이

| 구분 | 인메모리 (mem) | 파일 (file) |
|------|--------------|------------|
| URL 형식 | `r2dbc:h2:mem:///dbname` | `r2dbc:h2:file:///path/to/db` |
| 데이터 유지 | JVM 종료 시 사라짐 | 파일로 영구 저장 |
| `DB_CLOSE_DELAY` | `-1` (연결 유지) | 불필요 |
| 테스트 격리 | 높음 (각 이름별 독립) | 낮음 (파일 공유 가능) |
| 권장 용도 | 단위 테스트, 예제 | 임시 통합 테스트 |

```kotlin
// 인메모리 (테스트 권장)
R2dbcDatabase.connect("r2dbc:h2:mem:///mydb;DB_CLOSE_DELAY=-1;")

// 커넥션 풀 + 인메모리
R2dbcDatabase.connect("r2dbc:pool:h2:mem:///mydb?maxSize=10")

// 파일 DB (영구 저장)
R2dbcDatabase.connect("r2dbc:h2:file:///tmp/mydb")
```

---

## 참고 자료

- [R2DBC 스펙](https://r2dbc.io/)
- [Exposed R2DBC 가이드](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
