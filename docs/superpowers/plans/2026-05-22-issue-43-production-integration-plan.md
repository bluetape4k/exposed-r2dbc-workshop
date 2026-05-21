# Issue #43 Chapter 12 Production Integration Plan

## Target

Deliver Chapter 12 with paired Spring Boot 4 and Ktor Exposed R2DBC examples,
documentation, tests, and verification evidence.

## Steps

1. Add Gradle wiring.
   - Add `includeModules("12-production-integration", false, false)`.
   - Cross-check the existing `includeModules` signature in
     `settings.gradle.kts` before editing.
   - Add Ktor aliases to `gradle/libs.versions.toml`.

2. Implement Spring module.
   - Create build file and package structure.
   - Add package slices: `app`, `auth`, `realtime`, `outbound`, and
     `diagnostics`.
   - Add Exposed R2DBC schema, repositories, services, WebFlux handlers,
     realtime SSE/WebSocket outbox replay, and readiness diagnostics.
   - Add focused repository/service/WebTestClient tests. Keep app-boundary
     tests on H2 R2DBC; use repository-level dialect matrix only where useful.

3. Implement Ktor module.
   - Create build file and package structure.
   - Add matching package slices: `app`, `auth`, `realtime`, `outbound`, and
     `diagnostics`.
   - Add matching Exposed R2DBC schema, repositories, services, Ktor routing,
     auth/session, WebSockets, StatusPages, and outbound client boundary.
   - Add focused repository/service/`testApplication`/MockEngine tests.
     Keep app-boundary tests on H2 R2DBC; use repository-level dialect matrix
     only where useful.

4. Add documentation.
   - Add `12-production-integration/README.md`.
   - Add `12-production-integration/README.ko.md`.
   - Update root `README.md` and `README.ko.md` learning path/module map.

5. Verify.
   - Run IDE diagnostics if available.
   - Run `./gradlew projects`.
   - Run targeted Chapter 12 tests sequentially, preferably through
     `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test`.
   - Run code review gate and resolve P0/P1 findings.

6. Capture durable learning.
   - Add `docs/lessons/2026-05-22-issue-43-production-integration.md`.
   - Commit all changes with a Lore commit message.
   - Create PR for Issue #43 after lessons are committed.

## Review Notes

Spec and plan are intentionally scoped to two modules. If advisor review finds
that child issues require separate modules for traceability, split only the
directory layout while preserving shared domain vocabulary and README coverage.
