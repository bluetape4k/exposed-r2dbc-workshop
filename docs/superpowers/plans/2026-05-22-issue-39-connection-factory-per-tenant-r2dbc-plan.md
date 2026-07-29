# Issue #39 Connection-factory-per-tenant R2DBC 구현 계획

## 범위

추가: `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`, a
Spring WebFlux + Exposed R2DBC workshop module that routes each tenant to a
distinct pooled `ConnectionFactory`.

승인된 명세:

- `docs/superpowers/specs/2026-05-22-issue-39-connection-factory-per-tenant-r2dbc-design.md`

이 계획은 the module H2-only for the first implementation. root CI가 이를 커버한다 through `./gradlew test`; non-H2 profile이 구현 중 추가되지 않는 한 Nightly shard 수정은 계획하지 않는다: non-H2
profiles are added during implementation.

## 구현 작업

### 1. Scaffold module from chapter 10 schema-per-tenant example

복잡도: M

- module shape를 다음에서 복사한다:
  `10-multi-tenant/03-multitenant-spring-webflux` into
  `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`.
- package를 다음으로 변경한다: `exposed.r2dbc.multitenant.connectionfactory`.
- app entrypoint를 다음으로 변경한다: `ConnectionFactoryTenantApp.kt` and update
  `springBoot.mainClass`.
- 유지: the actor/movie API shape and fixture semantics so readers can compare
  strategy `03` and strategy `04`.
- request path에서 schema-switching helper를 제거한다.
- 유지: `src/test/resources/junit-platform.properties` and `logback-test.xml`.
- 유지: public KDoc English in all new or materially changed public classes.

Rollback point: 새 module은 한 directory 아래에 격리되어 있어 기존 example을 건드리지 않고 제거할 수 있다.

### 2. Configure tenant connection factories and pools

복잡도: L

- 추가: `app.tenants.default-tenant` and `app.tenants.definitions.*.url` to
  `application.yml`.
- 추가: bounded pool setting:
  `max-create-connection-time`, `max-acquire-time`, `acquire-retry`,
  `max-idle-time`, and conservative `max-size`.
- 구현: configuration properties in
  `config/ConnectionFactoryTenantR2dbcConfig.kt`.
- 생성: tenant pools eagerly at startup from distinct H2 URLs:
  - `tenant_cf_korean`
  - `tenant_cf_english`
- 사용: `io.r2dbc.pool.ConnectionPool` and
  `io.r2dbc.pool.ConnectionPoolConfiguration` as the owned factory type.
- 금지: 독립 initializer pool을 생성한다.

Ordering dependency: 작업 3은 이 registry와 routing factory를 사용한다.

### 3. 구현: routing registry, routing factory, and explicit databases

복잡도: L

- 구현: `tenant/TenantConnectionFactoryRegistry.kt`.
  - tenant `ConnectionPool` instance의 단일 owner.
  - provides `get(tenantId)`, `keys()`, and a target map for Spring routing.
  - Spring shutdown cleanup을 구현한다 and calls `ConnectionPool.dispose()` once
    per pool. local r2dbc-pool `1.0.2.RELEASE` 는 synchronous
    `dispose()`, plus reactive `disposeLater()`; 사용: `dispose()` in
    `DisposableBean.destroy()` and shutdown을 deterministic하게 유지한다 without
    `runBlocking`.
- 구현: `tenant/TenantRoutingConnectionFactory.kt` extending Spring
  `AbstractRoutingConnectionFactory`.
  - `determineCurrentLookupKey()` 읽는다: `TenantContextKeys.TENANT_ID` with
    `Mono.deferContextual`.
  - 빈 lookup-key publisher는 Spring이 configured default factory를 사용하게 한다.
  - 알 수 없는 emitted lookup key는 bean creation에서
    `setLenientFallback(false)`.
- In `ConnectionFactoryTenantR2dbcConfig.kt`, create:
  - primary routing `ConnectionFactory`.
  - call Spring initialization explicitly with `다음 위치 뒤:PropertiesSet()` 다음 위치 뒤:
    `setTargetConnectionFactories`, `setDefaultTargetConnectionFactory`, and
    `setLenientFallback(false)`.
  - explicit `tenantRoutingDatabase` from the routing factory.
  - explicit initializer `Map<Tenant, R2dbcDatabase>` using registry-owned
    tenant pools.
- 사용: the same `Dispatchers.IO` `R2dbcDatabaseConfig` convention as existing
  R2DBC workshop modules; document it as local consistency, not because R2DBC
  itself is blocking.
