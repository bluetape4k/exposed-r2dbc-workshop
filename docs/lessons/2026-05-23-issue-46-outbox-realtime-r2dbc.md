# Issue #46 Outbox Realtime R2DBC

## Context

Issue #46 needed paired Spring Boot 4 and Ktor database-backed realtime
notification patterns while preserving the existing two-module chapter 12
layout from issues #44 and #45.

## Decision

Keep realtime as a package-level slice inside the existing Spring and Ktor
production modules:

- Work-item creation persists the work row and a realtime outbox row in the
  same Exposed R2DBC `suspendTransaction`.
- Outbox rows carry explicit `PENDING`, `PUBLISHED`, and `FAILED` state with an
  attempt count and last error text.
- Spring publishes through an in-process SSE hub; Ktor publishes through a
  `MutableSharedFlow` WebSocket hub.
- Replay endpoints return only `PUBLISHED` rows after the supplied cursor, so
  reconnect behavior is database-backed rather than live-buffer-only.

## Guardrail

Future chapter 12 slices should continue extending the two existing modules
unless the module boundary itself becomes the lesson. Realtime changes must
keep testing event persistence before publish, cursor replay boundaries, live
delivery, and failed delivery retention.
