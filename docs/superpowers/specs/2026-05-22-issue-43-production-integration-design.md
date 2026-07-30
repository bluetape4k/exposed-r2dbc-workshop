# Issue #43 Chapter 12 Production Integration 설계

## 맥락

Issue #43 asks for a new `12-production-integration` chapter that compares
production-grade Exposed R2DBC service patterns in Spring Boot 4 and Ktor.
Child issues #44-#49 cover application architecture, authentication/session,
realtime outbox delivery, outbound HTTP idempotency, observability/readiness,
and documentation/verification wiring.

GNO preflight found no prior Issue #43 design artifact. Existing workshop
structure uses chapter directories with leaf Gradle module names and keeps
paired English/Korean README files for discoverability.

## Decision

Create two paired modules instead of ten topic-specific Gradle modules:

- `12-production-integration/01-spring-production-integration`
- `12-production-integration/02-ktor-production-integration`

Each module covers all five production topics with the same domain vocabulary
and database table shape. This keeps the chapter easy to scan, minimizes Gradle
and CI churn, and still satisfies the parent issue requirement that each topic
has Spring Boot 4 and Ktor R2DBC coverage.

Child issues are mapped to package-level slices inside both modules so each
child remains traceable without multiplying Gradle projects:

| Issue | Spring package slice | Ktor package slice | Coverage |
|---|---|---|---|
| #44 | `app` | `app` | service structure, validation, JSON, structured errors |
| #45 | `auth` | `auth` | credentials, permissions, session/token metadata |
| #46 | `realtime` | `realtime` | persisted outbox, replay cursor, Spring SSE/WebFlux or WebSocket, Ktor WebSockets |
| #47 | `outbound` | `outbound` | HTTP outbox, retry state, idempotency key duplicate handling |
| #48 | `diagnostics` | `diagnostics` | readiness, degraded state, correlation, slow-operation metadata |
| #49 | chapter README/root README/test evidence | chapter README/root README/test evidence | discoverability and verification wiring |

PR follow-up comments should explain this split on child issues #44-#49 before
closing them.

## 범위

- 추가: Chapter 12 modules to `settings.gradle.kts`.
- 추가: Ktor dependencies to `gradle/libs.versions.toml`.
- Implement a shared production-workflow domain per stack:
  - request validation and structured errors,
  - credential/session persistence,
  - domain event outbox and replay boundaries,
  - outbound request outbox with idempotency keys,
  - readiness/degraded diagnostics.
- 사용: Exposed R2DBC repositories with `suspendTransaction`.
- 추가: focused tests for service/repository behavior and the stack boundary:
  - Spring: WebFlux/WebTestClient focused coverage where lightweight.
  - Ktor: `testApplication`, WebSockets, and MockEngine where useful.
- 추가: `12-production-integration/README.md` and `README.ko.md`.
- Update root `README.md` and `README.ko.md` module maps.
- 추가: a concise lesson after implementation.

## 비목표

- No real external service calls.
- No production authentication provider integration.
- No new shared framework abstraction across Spring and Ktor modules.
- No Testcontainers-backed parallel Gradle runs; targeted module tests run in a
  single/sequential invocation.

## API And Data Model

Both modules expose the same conceptual operations:

- Register/find account credentials and permissions.
- Create validated work items.
- Persist outbox events before delivery.
- Persist outbound requests before client dispatch.
- Reserve idempotency keys and reject duplicates. Duplicate idempotency keys
  return HTTP 409 with a structured conflict error in both stacks; replaying a
  stored success response is out of scope for this chapter.
- Report readiness as `UP`, `DEGRADED`, or `DOWN`.

Tables are local to each module and prefixed by the module package context to
avoid collisions. DTOs and data classes implement `Serializable` with
`serialVersionUID` where applicable.

## Stack Coverage

Spring Boot 4 module:

- WebFlux routes/controllers for work item and readiness examples.
- Spring Security-shaped service boundary, kept lightweight for workshop focus.
- Spring realtime endpoint for #46 using SSE (`ServerSentEvent`/`Flux`) backed
  by the persisted outbox. Tests must cover event persistence and replay from a
  cursor. WebFlux WebSocket parity is an optional follow-up, not the default
  implementation path.
- Spring Boot Actuator/readiness style diagnostics.
- Spring WebTestClient tests.

Ktor module:

- Ktor Netty server module with routing, StatusPages, Authentication/Sessions,
  WebSockets, and JSON serialization.
- Ktor HTTP client MockEngine for outbound idempotency tests.
- `testApplication` tests for HTTP and WebSocket behavior.

Test database strategy:

- App-boundary tests run against H2 R2DBC only to keep Spring Boot and Ktor
  application bootstrap predictable and fast.
- Repository-level tests may use the existing `TestDB` matrix where the table
  and transaction behavior is dialect-sensitive.
- If a future change requires full app-boundary dialect coverage, Spring must
  inject per-dialect R2DBC URLs with `@DynamicPropertySource`, and Ktor must
  inject equivalent configuration through `MapApplicationConfig`.

Ktor official docs checked on 2026-05-22:

- Server dependencies use `ktor-server-core-jvm` and `ktor-server-netty-jvm`.
- WebSocket examples use `install(WebSockets)` and server test clients with the
  WebSockets client plugin.
- Client testing uses `ktor-client-mock` / MockEngine.

## 수용 기준

- `./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test`
  passes.
- `./gradlew projects` shows both Chapter 12 modules.
- Test evidence is captured with `repo-test-summary -- ./gradlew ...` when
  available.
- README pairs explain Spring Boot 4 vs Ktor tradeoffs and verification scope.
- Root README pairs list Chapter 12.
- `docs/lessons/2026-05-22-issue-43-production-integration.md` exists.
- Review gate records P0/P1 convergence.

## Risks

- Ktor is new to this repository, so dependency wiring may reveal missing
  catalog aliases. Keep dependencies explicit and version-catalog based.
- Spring Boot 4 and Ktor examples can drift if duplicated too mechanically.
  Keep the domain vocabulary aligned but use stack-native boundary tests.
- Full end-to-end WebSocket/database integration can become oversized. Keep the
  example focused on persisted outbox and replay semantics, not full delivery
  infrastructure.
