# Issue #38 Schema-Per-Tenant R2DBC Design

## Context

Issue #38 is part of the Chapter 10 multi-tenant epic (#37). The existing
`10-multi-tenant/03-multitenant-spring-webflux` module already contains a
Spring WebFlux + Exposed R2DBC implementation around the actors/movies domain.
This work re-baselines on that implementation and closes the gaps against the
issue acceptance criteria.

## Existing Inventory

Keep and evolve these files:

- `ExposedMultitenantWebfluxApp.kt`
- `config/ExposedR2dbcConfig.kt`
- `config/NettyConfig.kt`
- `config/SwaggerConfig.kt`
- `config/TenantConfig.kt`
- `controller/ActorController.kt`
- `domain/model/Mappers.kt`
- `domain/model/MovieRecords.kt`
- `domain/model/MovieSchema.kt`
- `domain/repository/ActorR2dbcRepository.kt`
- `domain/repository/MovieR2dbcRepository.kt`
- `tenant/DataInitializer.kt`
- `tenant/SchemaSupport.kt`
- `tenant/TenantFilter.kt`
- `tenant/TenantId.kt`
- `tenant/TenantInitializer.kt`
- `tenant/Tenants.kt`
- `AbstractMultitenantTest.kt`
- `ActorControllerTest.kt`
- `ExposedR2dbcConfigTest.kt`
- `ConnectionPoolSizingTest.kt`

Do not replace the actors/movies domain with a new orders aggregate. The current
domain is already used by the README and tests, and it is sufficient to prove
schema-per-tenant isolation.

## Goal

Show tenant-specific schema routing with shared R2DBC database infrastructure in
a Spring WebFlux + Kotlin coroutine example.

## Scope

- Preserve the existing actors/movies domain and `/actors` API.
- Resolve tenants from the `X-TENANT-ID` request header.
- Propagate the resolved tenant through Reactor context into coroutine
  controllers.
- Use `suspendTransactionWithCurrentTenant` to set the Exposed R2DBC schema for
  every tenant-aware transaction.
- Tighten error handling and tests for missing tenant, unknown tenant, and
  cross-tenant isolation.
- Document schema-per-tenant tradeoffs in `README.md` and `README.ko.md`.
- Record CI/Nightly coverage decisions.

## Non-Goals

- Do not implement connection-factory-per-tenant routing. That belongs to #39.
- Do not implement Spring Security tenant authorization. That belongs to #40.
- Do not implement tenant onboarding/provisioning flows. That belongs to #41.
- Do not add a new aggregate or public API beyond what is needed for #38.

## Tenant Contract

- Header: `X-TENANT-ID`
- Supported tenant IDs: `korean`, `english`
- Missing or blank header: `400 Bad Request`
- Unknown tenant ID: `400 Bad Request`

The existing default-on-missing behavior is changed because Issue #38 explicitly
requires a missing-tenant test. In a schema-per-tenant example, silent fallback
can hide data isolation bugs, so the request must name its tenant.

## Context Propagation

Use the existing `TenantFilter` and `TenantId` helpers:

- `TenantFilter` reads `X-TENANT-ID`, validates it, and stores `TenantId` in the
  Reactor context.
- Controllers call `suspendTransactionWithCurrentTenant`, which reads the
  Reactor-context tenant through `currentReactorTenant()`.
- `currentTenant()` remains the coroutine-context helper for non-WebFlux direct
  calls; WebFlux controllers use `currentReactorTenant()` through
  `suspendTransactionWithCurrentTenant`.
- `TenantId.DEFAULT` and the fallback inside `currentReactorTenant()` are
  retained only for non-WebFlux direct calls and tests, with a warning log when
  the fallback is used. The HTTP WebFlux path must reject missing or blank
  headers before any fallback is used.

Spring official documentation confirms WebFlux supports coroutine controllers
and Kotlin coroutine-specific WebFilter support. Spring also documents Reactor
context as the propagation path for nested reactive operations.

## Schema Routing

Keep the current `SchemaUtils.setSchema(getSchemaDefinition(tenant))` strategy
instead of replacing all tables with dynamic schema-qualified table classes.
The existing repositories use unqualified actors/movies tables, and the
transaction helper is the module's intended teaching point.

Risk control:

- Set the tenant schema at the beginning of every tenant-aware transaction.
- Add tests that execute requests for different tenants through the same app
  instance and verify no rows leak.
- Avoid using plain `suspendTransaction` in controllers.
- If a future change adds non-tenant transactions to this module, reset schema
  state explicitly or use schema-qualified table factories for that path.

The design accepts that `SchemaUtils.setSchema` is connection state. The example
teaches how to contain that state in a small transaction helper; #39 will cover
connection-factory isolation.

## API Surface

- `GET /actors`
  - Header: `X-TENANT-ID`
  - Returns actors from only the resolved tenant schema.
- `GET /actors/{id}`
  - Header: `X-TENANT-ID`
  - Returns the actor from only the resolved tenant schema.

No new endpoint is required for #38.

## Tests

Keep existing tests and update/add focused cases:

- `ActorControllerTest`
  - keep tenant parameterized successful reads.
  - keep unknown tenant error test, aligned to `400`.
  - replace default-tenant missing-header expectation with `400`.
  - add or strengthen cross-tenant isolation evidence.
- `ExposedR2dbcConfigTest`
  - keep repository/schema initialization coverage.
- `ConnectionPoolSizingTest`
  - keep unchanged.
- `AbstractMultitenantTest`
  - keep unchanged unless test bootstrap needs explicit reset.

## Documentation

Update the existing English/Korean README files without deleting current
architecture sections:

- clarify when schema-per-tenant is appropriate.
- clarify `X-TENANT-ID` is mandatory.
- record that #39 handles connection-factory-per-tenant.
- record CI/Nightly coverage decisions.

## CI/Nightly Decision

No new workflow is required for #38:

- CI runs the module through root `./gradlew test`.
- Nightly already references `:03-multitenant-spring-webflux:test` in
  `.github/workflows/nightly.yml`.
- Local verification must run
  `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`.
- If the module name changes, update this local command and the Nightly
  `:03-multitenant-spring-webflux:test` shard entry together.

## Evidence

- Issue #38 requires schema routing, missing tenant, unknown tenant, and
  cross-tenant isolation tests.
- Existing source already implements actors/movies schema-per-tenant routing
  with `TenantFilter`, `TenantId`, `Tenants`, and
  `suspendTransactionWithCurrentTenant`.
- Existing Exposed examples use `SchemaUtils.createSchema` and
  schema-qualified test tables.
- Spring docs confirm coroutine controller support and Reactor context
  propagation.

## Review Notes

Initial Claude advisor review artifact:
`.omx/artifacts/claude-issue-38-spec-plan-review-20260522.md`.

Claude advisor re-review artifact:
`.omx/artifacts/claude-issue-38-spec-plan-rereview-20260522.md`.

Accepted P0/P1 fixes:

- Re-baselined on existing source and tests.
- Kept actors/movies domain instead of adding orders.
- Kept `X-TENANT-ID`.
- Chose missing/unknown tenant `400`.
- Kept `SchemaUtils.setSchema` and documented pooled connection-state risk.
- Added explicit keep/modify decisions for existing tests and README files.

Latest gate status: `P0=0`, `P1=0`, Gate `PASS`.
