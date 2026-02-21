# 07 Exposed R2DBC Custom Entities (ID 생성 전략)

이 모듈은 Exposed의 강력한 패턴을 보여줍니다: 특정 기본 키 전략을 캡슐화하는 재사용 가능한 기본 `Table`과 `Entity` 클래스를 생성하는 것입니다. 모든 테이블에 대해
`id` 컬럼과 그 기본 생성기를 수동으로 정의하는 대신, 미리 구성된 기본 클래스에서 상속받기만 하면 됩니다.

이 접근 방식은 `06-custom-columns` 모듈의 개념을 기반으로 하여, 커스텀 클라이언트 측 기본값 생성기를 편리하고 재사용 가능한 추상화로 패키징합니다.

## 학습 목표

- 커스텀 기본 `IdTable`과 `Entity` 클래스를 생성하는 방법 이해
- Snowflake, KSUID, 시간 기반 UUID 같은 일반적인 ID 생성 전략을 추상화하는 방법 학습
- 커스텀 기본 클래스 상속을 통한 테이블 정의 간소화
- DSL과 DAO 패턴 모두에서 이러한 커스텀 엔티티를 원활하게 사용

## 제공되는 예제

이 모듈은 다양한 ID 생성 요구에 대해 즉시 사용할 수 있는 여러 기본 테이블/엔티티 쌍을 제공합니다.

### SnowflakeIdTable / SnowflakeIdEntity

| 항목        | 설명                                             |
|-----------|------------------------------------------------|
| **ID 타입** | `Long`                                         |
| **생성기**   | Snowflake 알고리즘을 사용하여 k-ordered 고유 `Long` ID 생성 |
| **용도**    | 대략 시간 순서의 고유 숫자 ID가 필요한 분산 시스템에 적합             |

### KsuidTable / KsuidEntity

| 항목        | 설명                                      |
|-----------|-----------------------------------------|
| **ID 타입** | `String` (varchar 27)                   |
| **생성기**   | K-Sortable Unique Identifier (KSUID) 생성 |
| **용도**    | 고유하면서도 생성 시간별로 사전식 정렬 가능한 ID가 필요할 때 탁월  |

### KsuidMillisTable / KsuidMillisEntity

| 항목        | 설명                        |
|-----------|---------------------------|
| **ID 타입** | `String` (varchar 27)     |
| **생성기**   | 밀리초 정밀도의 KSUID 생성         |
| **용도**    | KSUID와 유사하지만 더 미세한 시간 해상도 |

### TimebasedUUIDTable / TimebasedUUIDEntity

| 항목        | 설명                          |
|-----------|-----------------------------|
| **ID 타입** | `java.util.UUID`            |
| **생성기**   | 시간 기반(버전 1) UUID 생성         |
| **용도**    | 시간 순서인 표준 UUID 형식이 필요할 때 유용 |

### TimebasedUUIDBase62Table / TimebasedUUIDBase62Entity

| 항목        | 설명                                                       |
|-----------|----------------------------------------------------------|
| **ID 타입** | `String` (varchar 22)                                    |
| **생성기**   | 시간 기반 UUID를 생성하고 Base62로 인코딩하여 더 짧고 URL 친화적인 문자열 표현 제공   |
| **용도**    | 시간 순서 UUID가 필요하지만 표준 36자 UUID 문자열보다 더 컴팩트한 문자열 형식이 필요할 때 |

## 작동 원리

이러한 기본 테이블은 일반적으로 `IdTable`을 상속받는 `abstract class`로 구현됩니다. `id` 컬럼은 재정의되어 원하는 타입과 `clientDefault` 생성기로 구성됩니다. 해당
`Entity`와 `EntityClass`도 생성되어 추상화를 완성합니다.

## 코드 예제: SnowflakeIdTable 사용

`SnowflakeIdTable`을 상속받으면 `id` 컬럼과 자동 생성을 무료로 얻을 수 있습니다.

```kotlin
import io.bluetape4k.exposed.dao.id.SnowflakeIdTable
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntity
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntityClass
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntityID

// 1. SnowflakeIdTable을 상속받아 테이블 정의
object Products: SnowflakeIdTable("products") {
    val name = varchar("name", 255)
    val price = integer("price")
}

// 2. SnowflakeIdEntity를 상속받아 엔티티 정의
class Product(id: SnowflakeIdEntityID): SnowflakeIdEntity(id) {
    companion object: SnowflakeIdEntityClass<Product>(Products)

    var name by Products.name
    var price by Products.price
}

// 3. 사용. ID가 자동으로 생성됩니다
transaction {
    // DAO 스타일
    val newProduct = Product.new {
        name = "Laptop"
        price = 1200
    }
    // ID가 이미 할당됨: newProduct.id

    // DSL 스타일
    Products.insert {
        it[name] = "Mouse"
        it[price] = 25
    }
    // Snowflake ID가 자동으로 생성되어 삽입됨
}
```

이 패턴은 상용구 코드를 크게 줄이고, 사용하는 모든 테이블에서 일관된 기본 키 전략을 보장합니다.

## 테스트 실행

이 모듈의 테스트는 각 커스텀 ID 테이블 타입에 대한 레코드 생성, 배치 삽입, 조회를 표준 및 코루틴 컨텍스트 모두에서 보여줍니다.

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :06-advanced:07-exposed-r2dbc-custom-entities:test

# 특정 엔티티 타입 테스트 실행 (예: Snowflake)
./gradlew :06-advanced:07-exposed-r2dbc-custom-entities:test --tests "exposed.examples.custom.entities.SnowflakeIdTableTest"
```

## 참고 자료

- [Custom IdTable & Entities](https://debop.notion.site/Custom-Table-Entities-1c32744526b0804bad10ea3a0dce6c13)
