# 08 Exposed R2DBC Jackson (Jackson 기반 JSON)

이 모듈은 인기 있는 **Jackson** 라이브러리를 활용하여 Exposed에서 `JSON`과 `JSONB` 컬럼 타입을 사용하는 방법을 학습합니다. `kotlinx.serialization`을 사용하는
`exposed-json` 모듈의 대안으로, 이미 Jackson 생태계를 사용 중인 프로젝트에 이상적입니다.

## 학습 목표

- Jackson을 사용하여 Kotlin 데이터 클래스에 매핑되는 `json`과 `jsonb` 컬럼 정의
- `@Serializable` 어노테이션 없이 복잡하고 중첩된 객체 저장 및 조회
- `.extract<T>()`, `.contains()`, `.exists()`를 포함한 Exposed의 전체 JSON 쿼리 함수 사용
- DSL과 DAO 프로그래밍 스타일 모두에서 Jackson 기반 JSON 컬럼 적용

## 핵심 개념

이 모듈의 API는 `exposed-json`과 거의 동일하지만, 기본 구현은 Jackson의 `ObjectMapper`를 사용합니다.

### Jackson ObjectMapper 설정 주의사항

`bluetape4k-exposed` 의 Jackson 컬럼 타입은 내부적으로 `ObjectMapper` 인스턴스를 공유합니다.
커스터마이징이 필요한 경우 아래 사항에 유의하세요:

| 주의사항                              | 설명                                                                     |
|-----------------------------------|------------------------------------------------------------------------|
| **KotlinModule 등록**               | Kotlin 데이터 클래스 역직렬화를 위해 `KotlinModule`이 자동 등록됩니다                       |
| **`@JsonIgnoreProperties(ignoreUnknown = true)`** | 스키마 변경 시 역직렬화 오류 방지를 위해 권장합니다                              |
| **`WRITE_DATES_AS_TIMESTAMPS`**   | 기본값 `true` — `false`로 설정 시 날짜가 ISO 8601 문자열로 직렬화됩니다                   |
| **`FAIL_ON_EMPTY_BEANS`**         | 기본값 `true` — 빈 객체 직렬화 시 예외 발생. 필요 시 `false`로 비활성화                     |
| **`FAIL_ON_UNKNOWN_PROPERTIES`**  | 기본값 `true` — JSON에 알 수 없는 필드가 있으면 예외 발생. 스키마 진화 시 `false`로 설정 권장      |
| **스레드 안전**                        | `ObjectMapper`는 스레드 안전(thread-safe)하므로 단일 인스턴스를 공유해도 됩니다              |

### SerializationFeature 주요 옵션

Jackson의 `SerializationFeature`와 `DeserializationFeature`는 직렬화/역직렬화 동작을 세밀하게 제어합니다:

