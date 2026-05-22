# Issue #47 HTTP Outbox Idempotency Plan

## Tasks

1. Extend Spring and Ktor outbound models with explicit status, attempts,
   response code, and error fields.
2. Extend Spring and Ktor R2DBC tables/repositories with durable status
   transitions for pending, in-flight claim, retryable failure, permanent
   failure, exhausted attempts, and success. Keep HTTP calls outside
   `suspendTransaction`.
3. Add injectable outbound dispatch boundaries:
   - Spring WebFlux `WebClient` implementation.
   - Ktor `HttpClient` implementation.
4. Add HTTP routes to list outbound rows and dispatch retryable rows; guard all
   outbound routes with `outbound:create`.
5. Add focused Spring WebTestClient and Ktor `testApplication` tests for:
   success, retry, duplicate idempotency key, permanent failure, and permission
   boundaries.
6. Add repository/service tests for concurrent duplicate submit, concurrent
   dispatch single-send behavior, max-attempt exhaustion, and sanitized
   `lastError` persistence.
7. Update chapter/module/root README pairs and add a PNG diagram asset under
   `docs/assets/`.
8. Add `docs/lessons/2026-05-23-issue-47-http-outbox-idempotency-r2dbc.md`.
9. Run targeted tests, anti-pattern scan, Codex 6-tier review, Claude Code CLI
   6-tier review, then commit and open PR.

## Verification

- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check --cached`
- Anti-pattern scan for blocking calls, unsafe coroutine scopes, deprecated
  assertions, and deprecated Exposed imports.
