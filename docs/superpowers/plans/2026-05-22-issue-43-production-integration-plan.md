# Issue #43 Chapter 12 Production Integration 구현 계획

## Target

Deliver Chapter 12 with paired Spring Boot 4 and Ktor Exposed R2DBC examples,
documentation, tests, and verification evidence.

## Steps

1. 추가: Gradle wiring.
   - 추가: `includeModules("12-production-integration", false, false)`.
   - Cross-check the existing `includeModules` signature in
     `settings.gradle.kts` before editing.
   - 추가: Ktor aliases to `gradle/libs.versions.toml`.

2. 구현: Spring module.
   - 생성: build file and package structure.
   - 추가: package slices: `app`, `auth`, `realtime`, `outbound`, and
     `diagnostics`.
   - 추가: Exposed R2DBC schema, repositories, services, WebFlux handlers,
     realtime SSE/WebSocket outbox replay, and readiness diagnostics.
   - 추가: focused repository/service/WebTestClient tests. 유지: app-boundary
     tests on H2 R2DBC; use repository-level dialect matrix only where useful.

3. 구현: Ktor module.
   - 생성: build file and package structure.
   - 추가: matching package slices: `app`, `auth`, `realtime`, `outbound`, and
     `diagnostics`.
   - 추가: matching Exposed R2DBC schema, repositories, services, Ktor routing,
     auth/session, WebSockets, StatusPages, and outbound client boundary.
   - 추가: focused repository/service/`testApplication`/MockEngine tests.
     유지: app-boundary tests on H2 R2DBC; use repository-level dialect matrix
     only where useful.

4. 추가: documentation.
   - 추가: `12-production-integration/README.md`.
   - 추가: `12-production-integration/README.ko.md`.
   - 갱신: root `README.md` and `README.ko.md` learning path/module map.

5. Verify.
   - 실행: IDE diagnostics if available.
   - 실행: `./gradlew projects`.
   - 실행: targeted Chapter 12 tests sequentially, preferably through
     `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test`.
   - 실행: code review gate and resolve P0/P1 findings.

6. Capture durable learning.
   - 추가: `docs/lessons/2026-05-22-issue-43-production-integration.md`.
   - 커밋: all changes with a Lore commit message.
   - 생성: PR for Issue #43 다음 위치 뒤: lessons are committed.

## 검토 메모

Spec and plan are intentionally scoped to two modules. If advisor review finds
that child issues require separate modules for traceability, split only the
directory layout while preserving shared domain vocabulary and README coverage.