| 옵션                                          | 기본값     | 설명                                                |
|---------------------------------------------|---------|---------------------------------------------------|
| `SerializationFeature.INDENT_OUTPUT`        | `false` | JSON 출력을 보기 좋게 들여쓰기 (디버깅용)                        |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` | `true`  | 날짜를 타임스탬프 숫자로 직렬화 (`false` 시 ISO 8601)      |
| `SerializationFeature.WRITE_ENUMS_USING_TO_STRING` | `false` | enum을 `toString()` 값으로 직렬화 (기본: `name()`)  |
| `SerializationFeature.FAIL_ON_EMPTY_BEANS`  | `true`  | 매핑 가능한 속성이 없는 빈 객체 직렬화 시 예외 발생                    |
| `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` | `true` | JSON에 알 수 없는 필드가 있을 때 예외 발생 (스키마 진화 시 주의) |
| `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` | `false` | 부동소수점을 `BigDecimal`로 역직렬화 (정밀도 필요 시 사용)    |

### 컬럼 타입

| 타입                  | 설명                                                                                      |
|---------------------|-----------------------------------------------------------------------------------------|
| `jackson<T>(name)`  | 표준 `JSON` 텍스트 컬럼에 Jackson 호환 객체 `T`를 저장하는 컬럼 정의                                         |
| `jacksonb<T>(name)` | 최적화된 `JSONB` (바이너리 JSON) 컬럼에 Jackson 호환 객체 `T`를 저장하는 컬럼 정의. PostgreSQL 등 지원 데이터베이스에서 권장 |

`exposed-json`과 달리 데이터 클래스에 `@Serializable` 표시가 **필요하지 않습니다**. 표준 Kotlin 데이터 클래스나 POJO를 사용할 수 있습니다.

### 쿼리 함수

동일한 강력한 쿼리 함수를 사용할 수 있습니다:

| 함수                            | 설명                                        |
|-------------------------------|-------------------------------------------|
| `.extract<T>(path, toScalar)` | JSON 문서에서 특정 경로의 값 추출                     |
| `.contains(value, path)`      | JSON 문서에 주어진 JSON 형식 문자열이 값으로 포함되어 있는지 확인 |
| `.exists(path, optional)`     | 주어진 JSONPath 표현식에 값이 존재하는지 확인             |

## 예제 개요

### `JacksonSchema.kt`

데이터 클래스(`User`, `DataHolder`)와 Exposed `Table` 객체(`JacksonTable`, `JacksonBTable`)를 정의합니다. DAO `Entity` 클래스(
`JacksonEntity`, `JacksonBEntity`)와 테스트 헬퍼 함수도 포함합니다.

### `JacksonColumnTest.kt` (DSL & DAO with `json`)

`json` (텍스트 기반 JSON) 컬럼 타입의 사용법을 보여줍니다:

- `INSERT`, `UPDATE`, `UPSERT`, `SELECT` 작업
- `.extract()`, `.contains()`, `.exists()`를 사용한 쿼리
- DAO 엔티티 내에서 컬럼 사용
- 컬렉션과 nullable JSON 컬럼 처리

### `JacksonBColumnTest.kt` (DSL & DAO with `jsonb`)

`JacksonColumnTest.kt`와 유사하지만 더 성능이 좋은 `jacksonb` 컬럼 타입을 사용합니다. 코드는 거의 동일하며 API의 일관성을 보여줍니다.

## 코드 예제

### 1. `jacksonb` 컬럼이 있는 테이블 정의

```kotlin
import io.bluetape4k.exposed.core.jackson.jacksonb

// 표준 데이터 클래스 - @Serializable 불필요
data class User(val name: String, val team: String?)
data class UserData(val info: User, val logins: Int, val active: Boolean)

object UsersTable: IntIdTable("users") {
  // 컬럼이 UserData 객체를 Jackson을 사용하여 JSONB로 저장
  val data = jacksonb<UserData>("data")
}
```

### 2. Jackson으로 삽입 및 쿼리 (DSL)

```kotlin
val userData = UserData(info = User("test", "A"), logins = 5, active = true)

// 데이터 삽입
UsersTable.insert {
  it[data] = userData
}

// 중첩된 값 추출 후 WHERE 절에서 사용
// 참고: 경로 문법은 데이터베이스마다 다를 수 있음
val username = UsersTable.data.extract<String>(".info.name")
val userRecord = UsersTable.selectAll().where { username eq "test" }.single()

// 읽을 때 전체 객체가 자동으로 역직렬화됨
val retrievedData = userRecord[UsersTable.data]
retrievedData.logins shouldBeEqualTo 5
```

### 3. 엔티티에서 Jackson 컬럼 사용 (DAO)

```kotlin
class UserEntity(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<UserEntity>(UsersTable)

  // 속성이 JSON으로/에서 자동 매핑됨
  var data by UsersTable.data
}

// 새 엔티티 생성
val entity = UserEntity.new {
  data = UserData(info = User("dao_user", "B"), logins = 1, active = true)
}

// 속성 접근
println(entity.data.info.name) // "dao_user" 출력
```

## 테스트 실행

**참고**: JSON/JSONB 기능은 데이터베이스에 따라 크게 달라집니다. 많은 테스트가 제한된 지원을 가진 데이터베이스(예: H2)에서는 건너뜁니다. 최상의 결과를 위해 PostgreSQL에서 실행하세요.

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :08-exposed-r2dbc-jackson:test

# JSONB 컬럼 타입 테스트
./gradlew :08-exposed-r2dbc-jackson:test --tests "exposed.examples.jackson.JacksonBColumnTest"
```

## 참고 자료

- [Exposed Jackson](https://debop.notion.site/Exposed-Jackson-1c32744526b0809599a7db2e629a597a)
