# 03 Exposed R2DBC SQL Example (SQL DSL 기본)

Exposed R2DBC의 SQL DSL(Domain Specific Language)을 사용하여 타입 안전한 쿼리를 작성하는 방법을 학습합니다.
R2DBC 환경에서 비동기로 SELECT, INSERT, UPDATE, DELETE를 수행합니다.

---

## 학습 목표

- Exposed DSL로 테이블을 정의하는 방법 이해
- 타입 안전한 SQL 쿼리 작성 (`selectAll`, `insert`, `update`, `deleteWhere`)
- JOIN 타입별 사용 방법 (INNER JOIN, LEFT JOIN 등)
- `andWhere` / `orWhere`로 복합 조건 조합
- `GROUP BY` + 집계 함수 활용
- R2DBC 환경에서의 비동기 쿼리 실행 패턴 (`Flow`, `single`, `toList`)

---

## 예제 ERD

여기에 사용되는 ERD는 다음과 같습니다. 도시에 살고 있는 사용자를 표현하기 위한 ERD를 정의합니다.
`cities` 와 `users` 는 1:N 관계입니다.

![City and Users ERD](CityUserSchema.png)

---

## 테이블 정의

### CityTable

`CityTable`은 `cities` 테이블을 정의합니다.
`id`는 auto increment 속성을 가지며 primary key로 설정합니다.

```kotlin
object CityTable: Table("cities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", length = 50)

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}
```

생성되는 DDL:

```sql
-- PostgreSQL
CREATE TABLE IF NOT EXISTS cities (
    id   SERIAL,
    "name" VARCHAR(50) NOT NULL,
    CONSTRAINT PK_Cities_ID PRIMARY KEY (id)
)
```

### UserTable

`UserTable`은 `users` 테이블을 정의합니다.
`id`는 varchar 타입 primary key, `cityId`는 `CityTable.id`를 참조하는 nullable 외래키입니다.

```kotlin
object UserTable: Table("users") {
    val id = varchar("id", length = 10)
    val name = varchar("name", length = 50)
    val cityId = optReference("city_id", CityTable.id)   // nullable 외래키

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}
```

생성되는 DDL:

```sql
CREATE TABLE IF NOT EXISTS users (
    id        VARCHAR(10),
    "name"    VARCHAR(50) NOT NULL,
    city_id   INT NULL,
    CONSTRAINT PK_User_ID PRIMARY KEY (id),
    CONSTRAINT fk_users_city_id__id FOREIGN KEY (city_id)
        REFERENCES cities(id) ON DELETE RESTRICT ON UPDATE RESTRICT
)
```

---

## 쿼리 패턴

### INSERT — 함수식 컬럼 표현식 사용

단순 값 입력 외에 SQL 함수를 컬럼 값으로 사용할 수 있습니다.

```kotlin
// 일반 INSERT
val seoulId = CityTable.insert {
    it[name] = "Seoul"
} get CityTable.id

// SQL 함수를 값으로 사용: INSERT INTO cities ("name") VALUES (SUBSTRING(TRIM('   Daegu   '), 1, 2))
val daeguId = CityTable.insert {
    it.update(name, stringLiteral("   Daegu   ").trim().substring(1, 2))
}[CityTable.id]
```

### UPDATE — 조건 지정 갱신

```kotlin
// UPDATE users SET "name" = 'Alexey' WHERE users.id = 'alex'
UserTable.update({ UserTable.id eq "alex" }) {
    it[name] = "Alexey"
}

// 갱신 결과 확인
UserTable
    .selectAll()
    .where { UserTable.id eq "alex" }
    .single()[UserTable.name] shouldBeEqualTo "Alexey"
```

### DELETE — 조건 지정 삭제

```kotlin
// DELETE FROM users WHERE users."name" LIKE '%thing'
val affectedCount = UserTable.deleteWhere { UserTable.name like "%thing" }
affectedCount shouldBeEqualTo 1
```

---

## JOIN 타입별 예제

### INNER JOIN — 외래키 기반 자동 조인

`optReference`로 선언된 외래키가 있으면 Exposed가 자동으로 ON 절을 생성합니다.

