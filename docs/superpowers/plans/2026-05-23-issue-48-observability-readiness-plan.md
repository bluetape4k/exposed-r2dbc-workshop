# Issue #48 Observability Readiness Plan

## Tasks

1. Add Spring and Ktor diagnostics models for operation records, operation
   collections, readiness details, and structured errors with request ids.
2. Extend Spring and Ktor R2DBC tables with separate diagnostic operation rows
   (`id`, `name`, `requestId`, `durationMs`, `slow`, `createdAtEpochMs`) and
   keep the existing diagnostics status row for sticky degraded database state.
3. Extend repositories with coroutine-safe `recordDiagnosticOperation`,
   `diagnosticOperations`, `pingDatabase`, `markDatabaseDegraded`, and
   `clearDatabaseDegraded` helpers. Keep all DB access in `suspendTransaction`.
   Bound live ping with a one-second coroutine timeout, map timeout/SQL/Exposed
   failures to degraded readiness, and rethrow `CancellationException`.
   Implement degraded marking as idempotent upsert and clearing as
   delete-if-exists.
4. Add a non-blocking diagnostics service path that validates operation names,
   rejects `delayMs` outside `0..2000`, uses coroutine `delay`, and records
   slow operations at the 250 ms threshold. Run measurement and delay outside
   transactions; only the final diagnostic insert is transactional.
5. Add request correlation:
   - Spring WebFlux `WebFilter` with `X-Request-ID` sanitization, response
     echo, and `Ordered.HIGHEST_PRECEDENCE` ordering.
   - Ktor `CallId` and `CallLogging` integration with the same header rules.
   - Both stacks use `[A-Za-z0-9._-]{1,64}` and replace CR/LF, spaces, or
     oversized values with UUIDv4.
6. Add routes:
   - `GET /production/readiness`
   - `GET /production/diagnostics/operations`
   - `GET /production/diagnostics/operations/{name}?delayMs=...`
   Listing returns the newest 100 operation rows ordered descending by creation
   time.
7. Update structured error handlers so validation, parse, permission, and
   idempotency conflict errors include the active request id without regressing
   existing error codes.
8. Add focused Spring `WebTestClient` and Ktor `testApplication` tests for
   readiness, request-id echo/replacement, operation persistence, slow flag,
   validation errors, malformed query/body handling, request id on 400/403/409
   paths, `delayMs > 2000` rejection, operation-name pattern rejection,
   degraded-to-UP recovery, and CR/LF request-id replacement.
9. Add a committed PNG diagram under `docs/assets/readme-diagrams/` and update
   root, chapter, and module README pairs.
10. Add `docs/lessons/2026-05-23-issue-48-observability-readiness-r2dbc.md`.
11. Run targeted tests, detekt, anti-pattern scan, Codex 6-tier review, Claude
   Code CLI 6-tier review, then commit and open PR.

## Verification

- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check`
- Anti-pattern scan for `GlobalScope`, `Thread.sleep`, unsafe `runCatching`,
  `@Synchronized`, `synchronized`, deprecated Exposed
  `SqlExpressionBuilder.eq`, `assertThrows`, `kotlin.test.assertFailsWith`,
  and non-bluetape4k assertion APIs in touched code.
