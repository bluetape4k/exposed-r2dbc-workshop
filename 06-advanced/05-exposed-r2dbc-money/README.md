# 05 Exposed R2DBC Money (금융 데이터 처리)

이 모듈은 `exposed-money` 확장을 사용하여 금전적 값을 안전하고 구조화된 방식으로 처리하는 방법을 학습합니다. Java/Kotlin 애플리케이션에서 돈을 다루는 표준인 JavaMoney(
`javax.money`) API와 통합됩니다. 이 접근 방식은 금융 계산에 `Double`이나
`Float`를 사용할 때 발생하는 일반적인 함정을 피하고, 통화 정보가 항상 숫자 금액과 함께 쌍으로 유지되도록 보장합니다.

## 학습 목표

- `compositeMoney`를 사용하여 금전적 값을 저장하는 컬럼 정의
- `compositeMoney`가 단일 `MonetaryAmount` 객체를 데이터베이스의 별도 금액 및 통화 컬럼에 매핑하는 방법 이해
- DSL과 DAO 스타일 모두에서 금전적 데이터 삽입, 업데이트, 쿼리
- 금전 컬럼의 개별 구성 요소(금액과 통화)에 접근하고 쿼리
- 금전 컬럼의 기본값 설정

## 핵심 개념

### `compositeMoney`

이 확장의 핵심은 `compositeMoney()` 함수입니다. 이는 여러 기본 데이터베이스 컬럼을 Kotlin 코드의 단일 논리적 속성으로 번들링하는 "복합 컬럼"입니다.

```kotlin
compositeMoney(precision: Int, scale: Int, columnName: String)
```

이 함수는 백그라운드에서 두 개의 컬럼을 생성하고 관리합니다:

1. 금액용 `DECIMAL` 컬럼 (예: `DECIMAL(precision, scale)`)
2. 통화용 `VARCHAR` 컬럼 (예: `VARCHAR(3)`). 통화 컬럼의 이름은 `columnName`에 `_C`를 추가하여 파생됩니다.

코드에서는 단일 `CompositeColumn<MonetaryAmount?>`로 상호작용합니다.

### 금액 및 통화 접근

`compositeMoney`가 반환하는 `CompositeColumn`은 구성 요소에 대한 접근을 제공합니다:

- `.amount`: 숫자 값을 나타내는 `Column<BigDecimal?>`
- `.currency`: 통화 단위를 나타내는 `Column<CurrencyUnit?>`

이를 통해 전체 `MonetaryAmount`, 금액만, 또는 통화만으로 필터링하는 유연한 쿼리가 가능합니다.

## 예제 개요

### `MoneyData.kt` - 테이블 및 엔티티 정의

`compositeMoney`의 기본 설정을 보여주는 `AccountTable`과 `AccountEntity`를 정의합니다.

- **`AccountTable`**: `composite_money`라는 `compositeMoney` 컬럼이 있는 `IntIdTable`. 데이터베이스에 `composite_money` (DECIMAL)와
  `composite_money_C` (VARCHAR) 컬럼을 생성합니다.
- **`AccountEntity`**: 해당 DAO 엔티티. 복합 컬럼을 `MonetaryAmount?` 타입의 단일 `money` 속성에 매핑합니다. `amount`와
  `currency`에 직접 접근하기 위한 편리한 위임 속성 생성 방법도 보여줍니다.

### `Ex01_MoneyDefaults.kt` - 기본값

상수 값용 `.default()`와 람다로 생성되는 값용 `.clientDefault()`를 모두 사용하여 `compositeMoney` 컬럼의 기본값 설정 방법을 보여줍니다.

### `Ex02_Money.kt` - CRUD 및 쿼리

금전 컬럼 작업에 대한 포괄적인 예제를 제공합니다.

- **삽입**: 완전한 `MonetaryAmount` 객체 삽입 및 `.amount`와 `.currency` 구성 요소를 별도로 설정하여 삽입
- **조회**: `MonetaryAmount` 객체 조회
- **쿼리**: `WHERE` 절에서 복합 컬럼과 그 구성 요소를 사용하여 레코드 찾기
- **수동 복합 컬럼**: 기존 `decimal`과 `currency` 컬럼에서 수동으로 `compositeMoney` 컬럼을 생성하는 방법

## 코드 예제

### 1. 금전 컬럼이 있는 테이블 정의

```kotlin
import org.jetbrains.exposed.v1.money.compositeMoney

internal object AccountTable: IntIdTable("Accounts") {
    // 총 8자리, 소수점 5자리의 nullable 금전 컬럼 정의
    // 두 개의 DB 컬럼 생성: "composite_money"와 "composite_money_C"
    val composite_money = compositeMoney(8, 5, "composite_money").nullable()
}
```

### 2. 엔티티에서 금전 다루기 (DAO)

```kotlin
internal class AccountEntity(id: EntityID<Int>): IntEntity(id) {
  companion object: EntityClass<Int, AccountEntity>(AccountTable)

  // 전체 MonetaryAmount 객체
  var money: MonetaryAmount? by AccountTable.composite_money

  // 기본 금액과 통화에 직접 접근
  val amount: BigDecimal? by AccountTable.composite_money.amount
  val currency: CurrencyUnit? by AccountTable.composite_money.currency
}
```

### 3. 금전적 값 삽입 및 쿼리 (DSL)

```kotlin
import org.javamoney.moneta.Money

// MonetaryAmount 생성
val tenDollars = Money.of(10, "USD")

// 전체 객체 삽입
AccountTable.insert {
    it[composite_money] = tenDollars
}

// 또는 구성 요소를 별도로 삽입
AccountTable.insert {
    it[composite_money.amount] = BigDecimal("10.00")
    it[composite_money.currency] = // ... "USD"에 대한 CurrencyUnit 가져오기
}

// 전체 객체로 쿼리
val results = AccountTable.selectAll().where { AccountTable.composite_money eq tenDollars }

// 통화만으로 쿼리
val usdAccounts = AccountTable.selectAll().where { AccountTable.composite_money.currency eq currencyUnitOf("USD") }
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :06-advanced:05-exposed-r2dbc-money:test

# 특정 테스트 클래스 실행
./gradlew :06-advanced:05-exposed-r2dbc-money:test --tests "exposed.examples.money.Ex02_Money"
```

## 참고 자료

- [Exposed Money](https://debop.notion.site/Exposed-Money-1c32744526b08051a216d87ca750d73f)
