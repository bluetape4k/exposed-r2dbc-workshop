# Issue 39 Connection-factory-per-tenant R2DBC Plan

## Scope

Add `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`, a
Spring WebFlux + Exposed R2DBC workshop module that routes each tenant to a
distinct pooled `ConnectionFactory`.

Approved spec:

- `docs/superpowers/specs/2026-05-22-issue-39-connection-factory-per-tenant-r2dbc-design.md`

This plan keeps the module H2-only for the first implementation. Root CI covers
it through `./gradlew test`; no Nightly shard edit is planned unless non-H2
profiles are added during implementation.

## Implementation Tasks

### 1. Scaffold module from chapter 10 schema-per-tenant example

Complexity: M

- Copy the module shape from
  `10-multi-tenant/03-multitenant-spring-webflux` into
  `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`.
- Rename package to `exposed.r2dbc.multitenant.connectionfactory`.
- Rename the app entrypoint to `ConnectionFactoryTenantApp.kt` and update
  `springBoot.mainClass`.
- Keep the actor/movie API shape and fixture semantics so readers can compare
  strategy `03` and strategy `04`.
- Drop schema-switching helpers from the request path.
- Keep `src/test/resources/junit-platform.properties` and `logback-test.xml`.
- Keep public KDoc English in all new or materially changed public classes.

Rollback point: the new module is isolated under one directory and can be
removed without touching existing examples.

### 2. Configure tenant connection factories and pools

Complexity: L

- Add `app.tenants.default-tenant` and `app.tenants.definitions.*.url` to
  `application.yml`.
- Add bounded pool settings:
  `max-create-connection-time`, `max-acquire-time`, `acquire-retry`,
  `max-idle-time`, and conservative `max-size`.
- Implement configuration properties in
  `config/ConnectionFactoryTenantR2dbcConfig.kt`.
- Create tenant pools eagerly at startup from distinct H2 URLs:
  - `tenant_cf_korean`
  - `tenant_cf_english`
- Use `io.r2dbc.pool.ConnectionPool` and
  `io.r2dbc.pool.ConnectionPoolConfiguration` as the owned factory type.
- Do not create independent initializer pools.

Ordering dependency: task 3 uses this registry and the routing factory.

### 3. Implement routing registry, routing factory, and explicit databases

Complexity: L

- Implement `tenant/TenantConnectionFactoryRegistry.kt`.
  - Sole owner of tenant `ConnectionPool` instances.
  - Provides `get(tenantId)`, `keys()`, and a target map for Spring routing.
  - Implements Spring shutdown cleanup and calls `ConnectionPool.dispose()` once
    per pool. Local r2dbc-pool `1.0.2.RELEASE` exposes synchronous
    `dispose()`, plus reactive `disposeLater()`; use synchronous `dispose()` in
    `DisposableBean.destroy()` and keep shutdown deterministic without
    `runBlocking`.
- Implement `tenant/TenantRoutingConnectionFactory.kt` extending Spring
  `AbstractRoutingConnectionFactory`.
  - `determineCurrentLookupKey()` reads `TenantContextKeys.TENANT_ID` with
    `Mono.deferContextual`.
  - Empty lookup-key publisher lets Spring use the configured default factory.
  - Unknown emitted lookup key fails because bean creation calls
    `setLenientFallback(false)`.
- In `ConnectionFactoryTenantR2dbcConfig.kt`, create:
  - primary routing `ConnectionFactory`.
  - call Spring initialization explicitly with `afterPropertiesSet()` after
    `setTargetConnectionFactories`, `setDefaultTargetConnectionFactory`, and
    `setLenientFallback(false)`.
  - explicit `tenantRoutingDatabase` from the routing factory.
  - explicit initializer `Map<Tenant, R2dbcDatabase>` using registry-owned
    tenant pools.
- Use the same `Dispatchers.IO` `R2dbcDatabaseConfig` convention as existing
  R2DBC workshop modules; document it as local consistency, not because R2DBC
  itself is blocking.
- Avoid deprecated Exposed imports such as `SqlExpressionBuilder.eq`.

Risk control: every request transaction must use the explicit routing database
instead of depending on Exposed default database state.

### 4. Implement tenant contract and transaction boundary

