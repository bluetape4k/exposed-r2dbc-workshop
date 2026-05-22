# Issue #47 HTTP Outbox Idempotency Design

## Context

Issue #47 completes the outbound HTTP client slice inside the existing chapter
12 Spring Boot 4 and Ktor R2DBC production integration modules. Earlier chapter
12 issues established the two-module package-slice layout, authentication,
sessions, and realtime outbox publishing. The matching `exposed-workshop`
issue #61 used dedicated JDBC modules; this repository keeps the R2DBC lesson
inside:

- `12-production-integration/01-spring-production-integration`
- `12-production-integration/02-ktor-production-integration`

## Decision

Extend the existing `OutboundRequests` tables into a durable HTTP outbox state
machine instead of adding topic-specific Gradle modules.

- Persist the outbound row before any external HTTP dispatch.
- Keep `idempotencyKey` as the duplicate boundary; duplicate submissions return
  HTTP 409 with the existing structured error shape.
- Dispatch only `PENDING` and `RETRYABLE_FAILED` rows.
- Claim rows as `IN_FLIGHT` before dispatch so concurrent dispatch calls cannot
  double-send the same row in this single-process workshop app. The claim
  happens in a short R2DBC transaction guarded by a repository mutex.
- Release the transaction before any HTTP call, then persist the result in a
  new short transaction.
- Mark 2xx responses as `SUCCEEDED`.
- Mark ordinary 4xx responses as `PERMANENT_FAILED`.
- Mark 408, 425, 429, 5xx, timeout, and transport failures as
  `RETRYABLE_FAILED` until `maxAttempts` is exhausted, then mark
  `PERMANENT_FAILED`. `maxAttempts` is a per-module repository constant for
  this workshop slice.
- Preserve retry evidence with `attempts`, `lastStatusCode`, and `lastError`.
- Store only sanitized failure metadata in `lastError`: status class, exception
  simple name, and a truncated safe message. Do not persist headers, request
  bodies, response bodies, or secrets.
- Keep the existing DB unique index on `idempotencyKey`; the repository keeps a
  deterministic single-process duplicate check and maps duplicate constraint
  failures to the same HTTP 409 conflict.
- Restrict enqueue, list, and dispatch routes to the existing
  `outbound:create` permission. Users with only `work:create` must receive 403.
- Use stack-native clients behind injectable boundaries:
  - Spring: WebFlux `WebClient` coroutine APIs.
  - Ktor: `HttpClient` with MockEngine-friendly request inspection.

## Non-Goals

- No real external service dependency in tests.
- No replay of a previous successful response for duplicate keys.
- No scheduler/background worker.
- No new shared abstraction across Spring and Ktor modules.
- No multi-node distributed dispatcher lock; the workshop teaches the local
  transaction boundary and durable state machine, not cluster scheduling.
- No stale `IN_FLIGHT` reaper after process crash; production systems need a
  lease timeout or recovery job.

## Acceptance Criteria

- Success, retryable failure plus retry, duplicate idempotency key, and
  permanent failure paths are tested for both stacks.
- Concurrent dispatch does not double-send the same outbound row.
- Duplicate submit races resolve to one persisted row plus conflict.
- Exhausted retry attempts become permanent failures.
- Error persistence is sanitized and truncated.
- The paired modules build and test independently.
- README pairs explain the R2DBC HTTP outbox/idempotency tradeoff.
- Root/chapter README diagram remains PNG-backed and shared by English/Korean
  docs.
- Review gates record P0/P1 convergence before PR creation.
