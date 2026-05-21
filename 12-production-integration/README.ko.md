# 12장: Production Integration

[English](README.md) | [한국어](README.ko.md)

12장은 Spring Boot 4와 Ktor에서 production-grade Exposed R2DBC 서비스 경계를
나란히 비교합니다. 두 스택의 도메인 용어는 맞추되, HTTP, realtime, client,
diagnostics 경계는 각 프레임워크의 자연스러운 방식으로 보여줍니다.

## 모듈

| 모듈 | 스택 | 초점 |
|---|---|---|
| [`01-spring-production-integration`](01-spring-production-integration/) | Spring Boot 4 WebFlux | Controller/service/repository 경계, SSE replay, structured errors, readiness |
| [`02-ktor-production-integration`](02-ktor-production-integration/) | Ktor 3 | Routing, sessions/authentication, WebSockets, MockEngine outbound dispatch, readiness |

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
- App-boundary test는 Spring/Ktor bootstrap을 예측 가능하게 유지하기 위해 H2
  R2DBC를 사용합니다.
- 중복 idempotency key는 두 스택 모두 HTTP 409와 structured conflict error를
  반환합니다.
- Realtime 예제는 delivery 전에 event를 outbox에 저장하고 cursor replay를
  지원합니다.
- Readiness는 database 접근 가능 시 `UP`, diagnostics table에 degraded 상태가
  기록되면 `DEGRADED`를 반환합니다.

## 검증

```bash
./gradlew projects
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test
```
