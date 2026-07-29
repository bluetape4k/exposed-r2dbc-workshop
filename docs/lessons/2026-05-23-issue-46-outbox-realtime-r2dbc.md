# Issue #46 Outbox Realtime R2DBC 교훈

## 맥락

Issue #46은 issue #44와 #45에서 잡은 기존 two-module chapter 12 layout을 보존하면서, Spring Boot 4와 Ktor 양쪽에 database-backed realtime notification pattern을 제공해야 했다.

## 결정

Realtime은 기존 Spring/Ktor production module 안의 package-level slice로 유지한다.

- Work item 생성은 같은 Exposed R2DBC `suspendTransaction` 안에서 work row와 realtime outbox row를 함께 저장한다.
- Outbox row는 explicit `PENDING`, `PUBLISHED`, `FAILED` state와 attempt count, last error text를 가진다.
- Spring은 in-process SSE hub를 통해 publish하고, Ktor는 `MutableSharedFlow` WebSocket hub를 통해 publish한다.
- Replay endpoint는 supplied cursor 이후의 `PUBLISHED` row만 반환한다. 따라서 reconnect behavior는 live buffer에만 의존하지 않고 database-backed가 된다.

## 방어선

향후 chapter 12 slice는 module boundary 자체가 lesson이 되지 않는 한 기존 두 module을 계속 확장해야 한다. Realtime 변경은 event persistence before publish, cursor replay boundary, live delivery, failed delivery retention을 계속 test로 보호해야 한다.
