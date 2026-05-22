# Ktor Production Integration

[English](README.md) | [한국어](README.ko.md)

이 모듈은 12장의 Ktor 3 예제입니다. Issue #44에서는 기본 application
architecture를 추가합니다: routing, JSON serialization, `StatusPages` error
mapping, session-protected route, Exposed R2DBC repository, focused
application test.

## 아키텍처

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## 패키지 구성

```text
exposed.r2dbc.examples.production.ktor
├── app         # DTO, validation, Exposed R2DBC repository
├── config      # JSON serialization과 structured error mapping
├── outbound    # Ktor HTTP client dispatcher 경계
├── persistence # repository 경계가 소유하는 table 정의
└── routes      # HTTP route와 WebSocket replay endpoint
```

## Ktor vs Spring Boot 4

| 관심사 | Ktor 모듈 | Spring Boot 4 쌍 |
|---|---|---|
| HTTP 경계 | 명시적인 routing DSL | Annotation 기반 WebFlux controller |
| JSON/error mapping | `ContentNegotiation`과 `StatusPages` | Boot JSON 지원과 `@RestControllerAdvice` |
| Session/auth 형태 | Session plugin으로 보호 route 구성 | 이 slice에서는 header 기반 workshop permission check |
| R2DBC 경계 | Repository가 `suspendTransaction` 호출을 소유 | Spring service 뒤에서도 같은 repository 형태 |
| Test 방식 | `testApplication` + Ktor client plugin | `@SpringBootTest` + `WebTestClient` |

## 검증

```bash
repo-test-summary -- ./gradlew :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```
