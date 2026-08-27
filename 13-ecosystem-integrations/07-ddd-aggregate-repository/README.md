# DDD Aggregate Repository with Exposed R2DBC

English | [한국어](README.ko.md)

This example implements a small order aggregate without introducing a
JDBC/DataSource bridge. The aggregate owns its value objects, state changes,
and pending domain events; the repository persists the aggregate state, order
lines, and newly raised events in one `suspendTransaction`.

## Aggregate boundary

`PurchaseOrder` is the aggregate root. `OrderNumber`, `CustomerId`, `Sku`, and
`OperatorId` are Kotlin value classes, while `Money` and `OrderLine` enforce
invariants before a database call is made.

The lifecycle is deliberately small:

1. `PurchaseOrder.place(...)` creates a `PLACED` order and records
   `OrderPlacedEvent`.
2. `approve(...)` is valid only for a placed order and records
   `OrderApprovedEvent`.
3. `OrderRepository.save(...)` writes the root, replaces its lines, updates the
   snapshot, and appends pending events atomically.
4. Events are cleared only after the R2DBC transaction commits. A failure hook
   after event insertion demonstrates that rollback leaves both rows and the
   pending event intact.

The root uses a bluetape4k snowflake-backed `IdTable`; line and event records
also receive explicit snowflake IDs so the example does not depend on a
dialect-specific auto-increment DDL form.

## Run

```bash
./gradlew :07-ddd-aggregate-repository:test -PuseDB=H2
```

Tests use an H2 R2DBC pool and `AbstractR2dbcExposedTest`. No JDBC
`DataSource`, Hikari pool, Docker service, or external broker is required.

## Tested behavior

- invalid commands and line quantities fail before persistence;
- typed aggregate IDs and `DomainEvent<Long>` metadata are retained;
- placement and approval events are captured in sequence order;
- aggregate state, lines, and events are committed together;
- a simulated post-event failure rolls back every row and preserves the
  pending event;
- an approval command cannot be applied twice.
