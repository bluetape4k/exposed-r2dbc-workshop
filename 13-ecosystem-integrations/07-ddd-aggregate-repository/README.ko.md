# Exposed R2DBC를 사용하는 DDD Aggregate Repository

[English](README.md) | 한국어

이 예제는 JDBC/DataSource bridge 없이 작은 order aggregate를 구현한다.
Aggregate가 value object, 상태 변경, pending domain event를 소유하고
repository는 `suspendTransaction` 하나로 aggregate 상태, order line, 새로
발생한 event를 저장한다.

## Aggregate 경계

`PurchaseOrder`가 aggregate root다. `OrderNumber`, `CustomerId`, `Sku`,
`OperatorId`는 Kotlin value class이고 `Money`, `OrderLine`은 database call
전에 invariant를 검사한다.

의도적으로 lifecycle을 작게 유지한다.

1. `PurchaseOrder.place(...)`가 `PLACED` order를 만들고
   `OrderPlacedEvent`를 기록한다.
2. `approve(...)`는 placed order에서만 가능하며 `OrderApprovedEvent`를
   기록한다.
3. `OrderRepository.save(...)`가 root를 저장하고 line을 교체하고 snapshot을
   갱신한 뒤 pending event를 원자적으로 추가한다.
4. R2DBC transaction commit 후에만 event를 비운다. event insert 직후 실패하는
   hook으로 rollback 시 row와 pending event가 모두 보존되는지 확인한다.

Root는 bluetape4k snowflake 기반 `IdTable`을 사용한다. Line과 event도
명시적인 snowflake ID를 받아 dialect별 auto-increment DDL 형식에 의존하지
않는다.

## 실행

```bash
./gradlew :07-ddd-aggregate-repository:test -PuseDB=H2
```

Test는 H2 R2DBC pool과 `AbstractR2dbcExposedTest`를 사용한다. JDBC
`DataSource`, Hikari pool, Docker service, 외부 broker가 필요하지 않다.

## 검증하는 동작

- invalid command와 line quantity가 persistence 전에 실패하는지 확인;
- typed aggregate ID와 `DomainEvent<Long>` metadata가 유지되는지 확인;
- placement 및 approval event가 sequence 순서로 기록되는지 확인;
- aggregate 상태, line, event가 함께 commit되는지 확인;
- event 이후 simulated failure가 모든 row를 rollback하고 pending event를
  보존하는지 확인;
- approval command를 두 번 적용할 수 없는지 확인.
