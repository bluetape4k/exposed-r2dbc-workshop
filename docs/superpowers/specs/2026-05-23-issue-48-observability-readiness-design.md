# Issue #48 Observability Readiness Design

## Context

Issue #48 finishes the diagnostics slice for chapter 12. The matching
`exposed-workshop` issue #62 used dedicated JDBC modules:

- `09-spring-observability-readiness`
- `10-ktor-observability-readiness`

This repository already established a different R2DBC layout in issues #44
through #47: all production topics live as package slices inside the existing
paired modules:

- `12-production-integration/01-spring-production-integration`
- `12-production-integration/02-ktor-production-integration`

The R2DBC version should therefore add diagnostics behavior to those modules
instead of introducing topic-specific Gradle modules.

## Decision

Add a coroutine-first diagnostics slice to both stacks.

- Persist diagnostic operation rows in R2DBC tables owned by each module.
- Keep readiness state durable enough for tests by using the existing
  diagnostics status row for database degraded state.
- Keep request correlation explicit:
  - Safe request ids match `[A-Za-z0-9._-]{1,64}` after trimming. Values with
    CR, LF, spaces, unsupported characters, or excessive length are rejected
    and replaced with a generated UUIDv4.
  - Spring WebFlux: an `Ordered.HIGHEST_PRECEDENCE` `WebFilter` reads
    `X-Request-ID`, stores the safe id in the exchange attributes and Reactor
    context, and echoes it on every response.
  - Ktor: `CallId` reads `X-Request-ID`, verifies the same pattern, generates a
    UUIDv4 when missing or invalid, exposes it through `call.callId`, and
    echoes it on every response.
- Use the same request id in persisted diagnostic operation rows and structured
  error responses.
- Measure synthetic slow operations without blocking threads. The operation
  endpoint may accept a `delayMs` parameter, but it must use coroutine delay
  rather than `Thread.sleep`. `delayMs` must be in `0..2000`; larger or
  negative values return `INVALID_REQUEST`. The delay and timing measurement
  run outside any R2DBC transaction; only the final operation insert uses
  `suspendTransaction`.
- Operation names must match `[a-z][a-z0-9-]{0,40}`. Invalid path values return
  `INVALID_REQUEST`.
- Mark operations slow when measured duration reaches the workshop threshold of
  250 ms.
- Keep diagnostics endpoints outside the auth/session examples so readiness and
  operational probes can run without user credentials.
- Preserve existing structured error behavior for previous slices while adding
  request id metadata to error responses.
- List diagnostics endpoints are intentionally unauthenticated for workshop
  readability; production deployments should restrict them through internal
  routing, an ingress allow-list, or a management network.

## Tables

Keep the existing single-row diagnostics status table:

```text
Diagnostics(name varchar primary key, status varchar, details varchar)
```

Add a separate operation history table for each stack:

```text
DiagnosticOperations(
  id varchar(64) primary key,
  name varchar(64),
  request_id varchar(64),
  duration_ms long,
  slow bool,
  created_at_epoch_ms long
)
```

The operation table must not reuse `Diagnostics` because readiness status is a
single named state while operation diagnostics are append-only rows.
Operation ids are UUIDv4 strings. Listing endpoints return the newest 100
operation rows ordered by `created_at_epoch_ms DESC` so the workshop example
does not expose an unbounded scan.

## Readiness

Readiness combines sticky degraded state with a live R2DBC ping.

- `pingDatabase` runs a minimal query inside `suspendTransaction` and is bounded
  by a one-second coroutine timeout.
- Timeout, SQL/driver, or Exposed failures return `ReadinessView("DEGRADED",
  "...")` instead of escaping as HTTP 500.
- `CancellationException` is rethrown.
- A sticky `DEGRADED` diagnostics row keeps readiness degraded even if the live
  ping succeeds.
- `clearDatabaseDegraded` removes the sticky degraded row so readiness can
  recover to `UP` when the live ping succeeds.
- `markDatabaseDegraded` is an idempotent upsert on `Diagnostics.name =
  "database"` and `clearDatabaseDegraded` is delete-if-exists.

## Non-Goals

- No Micrometer registry, tracing backend, OpenTelemetry exporter, or metrics
  server integration.
- No real database outage injection; degraded state is represented by the
  persisted diagnostics row so tests stay deterministic.
- No new shared abstraction across Spring and Ktor.
- No topic-specific Gradle modules.

## Acceptance Criteria

- Spring and Ktor expose readiness and diagnostic operation endpoints.
- Readiness reports `UP` when the database is reachable and `DEGRADED` when the
  diagnostics row records database degradation.
- Operation endpoints persist name, request id, duration, slow flag, and
  creation time.
- Invalid `X-Request-ID` values are replaced before echo and persistence.
- Structured validation and malformed request errors include the current
  request id.
- `delayMs` values outside `0..2000` and operation names outside
  `[a-z][a-z0-9-]{0,40}` return `INVALID_REQUEST`.
- Readiness covers live ping success, sticky degraded state, and recovery after
  clearing the degraded row.
- Tests cover readiness success/degraded state, request-id correlation,
  operation persistence, slow-operation detection, and structured errors for
  both stacks.
- README pairs document Spring WebFlux versus Ktor diagnostics tradeoffs.
- The root/chapter README diagram for this issue is a committed PNG.
- Codex and Claude Code CLI 6-tier reviews converge with P0=0 and P1=0 before
  PR creation.
