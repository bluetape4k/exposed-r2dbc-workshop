# Issue 39 Connection-factory-per-tenant R2DBC Design

## 맥락

Issue #39 extends chapter 10 with a Spring WebFlux R2DBC multi-tenant strategy
that routes each tenant to its own `ConnectionFactory`, instead of sharing one
database and switching schema per transaction.

The previous chapter 10 module is:

- `10-multi-tenant/03-multitenant-spring-webflux`
- Strategy: schema-per-tenant.
- HTTP tenant contract: mandatory `X-TENANT-ID`; missing, blank, and unknown
  tenants return `400 Bad Request`.
- Data isolation: `SchemaUtils.setSchema(...)` inside each Exposed transaction.

The new module will be:

- `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`
- Strategy: connection-factory-per-tenant.
- HTTP tenant contract: same mandatory `X-TENANT-ID` semantics as module `03`.
- Data isolation: each tenant maps to a different R2DBC URL and connection pool;
  no request path calls `SchemaUtils.setSchema`.

## External Evidence

Context7 quota was unavailable in this session. Official Spring Framework docs
were checked directly instead.

- Spring Framework `AbstractRoutingConnectionFactory` routes `create()` calls to
  target factories based on a lookup key, typically from subscriber context. It
  supports target factory maps, a default target, `setLenientFallback(false)`,
  and `determineCurrentLookupKey()`.
  Source: <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/r2dbc/connection/lookup/AbstractRoutingConnectionFactory.html>
- Local Spring source jar `spring-r2dbc-6.2.18-sources.jar` confirms:
  - empty lookup key may fall back to the default target.
  - unknown emitted lookup key fails when `lenientFallback=false`.
  - `determineCurrentLookupKey()` is the intended subscriber-context hook.
- Spring R2DBC reference docs confirm Spring obtains R2DBC connections through a
  `ConnectionFactory` and hides pooling/transaction concerns behind that
  abstraction.
  Source: <https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html>

## Local 재사용: Evidence

Existing local patterns:

- `10-multi-tenant/03-multitenant-spring-webflux`
  - actor/movie domain, WebFlux controller, tenant filter, README pair, and
    focused tests.
  - best source for chapter 10 HTTP contract and test shape.
- `11-high-performance/03-routing-datasource`
  - Reactor-context routing key pattern.
  - `ConnectionFactoryRegistry`, `DynamicRoutingConnectionFactory`, routing
    config, routing WebFilter tests, and README strategy explanation.
  - good source for connection factory registry shape, but this issue should
    narrow the concept to tenant isolation rather than read-write/read-only
    routing.

## 목표s

1. 추가: a chapter 10 Spring WebFlux R2DBC module that demonstrates
   connection-factory-per-tenant routing.
2. 보존: the user-facing actor API shape from module `03` so readers can
   compare the two tenant strategies.
3. Make tenant routing fail closed on HTTP paths:
   - missing header: `400 Bad Request`.
   - blank header: `400 Bad Request`.
   - unknown tenant: `400 Bad Request`.
4. Demonstrate fallback behavior only outside the HTTP path:
   - no Reactor tenant key emitted to the routing connection factory uses the
     configured default tenant connection.
   - emitted unknown lookup key fails because lenient fallback is disabled.
5. 문서화: resource lifecycle assumptions:
   - each tenant owns a distinct pooled `ConnectionFactory`.
   - all tenant pools are closed on Spring shutdown.
   - pool count grows with tenant count, so this strategy is for bounded tenant
     sets or provisioned tenant pools.

## Non-goals

- 금지: add Spring Security tenant authorization; that is issue #40.
- 금지: add tenant onboarding/provisioning workflow; that is issue #41.
- 금지: wire chapter 10 aggregate docs/verification beyond the new module's
  direct CI/Nightly coverage decision; that is issue #42.
- 금지: support dynamic runtime tenant registration in this issue.
- 금지: introduce a shared bluetape4k library API. This remains a workshop
  example module.

