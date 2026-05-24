# Spring Production Integration

[English](README.md) | [한국어](README.ko.md)

This module is the Spring Boot 4 WebFlux side of chapter 12. It now includes
the application architecture baseline from issue #44, the authentication /
session slice from issue #45, the realtime outbox slice from issue #46, and
the HTTP client outbox/idempotency slice from issue #47, and the diagnostics /
readiness slice from issue #48.

## Architecture

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## Authentication And Sessions

![Chapter 12 authentication and session metadata](../../docs/assets/readme-diagrams/issue-45-auth-session-r2dbc-01.png)

The Spring slice uses WebFlux Security with HTTP Basic authentication backed by
Exposed R2DBC account rows. Passwords are stored as BCrypt hashes. Session
metadata stores only SHA-256 token hashes; the raw opaque token is returned only
when a session is created. Public account registration always grants the
`work:create` permission and `USER` role; the seeded `admin` account is the only
admin/outbound account in this workshop slice.

Spring keeps Basic authentication as the authoritative transport. The persisted
session rows are session metadata for comparison with the Ktor cookie flow, not
a replacement authentication filter.

## Realtime Outbox

![Chapter 12 realtime outbox delivery](../../docs/assets/readme-diagrams/issue-46-outbox-realtime-r2dbc-01.png)

The Spring realtime slice accepts work through the existing API-key permission
boundary, stores the work item and `PENDING` outbox row in one R2DBC
transaction, then publishes pending rows through an in-process SSE hub. Replay
returns only `PUBLISHED` rows after the requested sequence, and failed delivery
is retained as `FAILED` with an attempt count and error text.

## HTTP Client Outbox

![Chapter 12 HTTP client outbox and idempotency](../../docs/assets/readme-diagrams/issue-47-http-outbox-idempotency-r2dbc-01.png)

The Spring outbound slice accepts `POST /production/outbound` only from an
account with `outbound:create`, persists the target URL, payload, and unique
idempotency key, then dispatches pending work through `POST
/production/outbound/dispatch`. `SpringOutboundDispatcher` uses WebFlux
`WebClient` and forwards the idempotency key as the `Idempotency-Key` header.
The repository claims dispatchable rows as `IN_FLIGHT` before calling the HTTP
client, runs the network call outside the transaction, caps retryable failures
at three attempts, and stores sanitized error text.

## Observability And Readiness

![Chapter 12 observability and readiness](../../docs/assets/readme-diagrams/issue-48-observability-readiness-r2dbc-01.png)

`SpringRequestCorrelationFilter` normalizes `X-Request-ID`, echoes the accepted
value on every response, and makes it available to structured error mapping.
`GET /production/diagnostics/operations/{name}` records a bounded diagnostic
operation with optional coroutine delay outside the R2DBC transaction, while
`GET /production/diagnostics/operations` returns the newest 100 rows. `GET
/production/readiness` runs a bounded live database ping and reports sticky
`DEGRADED` state when the diagnostics table marks the database as degraded.

## Package Layout

![Spring production package layout](../../docs/assets/readme-diagrams/12-production-integration-01-spring-package-layout-01.png)

## Spring Boot 4 vs Ktor

| Concern | Spring Boot 4 module | Ktor pair |
|---|---|---|
| HTTP boundary | Annotation-driven WebFlux controller | Explicit Ktor routing DSL |
| JSON/error mapping | Boot JSON support plus `@RestControllerAdvice` | `ContentNegotiation` plus `StatusPages` |
| Session/auth shape | WebFlux Security Basic auth plus DB session metadata | Ktor Basic auth creates signed-cookie session metadata |
| Realtime delivery | Server-Sent Events with persisted publish state | WebSocket replay/live stream with the same outbox state |
| Outbound HTTP | WebClient dispatcher with persisted idempotency state | Ktor HTTP client dispatcher with MockEngine tests |
| Diagnostics/readiness | WebFilter request correlation plus annotated diagnostics endpoints | CallId/CallLogging plus explicit route diagnostics |
| R2DBC boundary | Repository-owned `suspendTransaction` calls | Same repository shape under Ktor routes |
| Test style | `@SpringBootTest` + `WebTestClient` | `testApplication` + Ktor client plugins |

## Verification

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test -PuseDB=H2 --continue --console=plain
```

The test suite covers authorized access, missing credentials, invalid
credentials, non-admin role denial, public registration permission/role
clamping, session listings that hide raw tokens, event persistence, publish
state transitions, replay boundaries, delivery failure retention, outbound
success/retry/permanent failure, duplicate idempotency keys, permission denial,
sanitized dispatch errors, concurrent single-send protection, request-id echo
and replacement, structured error correlation, diagnostic operation timing, and
readiness degraded/recovery behavior.