- 회피: deprecated Exposed imports such as `SqlExpressionBuilder.eq`.

Risk control: every request transaction은 다음을 사용해야 한다: the explicit routing database
Exposed default database state에 의존하지 않는다.

### 4. 구현: tenant contract and transaction boundary

복잡도: M

- 구현: `tenant/Tenants.kt` 고정 `korean` and `english` tenants.
- 구현: `tenant/TenantContextKeys.kt`.
- 구현: strict tenant ID validation:
  - validation 전에 trim한다.
  - accept only `[a-zA-Z0-9_-]{1,64}`.
  - 거부: empty, whitespace-only, control-character, too-long, unsupported
    character, and unknown tenant IDs with `400 Bad Request`.
- 구현: `TenantFilter`.
  - Reads mandatory `X-TENANT-ID`.
  - Validates before registry/database access.
  - Writes the normalized tenant ID to Reactor context.
  - Uses fixed error messages for invalid or unknown tenant headers. 금지: echo
    raw 헤더 값을 읽는다 in `ResponseStatusException` messages.
  - Logs only the normalized tenant ID 다음 위치 뒤: it passes the regex allowlist.
- 구현: `TenantTransactionExecutor`.
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
  - 유지: the 50-request concurrent WebFlux test as a regression guard for this
    bridge, not as a discovery mechanism.
- Controller and repository code must never call bare `suspendTransaction`.

### 5. Initialize isolated tenant data

복잡도: M

- 구현: `tenant/DataInitializer.kt`.
  - Uses explicit initializer `R2dbcDatabase` for each tenant.
  - Creates actor/movie tables per tenant database.
  - Uses `SchemaUtils.create(...)` for the fresh H2 example database and does
    not use `SchemaUtils.createMissingTablesAndColumns`. If migration
    diagnostics are needed, inspect Exposed R2DBC `MigrationUtils` output before
    changing the initializer.
  - Seeds different Korean and English actor names for the same IDs.
  - Does not use schema switching.
- 유지: startup deterministic and idempotent by skipping seed insert when actor
  rows already exist.
- 문서화: that initializer databases reuse registry-owned pooled factories and
  do not own pool shutdown.

### 6. 추가: focused tests before broad verification

복잡도: L

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
  - 거부: `""`, whitespace-only, control-character, too-long, unsupported
    character, and unknown tenant IDs registry lookup 전에 거부한다.
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

복잡도: M

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
- 금지: update chapter aggregate docs beyond this module because issue #42 owns
  that wiring.

### 8. Verify, review, and publish

복잡도: M

실행: in order:

1. `./gradlew projects --console=plain`
2. `./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
3. `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
4. `git diff --check`
5. IDE diagnostics if available; otherwise record compile/test fallback.
6. Claude 단계 6-R code review gate with `P0=0`, `P1=0`.
7. 생성: `docs/lessons/2026-05-22-issue-39-connection-factory-per-tenant-r2dbc.md`.
8. 커밋: with Lore trailers.
9. 푸시: branch and open a draft PR assigned to `debop`.

## 단계 3-R 로컬 검토

| Perspective | P0 | P1 | P2/P3 notes |
|---|---:|---:|---|
| Implementer | 0 | 0 | 작업 순서는 scaffold -> routing/config -> contract -> data -> tests/docs다. High-risk bridge와 lifecycle 작업은 controller test 전에 분리한다. |
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
| P3 | `다음 위치 뒤:PropertiesSet()` omitted | Accepted | Plan requires explicit routing factory initialization |
| P3 | Mermaid diagram optional | Accepted | Plan requires README diagram parity |

Re-review artifact:
`.omx/artifacts/ask-claude-issue-39-plan-rereview-20260522103352.md`

최신 재검토 판정: `P0=0`, `P1=0`, `P2=0`, `P3=0`. 단계 3-R passes.

## 단계 3-R Integration Status

최신 상태: `P0=0`, `P1=0`; implementation may proceed.

## 단계 6-R 코드 검토 Gate

Artifact:
`.omx/artifacts/ask-claude-issue-39-code-review-gate-20260522110157.md`

최신 판정: `P0=0`, `P1=0`, `P2=2`, `P3=0`, `VERDICT=PASS`.

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P2 | `R2dbcDatabase` beans rely on registry-owned pool disposal | Accepted | This is intentional for the workshop: registry owns pools; initializer/routing databases do not own independent pools |
| P2 | Architecture allowlist must be maintained if transaction files move | Accepted | 향후 transaction-boundary file 이동 시 `ArchitectureTest` allowlist를 함께 갱신한다. |
