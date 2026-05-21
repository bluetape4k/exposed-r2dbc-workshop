# Issue #43 Production Integration

## Context

Chapter 12 needed paired Spring Boot 4 and Ktor examples for production-grade
Exposed R2DBC service patterns.

## Decision

Use two Gradle modules, one per stack, and keep child issue traceability through
package-level slices: `app`, `auth`, `realtime`, `outbound`, and `diagnostics`.
This avoids ten small modules while preserving Spring/Ktor parity.

## Outcome

Added `12-production-integration` with Spring WebFlux/SSE and Ktor
WebSocket/MockEngine examples. Both stacks persist accounts, work items,
realtime outbox events, outbound idempotency records, and readiness state with
Exposed R2DBC.

## Verification

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test "-PuseDB=H2" --continue --console=plain`
- Spring module: 4 tests passing.
- Ktor module: 5 tests passing.

## Future Guidance

For app-boundary examples, use H2 R2DBC with `DB_CLOSE_DELAY=-1`; otherwise each
connection can see an empty in-memory database. Keep dialect matrix coverage at
repository level unless app bootstrap is explicitly part of the lesson. Register
new end-to-end examples in `.github/workflows/Examples.yml` so the focused
workflow guards workshop adoption separately from the full CI matrix.
