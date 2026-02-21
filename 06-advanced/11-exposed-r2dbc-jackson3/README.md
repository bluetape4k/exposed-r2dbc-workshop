# 11 Exposed R2DBC Jackson 3 (Jackson 3.x 기반 JSON)

이 모듈은 **Jackson 3.x** 라이브러리를 사용하여 Exposed에서 `JSON`과
`JSONB` 컬럼 타입을 처리하는 방법을 학습합니다. Jackson 2.x 기반 모듈과 동일한 기능을 제공하지만, Jackson 3.x의 새로운 기능과 개선사항을 활용합니다.

## 학습 목표

- Jackson 3.x를 사용하여 Kotlin 데이터 클래스에 매핑되는 `json`과 `jsonb` 컬럼 정의
- Jackson 3.x로 복잡하고 중첩된 객체 저장 및 조회
- `.extract<T>()`, `.contains()`, `.exists()`를 포함한 Exposed의 전체 JSON 쿼리 함수 사용
- DSL과 DAO 프로그래밍 스타일 모두에서 Jackson 3.x 기반 JSON 컬럼 적용

## 핵심 개념

### 컬럼 타입

| 타입                  | 설명                                                             |
|---------------------|----------------------------------------------------------------|
| `jackson<T>(name)`  | 표준 `JSON` 텍스트 컬럼에 Jackson 3.x 호환 객체 `T`를 저장하는 컬럼 정의            |
| `jacksonb<T>(name)` | 최적화된 `JSONB` (바이너리 JSON) 컬럼에 Jackson 3.x 호환 객체 `T`를 저장하는 컬럼 정의 |

### 쿼리 함수

Jackson 2.x 모듈과 동일한 쿼리 함수를 사용할 수 있습니다:

| 함수                            | 설명                               |
|-------------------------------|----------------------------------|
| `.extract<T>(path, toScalar)` | JSON 문서에서 특정 경로의 값 추출            |
| `.contains(value, path)`      | JSON 문서에 주어진 JSON 값이 포함되어 있는지 확인 |
| `.exists(path, optional)`     | 주어진 JSONPath 표현식에 값이 존재하는지 확인    |

## 예제 개요

### `JacksonColumnTest.kt` (DSL & DAO with `json`)

Jackson 3.x를 사용한 `json` (텍스트 기반 JSON) 컬럼 타입의 사용법을 보여줍니다. 표준 CRUD 작업과 `.extract()`, `.contains()`,
`.exists()`를 사용한 JSON 특정 쿼리를 다룹니다. DAO 패턴과의 통합도 보여줍니다.

### `JacksonBColumnTest.kt` (DSL & DAO with `jsonb`)

`JacksonColumnTest.kt`와 유사하지만 `jacksonb` 컬럼 타입에 초점을 맞춥니다. 코드는 유사하게 구성되어 `json`과 `jsonb` 타입 간의 일관된 API를 강조합니다.

## 코드 예제

### 1. Jackson 3.x로 `jacksonb` 컬럼이 있는 테이블 정의

```kotlin
import io.bluetape4k.exposed.core.jackson3.jacksonb
import com.fasterxml.jackson.annotation.JsonCreator // Jackson 3 어노테이션 예시

// 표준 데이터 클래스
data class User(val name: String, val team: String?)
data class UserData(val info: User, val logins: Int, val active: Boolean)

object UsersTable: IntIdTable("users") {
    // 컬럼이 UserData 객체를 Jackson 3.x를 사용하여 JSONB로 저장
    val data = jacksonb<UserData>("data")
}
```

### 2. Jackson 3.x로 삽입 및 쿼리 (DSL)

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

### 3. 엔티티에서 Jackson 3.x 컬럼 사용 (DAO)

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
./gradlew :06-advanced:11-exposed-r2dbc-jackson3:test

# JSONB 컬럼 타입 테스트
./gradlew :06-advanced:11-exposed-r2dbc-jackson3:test --tests "exposed.examples.jackson3.JacksonBColumnTest"
```

## 참고 자료

- [Exposed Jackson](https://debop.notion.site/Exposed-Jackson-1c32744526b0809599a7db2e629a597a)