Complexity: M

- Implement `tenant/Tenants.kt` for fixed `korean` and `english` tenants.
- Implement `tenant/TenantContextKeys.kt`.
- Implement strict tenant ID validation:
  - trim before validation.
  - accept only `[a-zA-Z0-9_-]{1,64}`.
  - reject empty, whitespace-only, control-character, too-long, unsupported
    character, and unknown tenant IDs with `400 Bad Request`.
- Implement `TenantFilter`.
  - Reads mandatory `X-TENANT-ID`.
  - Validates before registry/database access.
  - Writes the normalized tenant ID to Reactor context.
  - Uses fixed error messages for invalid or unknown tenant headers. Do not echo
    raw header values in `ResponseStatusException` messages.
  - Logs only the normalized tenant ID after it passes the regex allowlist.
- Implement `TenantTransactionExecutor`.
  - The only request-path production class allowed to call
    `suspendTransaction(db = tenantRoutingDatabase)`.
  - Does not catch broad exceptions around suspend calls; cancellation must
    propagate.
  - Always uses an explicit Reactor/coroutine bridge instead of relying on test
    discovery:
    - read `coroutineContext[ReactorContext]?.context`;
    - run the transaction inside `mono(coroutineContext) { ... }` or an
      equivalent `Mono.deferContextual` boundary that carries
      `TenantContextKeys.TENANT_ID` into the routing factory subscriber context;
    - call `suspendTransaction(db = tenantRoutingDatabase, ...)` inside that
      boundary.
  - Keep the 50-request concurrent WebFlux test as a regression guard for this
    bridge, not as a discovery mechanism.
- Controller and repository code must never call bare `suspendTransaction`.

### 5. Initialize isolated tenant data

Complexity: M

- Implement `tenant/DataInitializer.kt`.
  - Uses explicit initializer `R2dbcDatabase` for each tenant.
  - Creates actor/movie tables per tenant database.
  - Uses `SchemaUtils.create(...)` for the fresh H2 example database and does
    not use `SchemaUtils.createMissingTablesAndColumns`. If migration
    diagnostics are needed, inspect Exposed R2DBC `MigrationUtils` output before
    changing the initializer.
  - Seeds different Korean and English actor names for the same IDs.
  - Does not use schema switching.
- Keep startup deterministic and idempotent by skipping seed insert when actor
  rows already exist.
- Document that initializer databases reuse registry-owned pooled factories and
  do not own pool shutdown.

### 6. Add focused tests before broad verification

Complexity: L

Tests must use JUnit 5, `runSuspendIO`, and bluetape4k assertions.

- Context/config tests:
  - Spring context loads controller, registry, routing factory,
    `tenantRoutingDatabase`, and initializer.
  - Registry exposes distinct tenant factories for `korean` and `english`.
  - H2 tenant URLs have distinct database names.
- Routing factory tests:
  - no Reactor context tenant uses default tenant factory.
  - known Reactor context tenant routes to the matching factory.
  - unknown emitted tenant fails with lenient fallback disabled.
- Tenant validation tests:
  - reject `""`, whitespace-only, control-character, too-long, unsupported
    character, and unknown tenant IDs before registry lookup.
- HTTP integration tests:
  - `GET /actors` succeeds for every tenant.
  - `GET /actors/{id}` returns tenant-specific fixture values for the same ID.
  - missing, blank, malformed, and unknown headers return `400`.
  - at least 50 concurrent Korean/English requests assert the tenant
    fingerprint in every response, proving Reactor context survives the suspend
    controller and Exposed transaction boundary.
- Resource lifecycle tests:
  - registry closes each close-tracking pool exactly once.
- Transaction architecture tests:
  - scan production Kotlin files and fail on `\bsuspendTransaction\s*\(` outside
    `tenant/TenantTransactionExecutor.kt` and `tenant/DataInitializer.kt`.
  - scan integration tests for bare default-database transactions and require
    `TenantTransactionExecutor` or explicit initializer databases instead.
- Cancellation evidence:
  - unit-test `TenantTransactionExecutor` with a cancelling block to prove
    `CancellationException` is not swallowed.

### 7. Write README pair and delivery notes

Complexity: M

