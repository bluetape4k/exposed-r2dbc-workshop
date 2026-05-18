# WIP - exposed-r2dbc-workshop

Snapshot: 2026-05-18 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 19 issues.

## Recently Completed

- CI/Nightly, version catalog migration, Spring Boot 4 alignment, dependency
  governance, and compatibility guards are merged.
- Test cleanup and Kluent to `bluetape4k-assertions` migration are merged.
- README hero/architecture refresh is merged.
- CTE Query Builder example branch exists separately for the new
  `bluetape4k-exposed` API.
- QMD-backed audit registered `#54` for shared `withTables()` cancellation
  handling.

## Current Direction

Keep shared R2DBC test infrastructure cancellation-safe before expanding
chapter 10-12 examples. New examples should rely on helpers that preserve
structured coroutine cancellation and report cleanup failures clearly.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P2 | [#54](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/54) `withTables()` can swallow coroutine cancellation during cleanup | M | Shared test helper uses `runCatching` and broad `Throwable` around suspend cleanup. |
| P3 | [#32](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/32) Ktor examples for chapters 10 and 11 epic | L | Parent for `#33`-`#36`. |
| P3 | [#33](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/33) Ktor R2DBC multi-tenant example | M | Child of `#32`. |
| P3 | [#34](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/34) Ktor R2DBC cache strategies example | M | Child of `#32`. |
| P3 | [#35](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/35) Ktor R2DBC routing datasource example | M | Child of `#32`; keep routing lifecycle/cancellation behavior explicit. |
| P3 | [#36](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/36) wire Ktor R2DBC chapter examples into docs and verification | S | Finish after `#33`-`#35`. |
| P3 | [#37](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/37) Spring WebFlux R2DBC multi-tenant strategy epic | L | Parent for `#38`-`#42`. |
| P3 | [#38](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/38) schema-per-tenant Spring WebFlux R2DBC example | M | Child of `#37`. |
| P3 | [#39](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/39) connection-factory-per-tenant Spring WebFlux R2DBC example | M | Child of `#37`. |
| P3 | [#40](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/40) Spring Security tenant authorization R2DBC example | M | Child of `#37`. |
| P3 | [#41](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/41) tenant onboarding/provisioning R2DBC example | M | Child of `#37`. |
| P3 | [#42](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/42) wire chapter 10 examples into docs and verification | S | Finish after `#38`-`#41`. |
| P3 | [#43](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/43) chapter 12 R2DBC production integration epic | L | Parent for `#44`-`#49`. |
| P3 | [#44](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/44) Spring Boot 4 and Ktor application architecture examples | M | Child of `#43`. |
| P3 | [#45](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/45) authentication/session examples | M | Child of `#43`. |
| P3 | [#46](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/46) outbox realtime examples | M | Child of `#43`. |
| P3 | [#47](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/47) HTTP client outbox/idempotency examples | M | Child of `#43`. |
| P3 | [#48](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/48) observability/readiness examples | M | Child of `#43`. |
| P3 | [#49](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/49) wire chapter 12 examples into docs and verification | S | Finish after `#44`-`#48`. |

## Dependency Map

```text
#54 withTables cancellation-safe cleanup
  -> shared test baseline for future R2DBC multi-database examples
  -> fix before broad chapter 10/11/12 test expansion

#32 Ktor chapters 10/11 epic
  -> #33 Ktor R2DBC multi-tenant
  -> #34 Ktor R2DBC cache strategies
  -> #35 Ktor R2DBC routing datasource
  -> #36 docs and verification

#37 Spring WebFlux R2DBC chapter 10 multi-tenant epic
  -> #38 schema-per-tenant
  -> #39 connection-factory-per-tenant
  -> #40 Spring Security tenant authorization
  -> #41 onboarding/provisioning
  -> #42 docs and verification

#43 chapter 12 R2DBC production integration epic
  -> #44 application architecture
  -> #45 authentication/session
  -> #46 outbox realtime
  -> #47 HTTP client outbox/idempotency
  -> #48 observability/readiness
  -> #49 docs and verification
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Shared test correctness | 1 | `#54` before broad R2DBC example expansion. |
| Ktor R2DBC examples | 1 | Start one child under `#32`; finish `#36` after children. |
| Spring WebFlux R2DBC multi-tenant examples | 1 | Start one child under `#37`; finish `#42` after children. |
| Production integration examples | 1 | Start one child under `#43`; finish `#49` after children. |
