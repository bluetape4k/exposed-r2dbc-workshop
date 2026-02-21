# 02 Exposed R2DBC JavaTime (java.time 통합)

이 모듈은 Java 8의 `java.time` (JSR-310) API와 Exposed의 통합 방법을 학습합니다. Exposed에서 날짜와 시간 값을 처리하는 표준적이고 권장되는 방법입니다.

## 학습 목표

- 데이터베이스 날짜/시간 타입을 `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` 같은 `java.time` 객체로 매핑하는 방법 이해
- 날짜/시간 조작을 위한 내장 SQL 함수(`year()`, `month()`, `day()` 등) 사용
- `CURRENT_TIMESTAMP` 같은 서버 측 기본값을 포함한 날짜/시간 컬럼의 기본값 정의
- `WHERE` 절에서 비교를 위해 날짜/시간 리터럴 올바르게 사용
- 다양한 데이터베이스 방언 간의 날짜/시간 처리 및 정밀도 차이 인식

## 주요 컬럼 타입 및 함수

`exposed-java-time` 모듈은 Exposed DSL에 여러 컬럼 타입과 함수를 추가합니다.

### 컬럼 타입

| 타입                            | 설명           | Java 타입                    |
|-------------------------------|--------------|----------------------------|
| `date(name)`                  | 날짜           | `java.time.LocalDate`      |
| `time(name)`                  | 시간           | `java.time.LocalTime`      |
| `datetime(name)`              | 날짜시간         | `java.time.LocalDateTime`  |
| `timestamp(name)`             | 타임스탬프        | `java.time.Instant`        |
| `timestampWithTimeZone(name)` | 시간대 포함 타임스탬프 | `java.time.OffsetDateTime` |
| `duration(name)`              | 기간           | `java.time.Duration`       |

### 기본값 표현식

서버 측 기본값을 설정하기 위한 특수 표현식을 사용할 수 있습니다.

| 표현식                                    | 설명                                            |
|----------------------------------------|-----------------------------------------------|
| `CurrentDate`                          | 데이터베이스의 `CURRENT_DATE` 함수                     |
| `CurrentDateTime` / `CurrentTimestamp` | 데이터베이스의 `CURRENT_TIMESTAMP` 또는 동등한 함수         |
| `CurrentTimestampWithTimeZone`         | 데이터베이스의 `CURRENT_TIMESTAMP WITH TIME ZONE` 함수 |

### 쿼리용 리터럴

`WHERE` 절에서 날짜/시간 값을 비교할 때, 특정 데이터베이스 방언에 맞게 값이 올바르게 포맷되도록 리터럴 함수를 사용하는 것이 좋습니다.

| 함수                                             | 설명               |
|------------------------------------------------|------------------|
| `dateLiteral(LocalDate)`                       | 날짜 리터럴           |
| `timeLiteral(LocalTime)`                       | 시간 리터럴           |
| `dateTimeLiteral(LocalDateTime)`               | 날짜시간 리터럴         |
| `timestampLiteral(Instant)`                    | 타임스탬프 리터럴        |
| `timestampWithTimeZoneLiteral(OffsetDateTime)` | 시간대 포함 타임스탬프 리터럴 |

## 예제 개요

### `Ex01_JavaTime.kt` - 기본 사용법 및 함수

핵심 기능을 보여줍니다:

- 쿼리에서 `year()`, `month()`, `day()` 같은 날짜 부분 추출 함수 사용
- `Instant`와 `LocalDateTime` 값 저장 및 조회, 나노초 정밀도 처리 포함
- `timestampWithTimeZone` 작업 및 다양한 데이터베이스의 시간대 정보 처리 방법 이해

### `Ex02_Defaults.kt` - 기본값

날짜/시간 컬럼의 기본값 설정 방법을 살펴봅니다.

- `clientDefault { ... }`: 클라이언트(애플리케이션)에서 `INSERT` 문 전에 기본값 생성
- `default(value)`: 상수 기본값
- `defaultExpression(...)`: `CurrentDateTime` 같은 함수를 사용하여 데이터베이스 자체에서 기본값 생성

### `Ex03_DateTimeLiteral.kt` - 리터럴로 쿼리하기

`WHERE` 절에서 날짜/시간 값을 올바르게 사용하는 방법을 보여줍니다. `dateLiteral()`,
`dateTimeLiteral()` 같은 리터럴 함수를 사용하면 다양한 데이터베이스에서 비교가 올바르게 작동합니다.

### `Ex04_MiscTable.kt` - 종합 통합 테스트

모든 `java.time` 컬럼 타입을 nullable과 non-nullable 변형으로 포함한 대규모 테이블(`Misc`)을 포함합니다. `INSERT`, `SELECT`,
`UPDATE` 작업에 대한 광범위한 테스트를 제공합니다.

## 코드 예제

### 1. `java.time` 컬럼이 있는 테이블 정의

```kotlin
object CitiesTime: IntIdTable("CitiesTime") {
  val name: Column<String> = varchar("name", 50)

  // nullable LocalDateTime 컬럼
  val local_time: Column<LocalDateTime?> = datetime("local_time").nullable()
}

object TableWithDBDefault: IntIdTable("t_db_default") {
  // 서버 측 기본값이 있는 non-nullable LocalDateTime 컬럼
  val t1: Column<LocalDateTime> = datetime("t1").defaultExpression(CurrentDateTime)
}
```

### 2. `java.time` 값 삽입 및 조회

```kotlin
// 값 삽입
val now = LocalDateTime.now()
val cityID = CitiesTime.insertAndGetId {
  it[name] = "Seoul"
  it[local_time] = now
}

// 날짜 부분 함수를 사용한 조회
val year = CitiesTime
  .select(CitiesTime.local_time.year())
  .where { CitiesTime.id eq cityID }
  .single()[CitiesTime.local_time.year()]

// WHERE 절에서 리터럴을 사용한 조회
val result = TableWithDate.selectAll()
  .where { TableWithDate.date less dateLiteral(LocalDate.of(3000, 1, 1)) }
  .firstOrNull()
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :06-advanced:02-exposed-r2dbc-javatime:test

# 특정 테스트 클래스 실행
./gradlew :06-advanced:02-exposed-r2dbc-javatime:test --tests "exposed.examples.java.time.Ex01_JavaTime"
```

## 참고 자료

- [Exposed Java Time Module](https://debop.notion.site/Exposed-Java-Time-1c32744526b0809d85e1d0425038dfdd)
