# Issue #47 HTTP Outbox Idempotency R2DBC

## Context

Issue #47 extends chapter 12 with outbound HTTP client examples for both Spring
Boot 4 WebFlux and Ktor. The exposed-workshop analog already proved the topic
shape, but this repository must keep the R2DBC/coroutine boundary explicit and
must not create topic-specific modules outside the two chapter 12 modules.

## Decision

- Keep the work as paired `outbound` slices inside
  `01-spring-production-integration` and `02-ktor-production-integration`.
- Persist outbound HTTP rows before dispatch with a database-unique
  idempotency key as the duplicate boundary.
- Validate outbound idempotency keys before persistence because the value is
  replayed into an `Idempotency-Key` HTTP header during dispatch.
- Claim `PENDING` and retryable rows as `IN_FLIGHT` in a short
  `suspendTransaction`, perform Spring WebClient or Ktor HTTP client calls
  outside the transaction, then record terminal/retryable state in a new
  transaction.
- Cap retryable failures at three attempts and store sanitized error text so
  credential-like values do not become durable row data.
- Bound each outbound dispatch attempt with a repository default timeout and
  record timeout as retryable transport status `599`; log remapped transport
  exceptions at WARN before storing sanitized row state.
- Use replaceable delivery interfaces and Ktor `MockEngine` to test outbound
  behavior without a real external HTTP service.

## Outcome

The Spring and Ktor examples now cover success, retry-to-success, retry
exhaustion, permanent client failure, duplicate idempotency keys, permission
denial, invalid target validation, timeout mapping, cross-repository duplicate
enqueue protection, and concurrent single-send dispatch. README
diagrams use a committed PNG generated from the SVG source under
`docs/assets/readme-diagrams/`.

## Verification

Use a single Gradle invocation for the chapter 12 modules:

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:cleanTest :02-ktor-production-integration:cleanTest :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

## Future Guard

Do not move the outbound HTTP examples into separate topic modules in this
repository. Future production slices for chapter 12 should remain package
slices inside the existing Spring and Ktor modules unless the chapter design is
explicitly revised.
