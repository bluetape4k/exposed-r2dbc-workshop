# 03 Exposed R2DBC SQL Example (SQL DSL 기본)

Exposed R2DBC의 SQL DSL(Domain Specific Language)을 사용하여 타입 안전한 쿼리를 작성하는 방법을 학습합니다. R2DBC 환경에서 비동기로 SELECT, INSERT, UPDATE, DELETE를 수행합니다.

## 학습 목표

- Exposed DSL로 테이블 정의하는 방법 이해
- 타입 안전한 SQL 쿼리 작성
- JOIN을 통한 관계형 데이터 조회
- R2DBC 환경에서의 비동기 쿼리 실행 패턴

## 예제 ERD

여기에 사용되는 ERD는 다음과 같습니다. 도시에 살고 있는 사용자를 표현하기 위한 ERD를 정의합니다.
`cities` 와 `users` 는 1:N 관계입니다.

![City and Users ERD](CityUserSchema.png)

## 테이블 정의

Kotlin object인 `CityTable` 은  `cities` 테이블을 정의합니다.

`CityTable.id` 는 auto increment 속성을 가지고, primary key로 설정합니다.

```kotlin
object CityTable: Table("cities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", length = 50)
    
    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}
```

`UserTable` 은 `users` 테이블을 정의합니다. id 는 varchar 타입으로 primary key 로 설정합니다.
`cityId` 는 `CityTable` 의 id 를 참조하는 외래키로 설정합니다.

```kotlin
object UserTable: Table("users") {
    val id = varchar("id", length = 10)
    val name = varchar("name", length = 50)
    val cityId = optReference("city_id", CityTable.id)
    
    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}
```

## 테이블 사용

Exposed 의 `Table` 을 상속받은 `CityTable` 과 `UserTable` 을 사용하여, 쿼리를 작성하는 법을 알아보겠습니다.

### Inner Join 을 통한 조회

다음 코드는 `UserTable` 과 `CityTable` 을 inner join 하여, `UserTable` 의 name 과 `CityTable` 의 name 을 조회합니다.

```kotlin
@ParameterizedTest
@MethodSource(ENABLE_DIALECTS_METHOD)
fun `join with foreign key`(testDB: TestDB) {
    withCityUsers(testDB) {
        UserTable
            .innerJoin(CityTable)
            .select(UserTable.name, UserTable.cityId, CityTable.name)
            .where { CityTable.name eq "Busan" }
            .orWhere { UserTable.cityId.isNull() }
            .forEach {
                if (it[UserTable.cityId] != null) {
                    log.info { "${it[UserTable.name]} lives in ${it[CityTable.name]}" }
                } else {
                    log.info { "${it[UserTable.name]} lives nowhere" }
                }
            }
    }
}
```

위 메소드에서 생성되는 SQL 문은 다음과 같습니다. SQL 생성을 위한 Kotlin DSL 이 상당히 직관적입니다.

```sql
-- Postgres
SELECT users."name",
       users.city_id,
       cities."name"
  FROM users INNER JOIN cities ON cities.id = users.city_id
 WHERE (cities."name" = 'Busan')
    OR (users.city_id IS NULL)
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :03-exposed-r2dbc-basic:exposed-r2dbc-sql-example:test
```

## 참고 자료

- [Exposed DSL 가이드](https://github.com/JetBrains/Exposed/wiki/DSL)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
