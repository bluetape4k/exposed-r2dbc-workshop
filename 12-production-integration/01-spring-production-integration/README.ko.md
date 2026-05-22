# Spring Production Integration

[English](README.md) | [한국어](README.ko.md)

이 모듈은 12장의 Spring Boot 4 WebFlux 예제입니다. Issue #44에서는 기본
application architecture를 추가합니다: HTTP controller, structured error
mapping, service boundary, Exposed R2DBC repository, focused application test.

## 아키텍처

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## 패키지 구성

```text
exposed.r2dbc.examples.production.spring
├── app         # DTO, service boundary, validation, Exposed R2DBC repository
├── persistence # repository 경계가 소유하는 table 정의
└── web         # WebFlux controller, SSE endpoint, structured error mapping
```

## Spring Boot 4 vs Ktor

| 관심사 | Spring Boot 4 모듈 | Ktor 쌍 |
|---|---|---|
| HTTP 경계 | Annotation 기반 WebFlux controller | 명시적인 Ktor routing DSL |
| JSON/error mapping | Boot JSON 지원과 `@RestControllerAdvice` | `ContentNegotiation`과 `StatusPages` |
| Session/auth 형태 | 이 slice에서는 header 기반 workshop permission check | Session plugin으로 보호 route 구성 |
| R2DBC 경계 | Repository가 `suspendTransaction` 호출을 소유 | Ktor route 아래에서도 같은 repository 형태 |
| Test 방식 | `@SpringBootTest` + `WebTestClient` | `testApplication` + Ktor client plugin |

## 검증

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test -PuseDB=H2 --continue --console=plain
```
