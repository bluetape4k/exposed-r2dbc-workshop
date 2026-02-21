# 03 Exposed R2DBC Kotlin DateTime (kotlinx.datetime 통합)

이 모듈은 `kotlinx.datetime` 라이브러리와 Exposed의 통합 방법을 학습합니다. 현대적인 멀티플랫폼 Kotlin 프로젝트에서 날짜와 시간을 처리하는 권장 방법입니다.

## 학습 목표

- 데이터베이스 날짜/시간 타입을 `LocalDate`, `LocalDateTime`, `Instant` 같은 `kotlinx.datetime` 객체로 매핑하는 방법 이해
- 날짜/시간 조작을 위한 내장 SQL 함수(`year()`, `month()`, `day()` 등) 사용
- `CurrentDateTime` 같은 표현식을 사용하여 날짜/시간 컬럼의 서버 측 기본값 정의
- 타입 안전한 비교를 위해 `WHERE` 절에서 날짜/시간 리터럴 올바르게 사용
- 다양한 데이터베이스 백엔드 간의 날짜/시간 처리 차이 인식

## 주요 컬럼 타입 및 함수

`exposed-kotlin-datetime` 모듈은 `exposed-java-time` 모듈과 유사한 컬럼 타입과 함수 세트를 `kotlinx.datetime` 라이브러리용으로 제공합니다.

### 컬럼 타입

| 타입                            | 설명           | Kotlin 타입                        |
|-------------------------------|--------------|----------------------------------|
| `date(name)`                  | 날짜           | `kotlinx.datetime.LocalDate`     |
| `time(name)`                  | 시간           | `kotlinx.datetime.LocalTime`     |
| `datetime(name)`              | 날짜시간         | `kotlinx.datetime.LocalDateTime` |
| `timestamp(name)`             | 타임스탬프        | `kotlinx.datetime.Instant`       |
| `timestampWithTimeZone(name)` | 시간대 포함 타임스탬프 | `java.time.OffsetDateTime`       |
| `duration(name)`              | 기간           | `kotlin.time.Duration`           |

> **참고**: `kotlinx.datetime`에는 네이티브 offset-aware 타입이 없으므로 `timestampWithTimeZone`은 `java.time.OffsetDateTime`에 매핑됩니다.

### 기본값 표현식

데이터베이스 생성 기본값을 설정하기 위한 표현식입니다.

| 표현식                                    | 설명                                    |
|----------------------------------------|---------------------------------------|
| `CurrentDate`                          | 데이터베이스의 `CURRENT_DATE` 함수             |
| `CurrentDateTime` / `CurrentTimestamp` | 데이터베이스의 `CURRENT_TIMESTAMP` 또는 동등한 함수 |
| `CurrentTimestampWithTimeZone`         | `CURRENT_TIMESTAMP WITH TIME ZONE`    |

### 쿼리용 리터럴

다양한 데이터베이스 방언에서 올바른 SQL 생성을 보장하기 위해 `WHERE` 절에서 비교할 때 사용합니다.

| 함수                                             | 설명               |
|------------------------------------------------|------------------|
| `dateLiteral(LocalDate)`                       | 날짜 리터럴           |
| `timeLiteral(LocalTime)`                       | 시간 리터럴           |
| `dateTimeLiteral(LocalDateTime)`               | 날짜시간 리터럴         |
| `timestampLiteral(Instant)`                    | 타임스탬프 리터럴        |
| `timestampWithTimeZoneLiteral(OffsetDateTime)` | 시간대 포함 타임스탬프 리터럴 |

## 예제 개요

이 모듈의 예제는 `exposed-java-time` 모듈과 유사하며, `kotlinx.datetime`에 대한 병렬 기능을 보여줍니다.

### `Ex01_KotlinDateTime.kt` - 기본 사용법 및 함수

핵심 기능을 보여줍니다:

- 쿼리에서 날짜 부분 추출 함수(`.year()`, `.month()`) 사용
- 나노초 정밀도를 포함한 `kotlinx.datetime` 타입 저장 및 조회
- `timestampWithTimeZone`을 통한 시간대 작업

### `Ex02_Defaults.kt` - 기본값

`kotlinx.datetime` 컬럼의 기본값 설정 방법을 살펴봅니다.

- `default(value)`: 상수, 클라이언트 측 기본값
- `clientDefault { ... }`: 람다로 생성되는 클라이언트 측 기본값
- `defaultExpression(...)`: `CurrentDateTime` 같은 데이터베이스 함수를 사용한 서버 측 기본값

### `Ex03_DateTimeLiteral.kt` - 리터럴로 쿼리하기

`WHERE` 절에서 `kotlinx.datetime` 값을 올바르게 사용하는 방법을 보여줍니다. `dateLiteral`,
`dateTimeLiteral` 등의 리터럴 함수로 감싸서 적절한 SQL 포맷팅을 보장합니다.

## 코드 예제

### 1. `kotlinx.datetime` 컬럼이 있는 테이블 정의

```kotlin
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.CurrentDateTime

object CitiesTime: IntIdTable("CitiesTime") {
  val name: Column<String> = varchar("name", 50)

  // nullable kotlinx.datetime.LocalDateTime 컬럼
  val local_time: Column<LocalDateTime?> = datetime("local_time").nullable()
}

object TableWithDBDefault: IntIdTable() {
  // 서버 측 기본값이 있는 non-nullable kotlinx.datetime.LocalDateTime 컬럼
  val t1: Column<LocalDateTime> = datetime("t1").defaultExpression(CurrentDateTime)
}
```

### 2. `kotlinx.datetime` 값 삽입 및 조회

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.datetime.dateLiteral

// 값 삽입
val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
val cityID = CitiesTime.insertAndGetId {
  it[name] = "Tunisia"
  it[local_time] = now
}

// 날짜 부분 함수를 사용한 조회
val insertedMonth = CitiesTime.select(CitiesTime.local_time.month())
  .where { CitiesTime.id eq cityID }
  .single()[CitiesTime.local_time.month()]

// WHERE 절에서 리터럴을 사용한 조회
val result = TableWithDate.selectAll()
  .where { TableWithDate.date less dateLiteral(LocalDate(3000, 1, 1)) }
  .firstOrNull()
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :06-advanced:03-exposed-r2dbc-kotlin-datetime:test

# 특정 테스트 클래스 실행
./gradlew :06-advanced:03-exposed-r2dbc-kotlin-datetime:test --tests "exposed.examples.kotlin.datetime.Ex01_KotlinDateTime"
```

## 참고 자료

- [Exposed Kotlin DateTime Module](https://debop.notion.site/Exposed-Kotlin-DateTime-1c32744526b0807bb3e8f149ef88f5f5)
