# 03 SQL Functions (SQL 함수)

Exposed R2DBC DSL에서 사용 가능한 **SQL 함수(Functions)
** 예제 모듈입니다. 문자열/비트 연산, 수학 함수, 통계 함수, 삼각 함수, 윈도우 함수(Window Function) 등 SQL 내장 함수와 커스텀 함수를 6개의 테스트 파일로 다루고 있습니다.

## 학습 목표

- SQL 함수를 Exposed DSL에서 사용하는 방법 이해
- 문자열, 수학, 통계, 삼각 함수 활용
- 윈도우 함수(Window Function)로 분석 쿼리 작성
- 커스텀 함수 정의 및 사용법 습득

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 프로젝트 구조

```
src/test/kotlin/exposed/r2dbc/examples/functions/
├── Ex00_FunctionBase.kt           # 공통 베이스 클래스: DUAL 테이블 기반 함수 평가 헬퍼
├── Ex01_Functions.kt              # 종합 함수: 문자열(upper, lower, concat, charLength), 비트 연산, case/when, coalesce, CustomFunction
├── Ex02_MathFunction.kt           # 수학 함수: abs, ceil, floor, round, sqrt, exp, power, sign
├── Ex03_StatisticsFunction.kt     # 통계 함수: stdDevPop, stdDevSamp, varPop, varSamp
├── Ex04_TrigonometricalFunction.kt # 삼각 함수: sin, cos, tan, asin, acos, atan, cot, degrees, radians, pi
└── Ex05_WindowFunction.kt         # 윈도우 함수: rowNumber, rank, denseRank, lead, lag, firstValue, lastValue, nthValue, ntile, cumeDist, percentRank
```

> **참고**: 이 모듈은 `src/main`이 없고, 모든 코드가 `src/test`에 위치합니다. 학습/실습 목적의 테스트 전용 모듈입니다.

## 예제 상세

### Ex00_FunctionBase - 공통 베이스 클래스

모든 함수 테스트의 부모 클래스로, `DUAL` 테이블을 이용해 SQL 함수 결과를 평가하는 `shouldExpressionEqualTo` 헬퍼를 제공합니다.

```kotlin
// DUAL 테이블에서 함수 결과 평가
protected suspend infix fun <T> SqlFunction<T>.shouldExpressionEqualTo(expected: T) {
    val result = Table.Dual.select(this).first()[this]
    result shouldBeEqualTo expected
}
```

### Ex01_Functions - 종합 함수

| 카테고리  | 함수                                                                               |
|-------|----------------------------------------------------------------------------------|
| 문자열   | `UpperCase`, `LowerCase`, `Concat`, `CharLength`, `substring`, `trim`, `locate`  |
| 비트 연산 | `bitwiseAnd`, `bitwiseOr`, `bitwiseXor`                                          |
| 조건    | `case`/`when`, `Coalesce`, `andIfNotNull`, `orIfNotNull`                         |
| 산술    | `DivideOp`, `ModOp`, `Sum`                                                       |
| 커스텀   | `CustomStringFunction`, `CustomLongFunction`, `CustomFunction`, `CustomOperator` |

### Ex02_MathFunction - 수학 함수

| 함수                | 설명               |
|-------------------|------------------|
| `AbsFunction`     | 절대값              |
| `CeilingFunction` | 올림               |
| `FloorFunction`   | 내림               |
| `RoundFunction`   | 반올림 (소수점 자릿수 지정) |
| `SqrtFunction`    | 제곱근              |
| `ExpFunction`     | 지수 함수 (e^x)      |
| `PowerFunction`   | 거듭제곱             |
| `SignFunction`    | 부호 (-1, 0, 1)    |

### Ex03_StatisticsFunction - 통계 함수

| 함수           | 설명       |
|--------------|----------|
| `stdDevPop`  | 모집단 표준편차 |
| `stdDevSamp` | 표본 표준편차  |
| `varPop`     | 모집단 분산   |
| `varSamp`    | 표본 분산    |

### Ex04_TrigonometricalFunction - 삼각 함수

`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `cot`, `degrees`, `radians`, `pi` 등 SQL 삼각 함수의 Exposed DSL 매핑을 다룹니다.

### Ex05_WindowFunction - 윈도우 함수

SQL 분석 함수(Window Function)의 종합 예제입니다:

| 카테고리 | 함수                                                                                          |
|------|---------------------------------------------------------------------------------------------|
| 순위   | `rowNumber`, `rank`, `denseRank`, `percentRank`, `cumeDist`, `ntile`                        |
| 값 접근 | `lead`, `lag`, `firstValue`, `lastValue`, `nthValue`                                        |
| 집계   | `sum`, `avg`, `count`, `min`, `max`, `stdDevPop`, `stdDevSamp`, `varPop`, `varSamp` (OVER절) |
| 프레임  | `WindowFrameBound`, `ROWS`, `RANGE`, `UNBOUNDED PRECEDING/FOLLOWING`                        |

```kotlin
// 윈도우 함수 사용 예
val rowNum = rowNumber().over()
    .partitionBy(sales.product)
    .orderBy(sales.amount, SortOrder.DESC)

sales.select(sales.product, sales.amount, rowNum)
    .toFastList()
```

## 공유 테스트 인프라

- `Ex00_FunctionBase` - 함수 평가용 베이스 클래스
- `DMLTestData.Sales` - 윈도우 함수 테스트에 사용하는 매출 데이터
- `R2dbcExposedTestBase` - 멀티 DB 테스트 지원

## 테스트 실행

```bash
# 전체 Functions 테스트 실행
./gradlew :05-exposed-r2dbc-dml:03-functions:test

# 특정 테스트 클래스 실행
./gradlew :05-exposed-r2dbc-dml:03-functions:test --tests "exposed.r2dbc.examples.functions.Ex05_WindowFunction"
```

## Further Reading

- [7.3 Functions](https://debop.notion.site/1ca2744526b0805e9689efa4a03d01df?v=1ca2744526b08138857a000c9847c052)
