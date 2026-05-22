# Spring Production Integration

[English](README.md) | [한국어](README.ko.md)

This module is the Spring Boot 4 WebFlux side of chapter 12. Issue #44 adds the
baseline application architecture: HTTP controller, structured error mapping,
service boundary, Exposed R2DBC repository, and focused application tests.

## Architecture

![Chapter 12 production application architecture](../../docs/assets/readme-diagrams/issue-44-production-architecture-r2dbc-01.png)

## Package Layout

```text
exposed.r2dbc.examples.production.spring
├── app         # DTOs, service boundary, validation, Exposed R2DBC repository
├── persistence # table definitions owned by the repository boundary
└── web         # WebFlux controller, SSE endpoint, structured error mapping
```

## Spring Boot 4 vs Ktor

| Concern | Spring Boot 4 module | Ktor pair |
|---|---|---|
| HTTP boundary | Annotation-driven WebFlux controller | Explicit Ktor routing DSL |
| JSON/error mapping | Boot JSON support plus `@RestControllerAdvice` | `ContentNegotiation` plus `StatusPages` |
| Session/auth shape | Header-based workshop permission check for this slice | Session plugin guards protected routes |
| R2DBC boundary | Repository-owned `suspendTransaction` calls | Same repository shape under Ktor routes |
| Test style | `@SpringBootTest` + `WebTestClient` | `testApplication` + Ktor client plugins |

## Verification

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test -PuseDB=H2 --continue --console=plain
```