## Proposed Module Shape

Module:

```text
10-multi-tenant/04-connection-factory-per-tenant-spring-webflux
```

Package:

```text
exposed.r2dbc.multitenant.connectionfactory
```

Key production components:

- `ConnectionFactoryTenantApp.kt`
  - Spring Boot entrypoint.
- `tenant/Tenants.kt`
  - enum-style tenant registry: `korean`, `english`.
- `tenant/TenantFilter.kt`
  - 읽는다: mandatory `X-TENANT-ID`, validates tenant, writes tenant ID to Reactor
    context.
- `tenant/TenantContextKeys.kt`
  - Reactor context key constants.
- `tenant/TenantRoutingConnectionFactory.kt`
  - extends Spring `AbstractRoutingConnectionFactory`.
  - `determineCurrentLookupKey()` 읽는다: `TenantContextKeys.TENANT_ID` from
    Reactor context.
  - `setLenientFallback(false)` is used during bean creation.
- `tenant/TenantConnectionFactoryRegistry.kt`
  - 저장한다: tenant ID to `ConnectionFactory`.
  - owns connection pool lifecycle and closes pools on shutdown.
- `config/ConnectionFactoryTenantR2dbcConfig.kt`
  - binds `app.tenants.*` settings.
  - creates tenant pools and registry.
  - creates routing `ConnectionFactory`.
  - creates an explicit routing `R2dbcDatabase`.
  - creates explicit tenant initializer `R2dbcDatabase` instances.
- `tenant/TenantTransactionExecutor.kt`
  - wraps Exposed `suspendTransaction(db = routingDatabase, ...)` so controllers
    always use the routing database, not whichever `R2dbcDatabase` was connected
    last during initialization.
- `tenant/DataInitializer.kt`
  - creates tables and loads different sample data per tenant using explicit
    tenant initializer databases.
- `controller/ActorController.kt`
  - same API shape as module `03`: `GET /actors`, `GET /actors/{id}`.

## Configuration Contract

사용: H2 tenant databases by default to keep the module CI-friendly:

