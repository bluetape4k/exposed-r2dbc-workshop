# 12장: Production Integration

[English](README.md) | [한국어](README.ko.md)

12장은 Spring Boot 4와 Ktor에서 production-grade Exposed R2DBC 서비스 경계를
나란히 비교합니다. 두 스택의 도메인 용어는 맞추되, HTTP, realtime, client,
diagnostics 경계는 각 프레임워크의 자연스러운 방식으로 보여줍니다.

## 모듈

| 모듈 | 스택 | 초점 |
|---|---|---|
| [`01-spring-production-integration`](01-spring-production-integration/) | Spring Boot 4 WebFlux | WebFlux Security, controller/service/repository 경계, SSE replay, structured errors, readiness |
| [`02-ktor-production-integration`](02-ktor-production-integration/) | Ktor 3 | Ktor Authentication/Sessions, WebSockets, MockEngine outbound dispatch, readiness |

## Application Architecture

![Chapter 12 production application architecture](../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## Authentication And Sessions

![Chapter 12 authentication and session metadata](../docs/assets/readme-diagrams/issue-45-auth-session-r2dbc-01.png)

## Realtime Outbox Delivery

![Chapter 12 realtime outbox delivery](../docs/assets/readme-diagrams/issue-46-outbox-realtime-r2dbc-01.png)

Realtime slice는 accepted work item과 outbox event를 같은 Exposed R2DBC
transaction에 저장합니다. Pending row publish는 Spring SSE 또는 Ktor
WebSocket delivery 경계가 event를 받아들인 뒤에만 `PUBLISHED`로 전환합니다.
Delivery 실패는 attempt count와 error note를 포함한 `FAILED` 상태로 저장하므로
reconnect/replay는 in-memory event에만 의존하지 않습니다.

## 이슈별 주제 맵

| Issue | 주제 | Spring slice | Ktor slice |
|---|---|---|---|
| #44 | Application architecture | `app` | `app` |
| #45 | Authentication/session | `auth` | `auth` |
| #46 | Realtime outbox | SSE replay 기반 `realtime` | WebSocket 기반 `realtime` |
| #47 | HTTP client outbox/idempotency | `outbound` | `outbound` |
| #48 | Observability/readiness | `diagnostics` | `diagnostics` |
| #49 | Documentation and verification | README + Gradle/test evidence | README + Gradle/test evidence |

## Production 계약

- Database access는 Exposed R2DBC `suspendTransaction` 안에서 실행합니다.
- Password는 BCrypt hash로 저장하고, session table은 SHA-256 token hash만
  저장합니다. Raw session token은 session 생성 시점에만 반환합니다.
- Public registration은 `work:create` permission과 `USER` role만 부여합니다.
  Admin/outbound access는 seeded admin account에서만 오므로 auth slice에서
  privileged user를 self-register할 수 없습니다.
- App-boundary test는 Spring/Ktor bootstrap을 예측 가능하게 유지하기 위해 H2
  R2DBC를 사용합니다.
- 중복 idempotency key는 두 스택 모두 HTTP 409와 structured conflict error를
  반환합니다.
- Realtime 예제는 delivery 전에 event를 outbox에 저장하고, pending row만
  publish하며, 실패 상태를 기록하고, cursor 이후 `PUBLISHED` row만 replay합니다.
- Readiness는 database 접근 가능 시 `UP`, diagnostics table에 degraded 상태가
  기록되면 `DEGRADED`를 반환합니다.

## 검증

```bash
./gradlew projects
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test
```