```kotlin
// SELECT users."name", users.city_id, cities."name"
//   FROM users INNER JOIN cities ON cities.id = users.city_id
//  WHERE (cities."name" = 'Busan') OR (users.city_id IS NULL)
UserTable
    .innerJoin(CityTable)
    .select(UserTable.name, UserTable.cityId, CityTable.name)
    .where { CityTable.name eq "Busan" }
    .orWhere { UserTable.cityId.isNull() }
    .collect {
        if (it[UserTable.cityId] != null) {
            log.info { "${it[UserTable.name]} lives in ${it[CityTable.name]}" }
        } else {
            log.info { "${it[UserTable.name]} lives nowhere" }
        }
    }
```

### INNER JOIN — 수동 조인 조건 추가

외래키 ON 조건 외에 추가 WHERE 조건을 `andWhere`로 명시할 수 있습니다.

```kotlin
// SELECT users."name", cities."name"
//   FROM users INNER JOIN cities ON cities.id = users.city_id
//  WHERE ((users.id = 'debop') OR (users."name" = 'Jane.Doe'))
//    AND (users.id = 'jane')
//    AND (users.city_id = cities.id)
UserTable
    .innerJoin(CityTable)
    .select(UserTable.name, CityTable.name)
    .where { (UserTable.id eq "debop") or (UserTable.name eq "Jane.Doe") }
    .andWhere { UserTable.id eq "jane" }
    .andWhere { UserTable.cityId eq CityTable.id }   // 수동 조인 조건
    .collect { ... }
```

### GROUP BY + 집계 함수

`count()` 집계와 `groupBy` + `orderBy`를 결합하여 도시별 사용자 수를 집계합니다.

```kotlin
// SELECT cities."name", COUNT(users.id)
//   FROM cities INNER JOIN users ON cities.id = users.city_id
//  GROUP BY cities."name"
//  ORDER BY cities."name"
val query = CityTable.innerJoin(UserTable)
    .select(CityTable.name, UserTable.id.count())
    .groupBy(CityTable.name)
    .orderBy(CityTable.name)

query.collect {
    val cityName = it[CityTable.name]
    val userCount = it[UserTable.id.count()]
    log.info { "$cityName has $userCount users" }
}
```

### `andWhere` — 다중 AND 조건

```kotlin
// SELECT users."name", cities."name"
//   FROM users INNER JOIN cities ON cities.id = users.city_id
//  WHERE cities."name" = 'Busan'
//    AND users."name" LIKE 'J%.Doe'
//  ORDER BY users."name"
val names = UserTable
    .innerJoin(CityTable)
    .select(UserTable.name, CityTable.name)
    .where { CityTable.name eq "Busan" }
    .andWhere { UserTable.name like "J%.Doe" }
    .orderBy(UserTable.name)
    .map { it[UserTable.name] }
    .toList()

names shouldBeEqualTo listOf("Jane.Doe", "John.Doe")
```

---

## 테스트 실행 방법

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :exposed-r2dbc-sql-example:test

# H2만 사용 (빠른 개발 반복)
USE_FAST_DB=true ./gradlew :exposed-r2dbc-sql-example:test

# 특정 테스트 클래스만 실행
./gradlew :exposed-r2dbc-sql-example:test --tests "exposed.r2dbc.sql.example.R2dbcExposedSQLExample"
```

### 테스트 패턴

모든 테스트는 `AbstractR2dbcExposedTest`를 상속하고 `@ParameterizedTest`로 여러 DB에서 실행합니다.

```kotlin
class R2dbcExposedSQLExample: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `andWhere 로 다중 조건 조회하기`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            // 트랜잭션 컨텍스트 안에서 Exposed DSL 실행
            val names = UserTable
                .innerJoin(CityTable)
                .select(UserTable.name, CityTable.name)
                .where { CityTable.name eq "Busan" }
                .andWhere { UserTable.name like "J%.Doe" }
                .map { it[UserTable.name] }
                .toList()

            names shouldBeEqualTo listOf("Jane.Doe", "John.Doe")
        }
    }
}
```

기본 활성 DB: H2, PostgreSQL, MySQL V8, MariaDB (`USE_FAST_DB=false`)
`USE_FAST_DB=true` 설정 시 H2 in-memory만 사용 (빠른 반복 개발용)

---

## 참고 자료

- [Exposed DSL 가이드](https://github.com/JetBrains/Exposed/wiki/DSL)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