```yaml
app:
  tenants:
    default-tenant: korean
    definitions:
      korean:
        url: r2dbc:h2:mem:///tenant_cf_korean;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
      english:
        url: r2dbc:h2:mem:///tenant_cf_english;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

The example may add pool settings under `app.r2dbc.pool.*` if needed, but must
avoid per-test external containers unless a later issue explicitly requires DB
matrix coverage for this module.

## Error Contract

HTTP request path:

| Case | Result |
|---|---|
| `X-TENANT-ID: korean` | Route to Korean tenant connection factory |
| `X-TENANT-ID: english` | Route to English tenant connection factory |
| missing `X-TENANT-ID` | `400 Bad Request` |
| blank `X-TENANT-ID` | `400 Bad Request` |
| malformed tenant ID | `400 Bad Request` |
| unknown tenant | `400 Bad Request` |

Tenant header validation is intentionally stricter than module `03`: trim the
header value, then accept only `[a-zA-Z0-9_-]{1,64}` before tenant lookup. This
keeps user-controlled tenant IDs out of logs and error messages in a predictable
shape. Issue #40 will add authorization; until then, this module demonstrates
routing only and must not be presented as production-safe tenant isolation.

Routing factory path:

| Case | Result |
|---|---|
| Reactor context has known tenant ID | route to matching target factory |
| Reactor context emits no tenant key | use configured default tenant factory |
| Reactor context emits unknown tenant ID | fail with `IllegalStateException` |

This split is intentional: WebFlux HTTP requests fail closed before database
access, while direct non-WebFlux examples can still show the Spring routing
factory default-target behavior.

Spring `AbstractRoutingConnectionFactory` treats an empty lookup-key publisher
and an unknown emitted lookup key differently: empty can use the default target,
while unknown fails when `setLenientFallback(false)` is configured.

## Coroutine and Reactor Context Bridge

The routing connection factory 읽는다: the tenant from Reactor subscriber context,
not directly from Kotlin coroutine context. This module must prove the bridge
because controllers are `suspend` functions and Exposed opens R2DBC connections
inside coroutine-backed transactions.

Required design:

1. `TenantFilter` validates `X-TENANT-ID` and writes the tenant ID to Reactor
   context before the controller executes.
2. `TenantTransactionExecutor` runs Exposed transactions against the explicit
   routing `R2dbcDatabase`.
3. `TenantRoutingConnectionFactory.determineCurrentLookupKey()` uses
   `Mono.deferContextual` or equivalent Spring routing hook to read
   `TenantContextKeys.TENANT_ID`.
4. The implementation must preserve the Reactor context through Spring WebFlux's
   Kotlin coroutine adapter and `kotlinx-coroutines-reactor` `ReactorContext`.
   If Exposed R2DBC drops that context, the transaction executor must wrap the
   transaction in a Reactor-context-preserving boundary before database access.
5. A parallel tenant WebFlux test must prove the Reactor context survives the
   suspend controller and Exposed transaction boundary by issuing concurrent
   Korean and English requests and asserting tenant-specific payloads.

If this bridge breaks, the routing factory may fall back to the default tenant
and silently hide cross-tenant bugs. That failure mode is P0 for this example.

## Transaction Boundary Rule

Controllers and repositories in this module must not call bare
`suspendTransaction { ... }`.

- All request-path Exposed work goes through `TenantTransactionExecutor`, which
  always supplies `db = tenantRoutingDatabase`.
- `DataInitializer` may use explicit tenant initializer databases during startup.
- 추가: an architecture test that scans this module's production Kotlin files and
  fails if bare `suspendTransaction` appears outside the allowed executor and
  initializer files. The initial implementation may use a strict regex plus
  explicit file allowlist:
  - allow `tenant/TenantTransactionExecutor.kt`.
  - allow `tenant/DataInitializer.kt`.
  - fail all other `src/main/kotlin/**/*.kt` files containing
    `\bsuspendTransaction\s*\(`, including aliased imports.
  Test code must use `TenantTransactionExecutor` or explicit tenant initializer
  databases; bare default-database transactions are not allowed in integration
  tests either.

This rule prevents Exposed default-database drift after multiple initializer
databases are connected.

## Resource Lifecycle

- `TenantConnectionFactoryRegistry` is the sole owner of tenant
  `ConnectionPool` instances.
- Initializer `R2dbcDatabase` instances reuse registry-owned pooled factories;
  they do not create or own independent pools.
- The registry implements Spring shutdown cleanup with `DisposableBean` or
  `@PreDestroy` and calls `ConnectionPool.dispose()` once per pool.
- 추가: a unit test with close-tracking fake or wrapped factories proving registry
  cleanup is invoked exactly once per registered pool.
- Connection pool settings must include bounded acquisition behavior:
  `maxCreateConnectionTime`, `maxAcquireTime`, `acquireRetry`, `maxIdleTime`,
  and a conservative `maxSize`.

Pools should be created eagerly during startup for this fixed-tenant example so
misconfigured tenant URLs fail before the first request.

## Testing Requirements

Focused tests must cover:

- context loading: controller, registry, routing database, initializer.
- registry contains distinct tenant factories for `korean` and `english`.
- routing factory fallback:
  - no Reactor context tenant uses default tenant.
  - unknown emitted tenant fails because lenient fallback is disabled.
- tenant validation:
  - reject `""`, whitespace-only, control-character, too-long, and unsupported
    character tenant IDs before registry lookup.
- HTTP routing:
  - `GET /actors` succeeds for every tenant.
  - same actor ID returns different fixture values for `korean` and `english`.
  - concurrent Korean and English requests never cross-route. 사용: at least 50
    concurrent requests over both tenants and assert the tenant fingerprint in
    every response.
  - missing header returns `400`.
  - blank header returns `400`.
  - unknown header returns `400`.
- initializer/resource assumptions:
  - each tenant database receives seed data.
  - registry closes closeable pools on shutdown, or the ownership note is
    explicitly documented and covered by a unit test.
- transaction boundary:
  - architecture test forbids bare `suspendTransaction` in production request
    code outside `TenantTransactionExecutor` and `DataInitializer`.

사용: `runSuspendIO`, JUnit 5, and bluetape4k assertions. Avoid
Testcontainers-backed tests in this module unless they become necessary during
implementation.

## CI/Nightly Coverage Decision

- Root CI already runs `./gradlew test` for PRs, so the new H2-only module is
  covered automatically.
- Nightly currently has explicit shards. Add
  `:04-connection-factory-per-tenant-spring-webflux:test` to the same
  PostgreSQL/MySQL shard group that already runs `:03-multitenant-spring-webflux:test`
  only if the new module supports those DBs.
- Initial decision: keep this module H2-only and document that root CI covers it
  through `./gradlew test`; do not add it to container-heavy Nightly shards in
  this PR unless implementation adds non-H2 profiles.

## Risks

- Exposed `R2dbcDatabase.connect(...)` can register multiple database instances.
  Controllers must use an explicit routing database bean to avoid accidental
  default-database drift after initializer databases are connected.
- `AbstractRoutingConnectionFactory` default fallback is useful for direct
  examples but dangerous on HTTP paths. `TenantFilter` must reject missing
  tenants before any database operation.
- Reactor context can be lost across coroutine or Exposed boundaries. The
  parallel tenant WebFlux test is the required regression guard.
- Tenant-per-connection-factory means tenant count directly affects pool count.
  README must call out this tradeoff.
- H2 in-memory URLs can accidentally share one DB if names collide. Tenant URLs
  must use distinct database names.
- This module has no tenant authorization. README must state that any client can
  choose a tenant header until issue #40 adds Spring Security authorization.

## Definition of Done

- `README.md` and `README.ko.md` explain when to choose
  connection-factory-per-tenant, how it differs from schema-per-tenant, and why
  authorization is out of scope until issue #40.
- New or changed public KDoc is English.
- `./gradlew projects --console=plain` shows the new module.
- `./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
  passes.
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
  passes.
- `git diff --check` passes.
- IDE diagnostics are clean when the worktree is open; otherwise record the
  fallback to compile/test evidence.
- Step 2-R, Step 3-R, and Step 6-R Claude advisor gates have `P0=0, P1=0`.

## Step 2-R Review Notes

### Local Multi-Perspective Review

Initial local review found no P0/P1, but Claude advisor identified missing
bridge, enforcement, and lifecycle requirements. Those findings are accepted and
folded into the spec.

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/ask-claude-issue-39-spec-review-20260522102112.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P0 | Reactor context to coroutine/Exposed bridge unspecified | Accepted | 추가함: bridge section and concurrent tenant regression requirement |
| P0 | `TenantTransactionExecutor` not enforced | Accepted | 추가함: transaction boundary rule and architecture test requirement |
| P1 | Initializer database/pool lifecycle unspecified | Accepted | 추가함: resource lifecycle section and cleanup test requirement |
| P1 | Tenant header trusted without auth/validation note | Accepted | 추가함: validation regex and issue #40 security note |
| P1 | Pool timeouts and shutdown unspecified | Accepted | 추가함: bounded pool settings and eager startup requirement |

Re-review artifact: `.omx/artifacts/ask-claude-issue-39-spec-rereview-20260522102420.md`

Re-review returned `P0=0`, `P1=0`, with P2/P3 improvements folded into the
bridge, architecture-test, test-discipline, and concurrency-test requirements.

Latest integrated Step 2-R status: `P0=0`, `P1=0`.
