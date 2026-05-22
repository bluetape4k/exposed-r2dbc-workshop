# Chapter 12: Production Integration

[English](README.md) | [한국어](README.ko.md)

Chapter 12 compares production-oriented Exposed R2DBC service boundaries in
Spring Boot 4 and Ktor. The chapter keeps the domain vocabulary aligned across
both stacks while showing the native HTTP, realtime, client, and diagnostics
surfaces each framework expects.

## Modules

| Module | Stack | Focus |
|---|---|---|
| [`01-spring-production-integration`](01-spring-production-integration/) | Spring Boot 4 WebFlux | WebFlux Security, controller/service/repository boundaries, SSE replay, WebClient outbox dispatch, structured errors, readiness |
| [`02-ktor-production-integration`](02-ktor-production-integration/) | Ktor 3 | Ktor Authentication/Sessions, WebSockets, MockEngine/Ktor HTTP client outbox dispatch, readiness |

## Application Architecture

![Chapter 12 production application architecture](../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## Authentication And Sessions

![Chapter 12 authentication and session metadata](../docs/assets/readme-diagrams/issue-45-auth-session-r2dbc-01.png)

## Realtime Outbox Delivery

![Chapter 12 realtime outbox delivery](../docs/assets/readme-diagrams/issue-46-outbox-realtime-r2dbc-01.png)

The realtime slice persists each accepted work item and outbox event in the
same Exposed R2DBC transaction. Publishing pending rows moves events to
`PUBLISHED` only after the Spring SSE or Ktor WebSocket delivery boundary
accepts them. Failed delivery is stored as `FAILED` with an attempt count and
error note, so reconnect/replay never depends on an in-memory event alone.

## HTTP Client Outbox And Idempotency

![Chapter 12 HTTP client outbox and idempotency](../docs/assets/readme-diagrams/issue-47-http-outbox-idempotency-r2dbc-01.png)

The outbound slice persists each external HTTP request before dispatch and
uses a database-unique idempotency key as the duplicate boundary. Dispatch
claims eligible rows as `IN_FLIGHT` in a short R2DBC transaction, performs the
Spring WebClient or Ktor HTTP client call outside the transaction, then records
`SUCCEEDED`, `RETRYABLE_FAILED`, or `PERMANENT_FAILED` with attempt count,
status code, and sanitized error text. Tests use replaceable dispatchers and
Ktor `MockEngine`, so success, retry, duplicate, permanent failure, and
concurrent single-send behavior are covered without a real external service.

## Topic Map

| Issue | Topic | Spring slice | Ktor slice |
|---|---|---|---|
| #44 | Application architecture | `app` | `app` |
| #45 | Authentication/session | `auth` | `auth` |
| #46 | Realtime outbox | `realtime` with SSE replay | `realtime` with WebSockets |
| #47 | HTTP client outbox/idempotency | `outbound` | `outbound` |
| #48 | Observability/readiness | `diagnostics` | `diagnostics` |
| #49 | Documentation and verification | README + Gradle/test evidence | README + Gradle/test evidence |

## Production Contracts

- Database access runs through Exposed R2DBC `suspendTransaction`.
- Passwords are stored as BCrypt hashes, and session tables store only SHA-256
  token hashes. Raw session tokens are returned only when a session is created.
- Public registration permission/role clamping grants only `work:create` and
  `USER`; admin/outbound access comes from the seeded admin account so the auth
  slice cannot self-register privileged users.
- App-boundary tests use H2 R2DBC for predictable Spring/Ktor startup.
- Duplicate idempotency keys return HTTP 409 with a structured conflict error.
- Realtime examples persist events before delivery, publish only pending rows,
  record failed delivery state, and replay only `PUBLISHED` rows after a cursor.
- HTTP client outbox examples persist outbound rows before delivery, claim
  retryable rows before dispatch, cap retries at three attempts, and redact
  credential-like text from stored dispatch errors.
- Readiness reports `UP` when the database is reachable and `DEGRADED` when the
  diagnostics table records a degraded database state.

## Verification

```bash
./gradlew projects
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test
```
