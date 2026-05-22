# Spring Production Integration

[English](README.md) | [한국어](README.ko.md)

이 모듈은 12장의 Spring Boot 4 WebFlux 예제입니다. Issue #44의 application
architecture baseline에 더해 issue #45의 authentication/session slice를
포함합니다.

## 아키텍처

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## 인증과 세션

![Chapter 12 authentication and session metadata](../../docs/assets/readme-diagrams/issue-45-auth-session-r2dbc-01.png)

Spring slice는 Exposed R2DBC account row로 backed되는 WebFlux Security HTTP
Basic 인증을 사용합니다. Password는 BCrypt hash로 저장하고, session metadata는
SHA-256 token hash만 저장합니다. Raw opaque token은 session 생성 응답에서만
반환합니다. Public account registration은 항상 `work:create` permission과
`USER` role만 부여하며, 이 workshop slice에서 admin/outbound account는 seeded
`admin`뿐입니다.

Spring에서는 Basic authentication이 authoritative transport입니다. Persisted
session row는 Ktor cookie flow와 비교하기 위한 session metadata이며, 별도의
authentication filter 대체물이 아닙니다.

## 패키지 구성

```text
exposed.r2dbc.examples.production.spring
├── auth        # WebFlux Security와 repository-backed user details
├── app         # DTO, service boundary, validation, Exposed R2DBC repository
├── persistence # repository 경계가 소유하는 table 정의
└── web         # WebFlux controller, SSE endpoint, structured error mapping
```

## Spring Boot 4 vs Ktor

| 관심사 | Spring Boot 4 모듈 | Ktor 쌍 |
|---|---|---|
| HTTP 경계 | Annotation 기반 WebFlux controller | 명시적인 Ktor routing DSL |
| JSON/error mapping | Boot JSON 지원과 `@RestControllerAdvice` | `ContentNegotiation`과 `StatusPages` |
| Session/auth 형태 | WebFlux Security Basic auth와 DB session metadata | Ktor Basic auth가 signed-cookie session metadata 생성 |
| R2DBC 경계 | Repository가 `suspendTransaction` 호출을 소유 | Ktor route 아래에서도 같은 repository 형태 |
| Test 방식 | `@SpringBootTest` + `WebTestClient` | `testApplication` + Ktor client plugin |

## 검증

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test -PuseDB=H2 --continue --console=plain
```

Test suite는 authorized access, missing credentials, invalid credentials,
non-admin role denial, public registration permission/role clamping, raw token을
숨기는 session listing을 검증합니다.
