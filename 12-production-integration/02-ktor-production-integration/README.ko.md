# Ktor Production Integration

[English](README.md) | [한국어](README.ko.md)

이 모듈은 12장의 Ktor 3 예제입니다. Issue #44의 application architecture
baseline, issue #45의 authentication/session slice, issue #46의 realtime
outbox slice를 포함합니다.

## 아키텍처

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## 인증과 세션

![Chapter 12 authentication and session metadata](../../docs/assets/readme-diagrams/issue-45-auth-session-r2dbc-01.png)

Ktor slice는 Basic authentication으로 database-backed session metadata를
생성하고, signed `production_session` cookie에는 opaque token만 저장합니다.
Password는 BCrypt hash로 저장하고, session row에는 SHA-256 token hash만
persist합니다. Public account registration은 항상 `work:create` permission과
`USER` role만 부여하며, 이 workshop slice에서 admin/outbound account는 seeded
`admin`뿐입니다.

Cookie는 signed cookie이며 `HttpOnly`와 `SameSite=Lax`를 사용합니다. 예제는
local plain HTTP로 실행되므로 `Secure` cookie와 restart-stable signing key는
default로 켜지지 않고 production hardening note로 둡니다.

## Realtime Outbox

![Chapter 12 realtime outbox delivery](../../docs/assets/readme-diagrams/issue-46-outbox-realtime-r2dbc-01.png)

Ktor realtime slice는 signed session cookie로 work, publish, outbox,
WebSocket endpoint를 보호합니다. Work item 생성은 같은 R2DBC transaction에서
`PENDING` outbox row를 저장하고, publish attempt는 `MutableSharedFlow` hub로
전달합니다. Replay는 cursor 이후의 `PUBLISHED` row만 반환하며, delivery 실패는
`FAILED` 상태로 계속 조회됩니다.

## 패키지 구성

```text
exposed.r2dbc.examples.production.ktor
├── app         # DTO, validation, Exposed R2DBC repository
├── config      # JSON serialization, structured error mapping, auth failure
├── outbound    # Ktor HTTP client dispatcher 경계
├── persistence # repository 경계가 소유하는 table 정의
└── routes      # HTTP route와 WebSocket replay endpoint
```

## Ktor vs Spring Boot 4

| 관심사 | Ktor 모듈 | Spring Boot 4 쌍 |
|---|---|---|
| HTTP 경계 | 명시적인 routing DSL | Annotation 기반 WebFlux controller |
| JSON/error mapping | `ContentNegotiation`과 `StatusPages` | Boot JSON 지원과 `@RestControllerAdvice` |
| Session/auth 형태 | Ktor Basic auth가 signed-cookie session metadata 생성 | WebFlux Security Basic auth와 DB session metadata |
| Realtime delivery | Persisted publish 상태가 있는 WebSocket replay/live stream | 같은 outbox 상태를 쓰는 Server-Sent Events |
| R2DBC 경계 | Repository가 `suspendTransaction` 호출을 소유 | Spring service 뒤에서도 같은 repository 형태 |
| Test 방식 | `testApplication` + Ktor client plugin | `@SpringBootTest` + `WebTestClient` |

## 검증

```bash
repo-test-summary -- ./gradlew :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

Test suite는 authorized access, missing credentials, invalid credentials,
non-admin role denial, public registration permission/role clamping, invalid
session cookie, raw token을 숨기는 session listing, event persistence, publish
state transition, replay/live WebSocket delivery, delivery failure retention을
검증합니다.
