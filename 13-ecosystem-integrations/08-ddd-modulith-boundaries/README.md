# DDD Bounded Context and Modulith Boundary Verification

English | [한국어](README.ko.md)

This example combines a small DDD order flow with Spring Modulith boundary
verification. The order context owns its R2DBC table and exports only an
`OrderAcceptedEvent` named interface. The shipping context reacts to that event
and writes its own reservation table without importing order repositories or
tables.

## What the example proves

- `orders` and `shipping` are separate Spring Modulith modules;
- each context owns its Exposed R2DBC table and repository in an `internal`
  package;
- `orders.events` is the only named interface exported by the order context;
- `shipping` declares `allowedDependencies = ["orders :: events"]`;
- `ApplicationModules.verify()` passes for the valid application;
- a negative fixture that imports `orders.internal.LeakyOrderRepository` fails
  verification with Spring Modulith `Violations`.

The order service persists and commits its aggregate before publishing the
event. The shipping listener hands the event to a coroutine and its own
`suspendTransaction`; this keeps the two bounded contexts independent and does
not pretend that the synchronous Spring Modulith publication SPI is an R2DBC
transaction manager.

## Boundary shape

| Package | Role |
|---|---|
| `orders` | order command, application service, and module metadata |
| `orders.events` | exported `OrderAcceptedEvent` named interface |
| `orders.internal` | private Exposed R2DBC table and repository |
| `shipping` | event listener, reservation model, and module metadata |
| `shipping.internal` | private Exposed R2DBC table and repository |

The root package contains only the Spring Boot entrypoint and R2DBC pool
configuration. Schema initialization stays inside each bounded context, so the
root never depends on module internals.

## Negative fixture

`src/test/kotlin/.../invalid` repeats the module metadata and adds this
forbidden dependency:

~~~kotlin
import exposed.r2dbc.examples.spring.modulith.boundaries.invalid.orders.internal.LeakyOrderRepository
~~~

The test imports that fixture with ArchUnit's
`ImportOption.Predefined.DO_NOT_INCLUDE_JARS` and asserts that Spring Modulith
reports the illegal dependency on `orders.internal`.

## Run

~~~bash
./gradlew :08-ddd-modulith-boundaries:test -PuseDB=H2
~~~

Tests use a local H2 R2DBC pool. No JDBC `DataSource`, Hikari bridge, Docker
service, or external broker is required.

## Tested behavior

- valid modules pass `ApplicationModules.verify()`;
- a direct dependency from `shipping` to `orders.internal` is rejected;
- accepting an order persists `ddd_modulith_orders`;
- the committed domain event is handed off and creates a row in
  `ddd_modulith_shipping_reservations`.
