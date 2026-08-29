# Spring Modulith Custom R2DBC Publication Log

English | [Korean](README.ko.md)

This example demonstrates a Spring Modulith-shaped order/fulfillment boundary
without claiming that Spring Modulith's native event publication registry is
reactive.

## Why this is custom

Spring Modulith's `EventPublicationRepository` is a synchronous SPI returning
`List`, `Optional`, and `void`. The official persistence modules are JDBC, JPA,
MongoDB, and Neo4j; there is no official R2DBC publication repository. This
module therefore does not depend on `bluetape4k-exposed-spring-modulith` or a
native event registry.

Instead, the order transaction writes both the order and a small
`modulith_publication_log` row through Exposed R2DBC. A coroutine dispatcher in
the fulfillment module reads outstanding rows, invokes the handler, and marks
the row `COMPLETED` or `FAILED`. The dispatcher reprocesses failed rows and
increments `attempts`.

## Module boundary

`orders.events` is the only named interface exported from the orders module.
The fulfillment module declares `allowedDependencies = ["orders :: events"]`.
`ApplicationModules.verify()` checks this metadata while all persistence code
uses `suspendTransaction` and intentional `Flow` collection.

## Run

```bash
./gradlew :06-spring-modulith-publications:test -PuseDB=H2
```

Tests use a local H2 R2DBC pool and do not require a JDBC `DataSource`, Docker,
or an external event broker.

## Tested behavior

- order and publication-log writes are committed in one R2DBC transaction;
- the fulfillment dispatcher completes an outstanding publication;
- a simulated downstream failure remains inspectable and is retried;
- Spring Modulith module metadata verifies the public event boundary.

For the native registry contract, see the [Spring Modulith events
reference](https://docs.spring.io/spring-modulith/reference/events.html) and the
[EventPublicationRepository API](https://docs.spring.io/spring-modulith/docs/2.1.1/api/org/springframework/modulith/events/core/EventPublicationRepository.html).
