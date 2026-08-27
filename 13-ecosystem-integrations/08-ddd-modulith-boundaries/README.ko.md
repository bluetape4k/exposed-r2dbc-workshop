# DDD Bounded Context와 Modulith Boundary Verification

[English](README.md) | 한국어

이 예제는 작은 DDD order flow와 Spring Modulith boundary verification을
결합한다. Order context가 자기 R2DBC table을 소유하고
`OrderAcceptedEvent` named interface만 공개한다. Shipping context는 order
repository나 table을 import하지 않고 그 event에 반응해 자기 reservation table에
기록한다.

## 예제가 증명하는 것

- `orders`와 `shipping`이 서로 다른 Spring Modulith module인지 확인;
- 각 context가 `internal` package 안에 자기 Exposed R2DBC table과 repository를
  소유하는지 확인;
- `orders.events`만 order context의 named interface로 공개되는지 확인;
- `shipping`이 `allowedDependencies = ["orders :: events"]`만 선언하는지 확인;
- 정상 application이 `ApplicationModules.verify()`를 통과하는지 확인;
- `orders.internal.LeakyOrderRepository`를 import하는 negative fixture가
  Spring Modulith `Violations`로 거부되는지 확인.

Order service는 aggregate를 저장하고 commit한 뒤 event를 발행한다. Shipping
listener는 event를 coroutine과 자기 `suspendTransaction`으로 전달한다. 따라서
두 bounded context가 독립적이며 synchronous Spring Modulith publication SPI가
R2DBC transaction manager라고 오해하지 않는다.

## Boundary 구조

| Package | 역할 |
|---|---|
| `orders` | order command, application service, module metadata |
| `orders.events` | 공개 `OrderAcceptedEvent` named interface |
| `orders.internal` | 비공개 Exposed R2DBC table과 repository |
| `shipping` | event listener, reservation model, module metadata |
| `shipping.internal` | 비공개 Exposed R2DBC table과 repository |

Root package에는 Spring Boot entrypoint와 R2DBC pool 설정만 둔다. Schema
initialization도 각 bounded context 안에 두어 root가 module internal에 의존하지
않는다.

## Negative fixture

`src/test/kotlin/.../invalid`가 같은 module metadata를 반복하고 다음 금지된
의존성을 추가한다.

~~~kotlin
import exposed.r2dbc.examples.spring.modulith.boundaries.invalid.orders.internal.LeakyOrderRepository
~~~

Test는 ArchUnit의 `ImportOption.Predefined.DO_NOT_INCLUDE_JARS`로 fixture를
import하고 Spring Modulith가 `orders.internal` 의존성을 violation으로 보고하는지
확인한다.

## 실행

~~~bash
./gradlew :08-ddd-modulith-boundaries:test -PuseDB=H2
~~~

Test는 local H2 R2DBC pool을 사용한다. JDBC `DataSource`, Hikari bridge, Docker
service, 외부 broker가 필요하지 않다.

## 검증하는 동작

- 정상 module이 `ApplicationModules.verify()`를 통과하는지 확인;
- `shipping`에서 `orders.internal`로 향하는 직접 의존성이 거부되는지 확인;
- order accept가 `ddd_modulith_orders`에 저장되는지 확인;
- commit된 domain event가 전달되어 `ddd_modulith_shipping_reservations` row를
  만드는지 확인.