- Write `README.md` and `README.ko.md`.
- Explain:
  - when to choose connection-factory-per-tenant.
  - how it differs from schema-per-tenant module `03`.
  - pool-count scaling tradeoff.
  - fail-closed HTTP tenant header behavior.
  - default-fallback routing behavior outside HTTP paths.
  - no tenant authorization until issue #40.
  - root CI coverage and why Nightly is unchanged for this H2-only module.
- Include the concrete bounded pool YAML snippet in both README files.
- Include a Mermaid architecture flow in both README files so readers can
  compare visually with module `03`.
- Do not update chapter aggregate docs beyond this module because issue #42 owns
  that wiring.

### 8. Verify, review, and publish

Complexity: M

Run in order:

1. `./gradlew projects --console=plain`
2. `./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
3. `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
4. `git diff --check`
5. IDE diagnostics if available; otherwise record compile/test fallback.
6. Claude Step 6-R code review gate with `P0=0`, `P1=0`.
7. Create `docs/lessons/2026-05-22-issue-39-connection-factory-per-tenant-r2dbc.md`.
8. Commit with Lore trailers.
9. Push branch and open a draft PR assigned to `debop`.

## Step 3-R Local Review

| Perspective | P0 | P1 | P2/P3 notes |
|---|---:|---:|---|
| Implementer | 0 | 0 | Task order is scaffold -> routing/config -> contract -> data -> tests/docs. High-risk bridge and lifecycle work is split before controller tests. |
| Test engineer | 0 | 0 | Success, failure, edge validation, concurrency, coroutine cancellation, lifecycle, and architecture tests are named. |
| Architect | 0 | 0 | New module is isolated, reuses chapter 10 API shape and chapter 11 routing patterns without extracting shared APIs. |
| Delivery | 0 | 0 | README pair, CI/Nightly decision, KDoc, lesson, verification, commit, and PR tasks are explicit. |

## Claude Code Opus Advisor

Artifact: `.omx/artifacts/ask-claude-issue-39-plan-review-20260522102931.md`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Reactor/coroutine bridge was deferred to test feedback | Accepted | Plan now requires an explicit `ReactorContext` bridge in `TenantTransactionExecutor` before tests |
| P1 | Raw tenant header could be echoed in logs/errors | Accepted | Plan now requires fixed error messages and logging only normalized allowlisted tenant IDs |
| P2 | Pool shutdown behavior did not address reactive close variant | Accepted with correction | Local `ConnectionPool` has synchronous `dispose()`; plan requires `DisposableBean.destroy()` to call it once per pool |
| P2 | Config naming and `Dispatchers.IO` rationale were loose | Accepted | Plan names `ConnectionFactoryTenantR2dbcConfig.kt` and records local consistency rationale |
| P2 | Schema helper choice unspecified | Accepted with bounded choice | Plan uses `SchemaUtils.create(...)` for fresh H2 DBs and avoids `createMissingTablesAndColumns`; `MigrationUtils` only for diagnostics |
| P2 | README config snippet missing | Accepted | Plan requires bounded pool YAML in both README files |
| P3 | `afterPropertiesSet()` omitted | Accepted | Plan requires explicit routing factory initialization |
| P3 | Mermaid diagram optional | Accepted | Plan requires README diagram parity |

Re-review artifact:
`.omx/artifacts/ask-claude-issue-39-plan-rereview-20260522103352.md`

Latest re-review verdict: `P0=0`, `P1=0`, `P2=0`, `P3=0`. Step 3-R passes.

## Step 3-R Integration Status

Latest status: `P0=0`, `P1=0`; implementation may proceed.

## Step 6-R Code Review Gate

Artifact:
`.omx/artifacts/ask-claude-issue-39-code-review-gate-20260522110157.md`

Latest verdict: `P0=0`, `P1=0`, `P2=2`, `P3=0`, `VERDICT=PASS`.

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P2 | `R2dbcDatabase` beans rely on registry-owned pool disposal | Accepted | This is intentional for the workshop: registry owns pools; initializer/routing databases do not own independent pools |
| P2 | Architecture allowlist must be maintained if transaction files move | Accepted | Keep `ArchitectureTest` allowlist updated with any future transaction-boundary file move |
