# Spring Production Integration

[English](README.md) | [한국어](README.ko.md)

This module is the Spring Boot 4 WebFlux side of chapter 12. It now includes
the application architecture baseline from issue #44 and the authentication /
session slice from issue #45.

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

## Package Layout

```text
exposed.r2dbc.examples.production.spring
├── auth        # WebFlux Security and repository-backed user details
├── app         # DTOs, service boundary, validation, Exposed R2DBC repository
├── persistence # table definitions owned by the repository boundary
└── web         # WebFlux controller, SSE endpoint, structured error mapping
```

## Spring Boot 4 vs Ktor

| Concern | Spring Boot 4 module | Ktor pair |
|---|---|---|
| HTTP boundary | Annotation-driven WebFlux controller | Explicit Ktor routing DSL |
| JSON/error mapping | Boot JSON support plus `@RestControllerAdvice` | `ContentNegotiation` plus `StatusPages` |
| Session/auth shape | WebFlux Security Basic auth plus DB session metadata | Ktor Basic auth creates signed-cookie session metadata |
| R2DBC boundary | Repository-owned `suspendTransaction` calls | Same repository shape under Ktor routes |
| Test style | `@SpringBootTest` + `WebTestClient` | `testApplication` + Ktor client plugins |

## Verification

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test -PuseDB=H2 --continue --console=plain
```

The test suite covers authorized access, missing credentials, invalid
credentials, non-admin role denial, public registration permission/role
clamping, and session listings that hide raw tokens.
