# Issue 41 Tenant Onboarding and Provisioning R2DBC Design

## Context

Issue #41 is part of the Chapter 10 Spring WebFlux R2DBC multi-tenant epic
(#37). The matching `bluetape4k/exposed-workshop` issue #55 has the same core
shape: tenant creation, initialization, duplicate handling, and rollback.

Current Chapter 10 R2DBC modules already cover:

- `03-multitenant-spring-webflux`: tenant discriminator in shared tables.
- `04-connection-factory-per-tenant-spring-webflux`: static
  connection-factory-per-tenant routing with `TenantTransactionExecutor`.
- `05-spring-security-tenant-authorization-spring-webflux`: authenticated
  tenant authorization before routing context is written.

Issues #38, #39, and #40 deliberately left dynamic tenant onboarding and
provisioning out of scope. #41 closes that gap without taking over aggregate
chapter wiring, which remains #42, or Ktor examples, which remain #33/#69.

## External Evidence

- Spring Framework `AbstractRoutingConnectionFactory` routes `create()` calls
  through `determineCurrentLookupKey()` and a target `ConnectionFactory` map.
  It supports a default target, `setLenientFallback(false)`, and explicit
  initialization after targets are set.
  Source: Context7 `/websites/spring_io_spring-framework_current_javadoc-api`,
  `AbstractRoutingConnectionFactory`.
- Exposed 1.1.1 R2DBC uses `suspendTransaction {}` for coroutine-based
  non-blocking DB work. It also supports `suspendTransaction(db = ...)` and
  `R2dbcDatabase.connect(connectionFactory = ..., databaseConfig = ...)`, with
  explicit dialect configuration when a `ConnectionFactory` is supplied.
  Source: Context7 `/jetbrains/exposed/1.1.1`, transactions and
  connection-factory docs.

## Local Reuse Evidence

- `04-connection-factory-per-tenant-spring-webflux` already proves:
  - registry-owned tenant pools.
  - an `AbstractRoutingConnectionFactory` subclass.
  - a `TenantTransactionExecutor` that bridges Reactor context into Exposed
    `suspendTransaction`.
  - architecture tests that prevent bare request-path `suspendTransaction`.
- `05-spring-security-tenant-authorization-spring-webflux` already proves:
  - the same routing contract can be composed with WebFlux security.
  - only the authorized-tenant filter writes the request tenant context.
  - `DataInitializer` may be an allowlisted provisioning/seed boundary.
- Existing qmd lessons require:
  - keep `TenantTransactionExecutor` as the only request-path Exposed
    transaction boundary unless the architecture test allowlist is deliberately
    updated.
  - reuse registry-owned pools for `R2dbcDatabase` instances instead of
    independent initializer pools.
  - update `.github/workflows/Examples.yml` when adding chapter examples.

## Goals

1. Add a new Chapter 10 module:
   `10-multi-tenant/06-tenant-onboarding-spring-webflux`.
2. Demonstrate dynamic tenant onboarding over Spring WebFlux + Exposed R2DBC:
   - persist tenant metadata in a registry database.
   - provision a tenant-specific R2DBC database/schema resource.
   - create tenant tables and seed optional starter data.
   - register the new tenant for request-time routing.
3. Demonstrate failure behavior:
   - duplicate tenant ID returns a deterministic conflict.
   - provisioning failure cleans up metadata and runtime registration.
   - failed onboarding does not expose the tenant to request routing.
4. Preserve isolation:
   - tenant-aware request code uses `TenantTransactionExecutor`.
   - data created under one onboarded tenant is not visible under another.
5. Update English/Korean README files and include a generated PNG diagram asset.
6. Update CI example workflow coverage and record the Nightly coverage decision.
7. Add a deliberately simple admin guard for the onboarding endpoint so the
   privileged resource-creation API is not anonymous in the example.

## Non-goals

- Do not implement Ktor tenant onboarding; that belongs to #33/#69.
- Do not build production-grade distributed provisioning, migrations, or
  cross-node registry synchronization.
- Do not build a production identity provider or authorization server.
- Do not implement tenant offboarding/deletion. This issue covers onboarding
  and failure cleanup only.
- Do not wire aggregate chapter documentation beyond the new module README and
  directly required root README links; issue #42 owns aggregate wiring.
- Do not add non-H2 Testcontainers coverage unless a later issue asks for it.
- Do not introduce a shared bluetape4k library API. This remains a workshop
  example module.

## Proposed Module Shape

```text
10-multi-tenant/06-tenant-onboarding-spring-webflux/
├── README.md
├── README.ko.md
├── build.gradle.kts
├── src/main/kotlin/exposed/r2dbc/multitenant/onboarding/
│   ├── TenantOnboardingApp.kt
│   ├── config/
│   │   └── TenantOnboardingR2dbcConfig.kt
│   ├── controller/
│   │   ├── ActorController.kt
│   │   └── TenantOnboardingController.kt
│   ├── domain/
│   │   ├── model/
│   │   └── repository/
│   └── tenant/
│       ├── TenantConnectionFactoryRegistry.kt
│       ├── TenantContextKeys.kt
│       ├── TenantIdResolver.kt
│       ├── TenantProvisioner.kt
│       ├── TenantRegistryRepository.kt
│       ├── TenantRoutingConnectionFactory.kt
│       └── TenantTransactionExecutor.kt
├── src/main/resources/application.yml
└── src/test/kotlin/exposed/r2dbc/multitenant/onboarding/
```

Use the actor/movie domain from #04/#05 so the workshop remains comparable
across strategies. Keep public class names specific to `onboarding` to avoid
cross-module ambiguity.

## Tenant Contract

Tenant IDs are external request/API values.

- Format: lowercase slug, `[a-z][a-z0-9-]{1,30}`.
- Canonical storage: exact slug.
- Invalid tenant ID: `400 Bad Request`.
- Unknown tenant on tenant-scoped actor APIs: `404 Not Found`.
- Duplicate onboarding request: `409 Conflict`.

Use bluetape4k validation helpers at API boundaries where practical. Use
`check()` only for internal invariants.

Represent validated tenant IDs as a value type, not raw strings:

```kotlin
@JvmInline
value class TenantId(val value: String)
```

`TenantId` construction owns regex validation. `TenantProvisioner` and routing
infrastructure accept only `TenantId` after the controller boundary, preventing
unvalidated strings from being interpolated into R2DBC URLs or registry keys.

## Onboarding Authorization

### Admin Authentication

`POST /api/tenants` is a privileged provisioning API. The module will use a
small workshop-only admin header guard instead of duplicating the full #40
security module:

- request header: `X-ADMIN-TOKEN`.
- configured value: `app.onboarding.admin-token`.
- missing or wrong token: `401 Unauthorized`.
- token comparison should use a constant-time byte comparison helper rather than
  plain string equality.

The README must state that production systems should replace this guard with
real authentication and authorization. Tenant-scoped actor APIs still use
`X-TENANT-ID` for routing and do not treat that header as authorization.

## Registry and Provisioning Model

### Registry Database

Add a small registry database/table that stores onboarded tenant metadata:

- `tenant_id`
- `display_name`
- `database_name`
- `r2dbc_url`
- `status`
- `created_at`

The registry database is not tenant-routed. Registry access lives in
`TenantRegistryRepository` and uses an explicitly named `R2dbcDatabase`.

Registry constraints:

- `tenant_id` is `UNIQUE NOT NULL`.
- `status` is stored as a string enum with values `PROVISIONING`, `ACTIVE`,
  or `FAILED`; do not store enum ordinals.
- reservation is an insert of `PROVISIONING`; a unique-constraint conflict maps
  to `DuplicateTenantException`.
- duplicate concurrent onboarding must fail at reservation and must not proceed
  to pool, database, schema, or runtime registry creation for the losing
  request.
- startup recovery marks stale `PROVISIONING` rows as `FAILED` for this
  workshop module instead of trying cross-process recovery.
- startup recovery marks stale `ACTIVE` rows as `FAILED` for this H2 in-memory
  workshop module, because tenant in-memory databases are single JVM lifetime
  resources and cannot be trusted after restart.
- runtime provisioning failures delete the just-created metadata row after
  cleanup so retrying the same tenant ID is possible.
- stale `FAILED` rows created by startup recovery are retryable: a new
  onboarding request may replace a `FAILED` row with a fresh `PROVISIONING`
  reservation in one guarded registry operation.
- the guarded reserve operation is implemented under a per-tenant JVM `Mutex`
  and one registry `suspendTransaction`: update `FAILED -> PROVISIONING` first;
  if no row is updated, select the current status; `ACTIVE` or `PROVISIONING`
  maps to `DuplicateTenantException`, absent row inserts a new `PROVISIONING`
  row, and any insert unique-constraint exception maps to
  `DuplicateTenantException` as a last-resort cross-process safety net.
  Multi-node retry coordination is a production concern and remains out of
  scope.
- if the registry database is unavailable at startup, fail application startup
  rather than serving tenant-routed endpoints against incomplete runtime state.
- if the registry database becomes unavailable during onboarding, map the
  operation to `TenantProvisioningException`/`500`; existing runtime routing for
  already registered tenants is unaffected.
- stored URLs are H2-only in this example; the README must warn that production
  credentials must be externalized or encrypted, not stored as plaintext.

### Tenant Resource

Use H2 in-memory R2DBC databases for the workshop implementation:

```text
r2dbc:h2:mem:///tenant_onboarding_<tenant_id>_<safe_suffix>;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

This keeps the example deterministic and avoids container-heavy tests while
still showing the production flow shape: metadata -> resource creation ->
schema creation -> runtime registration.

`safe_suffix` is a UUID-derived lowercase alphanumeric suffix generated by the
service, not derived from caller input. On cleanup, the provisioner drops tenant
tables/catalog objects before closing the pool so same-JVM retry tests cannot
observe stale H2 rows.

### Provisioning Steps

`TenantProvisioner.onboard(request)` executes these steps:

1. Validate and reserve tenant metadata in the registry.
2. Create a pooled `ConnectionFactory` for the tenant URL.
3. Connect Exposed `R2dbcDatabase` through that pooled factory using
   `R2dbcDatabaseConfig { explicitDialect = H2Dialect() }`.
4. Run `SchemaUtils.create(...)` for actor/movie tables inside a provisioning
   `suspendTransaction(db = tenantDatabase)`.
5. Seed optional starter data.
6. Register the tenant in `TenantConnectionFactoryRegistry`.
7. Mark metadata `ACTIVE`.

Concurrency and cleanup rules:

- duplicate concurrent onboarding is settled by the registry unique constraint;
  exactly one request may reserve a tenant ID.
- `maxTenants` is checked before reservation by counting `ACTIVE` and
  `PROVISIONING` rows under a global onboarding mutex plus the per-tenant
  guarded reserve path; if the cap is reached, no metadata row is written.
- after metadata reservation, any failure or cancellation triggers compensating
  cleanup in reverse order: deregister runtime tenant, close the tenant pool,
  and mark/delete registry metadata.
- runtime failure cleanup deletes the metadata row; startup recovery may keep a
  `FAILED` row for crash diagnosis and retry replacement.
- cleanup runs in `withContext(NonCancellable)` so partially created resources
  are not leaked after coroutine cancellation.
- `CancellationException` is rethrown after cleanup; other failures are wrapped
  in `TenantProvisioningException`.
- the controller returns a deterministic 5xx problem response for simulated
  provisioning failure.

The module should include a test-only failure hook in the provisioner so failure
cleanup can be verified without relying on brittle H2 URL errors.
Implement that hook as a constructor-injected `ProvisioningFailureSimulator`
bean with a no-op production implementation and a test override. It should use
an enum failure point so cleanup branches are testable:

```kotlin
enum class ProvisioningFailurePoint {
    AFTER_RESERVE,
    AFTER_POOL,
    AFTER_SCHEMA,
    AFTER_SEED,
    AFTER_REGISTER,
}
```

Do not use reflection, mutable static state, hidden global toggles, or any
external property/env switch that can activate failure points in production.

Provisioning should use explicit timeouts:

- overall onboarding timeout default: 60 seconds.
- reservation timeout default: 10 seconds.
- schema creation timeout default: 30 seconds.
- acquire timeout default: 5 seconds.
- properties live under `app.onboarding.*`.

## Runtime Routing Model

Reuse the #04/#05 request-time pattern:

- `TenantIdResolver` reads `X-TENANT-ID`.
- `TenantRoutingConnectionFactory.determineCurrentLookupKey()` reads Reactor
  context.
- `TenantTransactionExecutor.execute { ... }` bridges Reactor context into
  Exposed `suspendTransaction(db = tenantRoutingDatabase)`.
- Actor repositories do not call `suspendTransaction` directly.

Use the same Reactor bridge shape as #04/#05: the WebFlux handler writes
`TenantContextKeys.TENANT_ID` into Reactor context, and
`TenantTransactionExecutor` preserves the `ReactorContext` coroutine element
while awaiting this boundary:

```kotlin
Mono.deferContextual { contextView ->
    mono(coroutineContext + ReactorContext(contextView)) {
        suspendTransaction(db = tenantRoutingDatabase) { statement() }
    }
}.awaitSingle()
```

Dynamic registration means `TenantConnectionFactoryRegistry` must support safe
runtime insertion and removal, not only static constructor maps. It should own
pool shutdown and expose read-only target factories for routing initialization.

Because Spring's `AbstractRoutingConnectionFactory` resolves configured targets
during initialization, this dynamic module will not extend it. Instead,
`TenantRoutingConnectionFactory` implements `ConnectionFactory` directly:

1. read the tenant key from Reactor context.
2. validate/convert it to `TenantId`.
3. ask `TenantConnectionFactoryRegistry` for the active tenant factory.
4. delegate `create()` to that factory.
5. return `Mono.error(UnknownTenantException)` for missing or inactive tenants.
6. implement `getMetadata()` with static H2-oriented metadata that does not
   read Reactor context, so Spring/R2DBC startup probes cannot fail without a
   tenant.
   - `ConnectionFactoryMetadata.getName()` returns the exact constant `"H2"`.

`TenantConnectionFactoryRegistry` implements `DisposableBean`; `destroy()`
closes every active tenant pool independently. Registration and removal are
thread-safe through a `ConcurrentHashMap<TenantId, ConnectionPool>` plus a
`Mutex` around compound reserve/register/remove decisions. A configurable
`maxTenants` guard prevents unbounded pool creation and returns
`429 Too Many Requests` when exceeded.

The registry owns both the tenant `ConnectionPool` and the cached tenant
`R2dbcDatabase` wrapper. Removing a tenant evicts both references and closes the
pool after dropping tenant objects during provisioning cleanup.

All Exposed `R2dbcDatabase` beans in this module use explicit H2 dialect
configuration:

- `registryDatabase`
- `tenantRoutingDatabase`
- every provisioned tenant database wrapper

Use explicit bean qualifiers to avoid accidental database injection:

- `@Qualifier("registryDatabase")`
- `@Qualifier("tenantRoutingDatabase")`

## Architecture Test Policy

Request-path Exposed work must still go through `TenantTransactionExecutor`.
Direct `suspendTransaction(db = ...)` is allowed only in explicit infrastructure
boundaries:

- `TenantProvisioner` for schema creation/seed during onboarding.
- `TenantRegistryRepository` for registry database metadata access.
- startup initialization/recovery code for registry schema and stale
  `PROVISIONING` cleanup.

`ArchitectureTest` must assert that no controller, actor repository, or routing
class calls bare `suspendTransaction`. Implement this with the existing
Kotlin-source scan style used by #04/#05 `ArchitectureTest`. This is a
deliberate allowlist update from #04/#05, not a broad exception.

## API Surface

### Tenant Onboarding

```http
POST /api/tenants
Content-Type: application/json

{
  "tenantId": "korean",
  "displayName": "Korean Cinema"
}
```

Responses:

- `201 Created` with tenant metadata after successful provisioning.
  - response body includes `tenantId`, `displayName`, `status`, and
    `createdAt`.
  - response body excludes raw `r2dbc_url`.
- `400 Bad Request` for invalid tenant IDs or blank display names.
- `401 Unauthorized` for missing or wrong `X-ADMIN-TOKEN`.
- `409 Conflict` for duplicate tenant IDs.
- `500 Internal Server Error` with rollback evidence for simulated
  provisioning failure.
- `429 Too Many Requests` when `app.onboarding.max-tenants` is exceeded.

### Tenant-scoped Actors

Keep the familiar actor read/write endpoints from #04/#05. They operate only
after a tenant has been onboarded and route through `X-TENANT-ID`.

## Error Contract

Use explicit exception types for controller mapping:

- `InvalidTenantIdException` -> `400`
- `DuplicateTenantException` -> `409`
- `UnknownTenantException` -> `404`
- `TenantProvisioningException` -> `500`

Do not leak raw R2DBC URLs or stack traces in HTTP responses.

## Tests

Required tests:

- `TenantOnboardingControllerTest`
  - successful onboarding returns `201` and can serve tenant-scoped actor data.
  - duplicate onboarding returns `409`.
  - invalid tenant ID returns `400`.
  - unknown tenant on actor APIs returns `404`.
  - simulated provisioning failure returns `500`.
  - failed provisioning leaves no tenant metadata, no registry entry, and no
    request-routing access.
  - missing or wrong `X-ADMIN-TOKEN` returns `401`.
  - tenant-scoped actor APIs do not accept `X-ADMIN-TOKEN` as tenant
    authorization.
  - exceeding `app.onboarding.max-tenants` returns `429`.
  - two concurrent requests for the same tenant produce exactly one `201` and
    one `409`, with no leaked pool for the losing request.
- `TenantProvisionerTest`
  - creates metadata, schema, pool/database, and runtime registration.
  - cleans up pool/registry/metadata on failure after pool creation.
  - verifies cleanup for `AFTER_RESERVE`, `AFTER_POOL`, `AFTER_SCHEMA`,
    `AFTER_SEED`, and `AFTER_REGISTER` failure points.
  - cleans up after cancellation and rethrows `CancellationException`.
  - timeout during schema creation triggers cleanup.
  - enforces `maxTenants`.
  - `N + 1` concurrent distinct-tenant requests with `maxTenants=N` produce
    exactly `N` successes and one `429`.
  - allows retry after a runtime provisioning failure deleted metadata.
  - registry outage during reservation maps to `TenantProvisioningException`
    with no partial state.
  - registry outage after reservation maps to `TenantProvisioningException`
    with cleanup.
- `TenantIdTest`
  - accepts valid lowercase slugs.
  - rejects blank, uppercase, slash, dot, colon, and overlength values.
- `TenantIsolationTest`
  - two onboarded tenants keep actor rows isolated.
  - a row created under tenant A is not visible under tenant B.
  - a newly onboarded tenant is immediately routable with `X-TENANT-ID`.
- `TenantTransactionExecutorTest`
  - preserves Reactor context.
  - propagates `CancellationException` instead of swallowing it.
- `TenantConnectionFactoryRegistryTest`
  - shutdown closes every registered pool.
  - remove closes only the removed tenant pool.
- `TenantRegistryRecoveryTest`
  - startup marks stale `PROVISIONING` rows as `FAILED`.
  - startup marks stale `ACTIVE` rows as `FAILED` for this H2 in-memory module.
  - retry can replace a stale `FAILED` row with a fresh `PROVISIONING`
    reservation.
  - registry database startup failure fails application startup.
- `ArchitectureTest`
  - production request code does not call bare `suspendTransaction`.
  - only `TenantProvisioner`, `TenantRegistryRepository`, and initialization
    infrastructure are allowlisted for direct `suspendTransaction(db = ...)`.
  - actor repositories and controllers never call `suspendTransaction`.

Use `runSuspendIO` for real R2DBC IO. Keep tests H2-only and serial through the
existing Gradle/JUnit test mutex patterns.

## Documentation

Add `README.md` and `README.ko.md` for the new module:

- when to choose onboarding/provisioning:
  - choose it when tenants must be created at runtime and receive isolated
    resources.
  - choose #03 when shared-table tenancy is enough.
  - choose #04 when tenants are known at deploy time and need separate
    connection factories.
  - choose #05 when tenant routing must be authorized before DB access.
- explain failure cleanup and duplicate handling.
- include a production-credentials warning in the "Registry and resource model"
  section: non-H2 URLs may contain credentials and must not be stored plaintext
  in production.
- include a generated PNG diagram under `docs/assets/readme-diagrams/` and
  reference the same relative path from both READMEs.

The diagram should be a committed raster PNG, generated in the same style as
the repository's existing README diagram assets. Keeping the source SVG beside
the PNG is acceptable if the renderer needs it, but README files must reference
the PNG.

Diagram asset path:

```text
docs/assets/readme-diagrams/issue-41-tenant-onboarding-r2dbc-01.png
```

## CI/Nightly Decision

- Update `.github/workflows/Examples.yml` path filters, chapter 10 Gradle task
  list, and uploaded artifacts for
  `:06-tenant-onboarding-spring-webflux:test`.
- Run `actionlint` after workflow changes.
- Do not add the new module to Nightly matrix in this issue unless the existing
  Chapter 10 shard is already broadened. The module is H2-only and covered by
  Examples; record this decision in README and lesson.
- Verify `./gradlew projects` discovers the module.

The recorded Nightly decision for this issue is: Examples workflow coverage is
required; Nightly matrix expansion is deferred to #42 because this module is
H2-only and not container-heavy.

## Observability and Runtime Limits

The module should log one structured event per onboarding step with `tenantId`,
step name, duration, and outcome using `KLoggingChannel`. Do not log raw R2DBC
URLs. Use explicit pool defaults for each onboarded tenant:

- `app.onboarding.pool.initial-size`: 1.
- `app.onboarding.pool.max-size`: 4.
- `app.onboarding.pool.acquire-timeout`: 5 seconds.
- `app.onboarding.pool.max-idle-time`: 30 seconds.

These settings are intentionally small so a workshop run cannot silently create
large per-tenant pools.

The README should call out production observability gaps such as audit storage,
advanced metrics, and admin-token rotation as intentional workshop omissions.
The implementation should still expose a small baseline: one Micrometer counter
for onboarding failures and one gauge for active tenants.

## Acceptance Criteria

- New module builds and focused tests pass with `-PuseDB=H2`.
- Tenant onboarding persists metadata, provisions schema/resources, and exposes
  the tenant for routing.
- Duplicate tenant and invalid tenant paths are deterministic.
- Failure cleanup removes runtime and metadata traces of failed onboarding.
- Tenant data remains isolated across two dynamically onboarded tenants.
- Request-path Exposed access goes through `TenantTransactionExecutor`.
- README English/Korean docs include the PNG diagram and strategy guidance.
- `.github/workflows/Examples.yml` includes the module in path filters, chapter
  10 test tasks, and artifact upload paths, and passes `actionlint`.
- README and lesson record that Nightly expansion is deferred to #42.
- Step 2-R, Step 3-R, and Step 6-R Codex + Claude gates close with `P0=0`,
  `P1=0`.

## Open Decisions

- Dynamic routing will use a direct `ConnectionFactory` implementation, not a
  mutable `AbstractRoutingConnectionFactory` target map.
- Onboarding endpoint protection will use the minimal admin-header guard in
  this module. Full identity-provider integration remains #40/non-goal.

## Step 2-R Review Notes

### Codex Six-Tier Review Iteration 1

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Tenant ID was raw-string based and URL interpolation relied on controller validation. | Accepted | Added `TenantId` value type requirement and provisioner raw-string ban. |
| P1 | Privileged onboarding endpoint was unauthenticated. | Accepted | Added workshop admin-header guard. |
| P1 | Concurrent duplicate onboarding and unique constraint behavior were underspecified. | Accepted | Added registry unique constraint and concurrency test. |
| P1 | Dynamic routing over `AbstractRoutingConnectionFactory` was ambiguous. | Accepted | Switched spec to direct `ConnectionFactory` implementation. |
| P1 | Pool shutdown and cancellation cleanup were underspecified. | Accepted | Added `DisposableBean`, reverse cleanup, `NonCancellable` cleanup, and tests. |

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-spec-issue-41-tenant-onboarding-20260522212150.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P0 | Value-type validation, onboarding auth, duplicate race, routing design, arch allowlist, pool shutdown, and cancellation cleanup were blocking gaps. | Accepted | Spec sections updated for all seven gaps. |
| P1 | Stale provisioning status, plaintext URL warning, timeout, tenant cap, pool sizing, observability, failure-hook wiring, qualifier, isolation, and dynamic visibility were missing or weak. | Accepted | Spec sections/tests updated. |
| P1 | Rerun found test coverage and architecture allowlist policy needed to be more explicit. | Accepted | Added `Architecture Test Policy`, recovery/unknown/max/admin tests, exact diagram path, and CI/Nightly acceptance wording. |
| P1 | Second rerun found timeout inversion and ambiguous `FAILED` row retry semantics. | Accepted | Overall timeout increased to 60s, reservation timeout added, runtime failure deletes metadata, stale `FAILED` rows are retryable. |
| P2 | Second rerun requested exact Reactor bridge, H2 cleanup, duplicate ordering, failure-point enum, pool property names, offboarding non-goal, and architecture-test mechanism. | Accepted | Spec updated to pin each item before planning. |
| P1 | Third rerun found retry atomicity, custom `ConnectionFactory.getMetadata()`, and Exposed dialect binding were underspecified. | Accepted | Added per-tenant mutex plus reserve transaction, static H2 metadata, and `H2Dialect` config requirement. |
| P2 | Third rerun requested R2dbcDatabase lifecycle, timeout cleanup test, failure simulator prod guard, and baseline metrics. | Accepted | Added cache/evict ownership, timeout test, no prod activation switch, counter/gauge baseline. |
| P1 | Fourth rerun found explicit dialect missing on registry/routing DBs, reserve fallback missing SELECT/unique catch, and H2 restart `ACTIVE` semantics undefined. | Accepted | Added explicit dialect for all DB beans, reserve SELECT/unique fallback, and startup `ACTIVE -> FAILED` H2 policy. |
| P2 | Fifth rerun passed gate and recommended tightening `maxTenants`, admin auth, registry outage, response DTO, and suffix language. | Accepted | Added global onboarding mutex, Admin Authentication heading, outage tests, response DTO exclusion, and UUID-derived suffix. |
| P2 | Production-grade restart recovery and non-H2 coverage would expand scope. | Rejected | H2-only workshop and stale `PROVISIONING` -> `FAILED` sweep recorded. |

### Integrated Step 2-R Findings

| Priority | Count | Status |
|---|---:|---|
| P0 | 0 | Closed after Claude rerun 5. |
| P1 | 0 | Closed after Claude rerun 5. |
| P2 | 1 | Non-H2/container coverage deferred by scope; actionable P2s applied. |
| P3 | 0 | None. |

## Step 2-R Checklist Completion Report

| Item | Status | Notes |
|------|--------|-------|
| Developer perspective reviewed | Done | Codex review found validation, routing, cleanup, and concurrency gaps. |
| Security perspective reviewed | Done | Admin guard, raw URL leakage, value type, and failure simulator production activation addressed. |
| Ops/SRE perspective reviewed | Done | Shutdown, retry, timeout, startup recovery, and pool caps addressed. |
| User/caller perspective reviewed | Done | README strategy guidance, response DTO, duplicate/invalid/error contract addressed. |
| Claude Code Opus advisor artifact exists | Done | Latest gate artifact `.omx/artifacts/claude-spec-issue-41-tenant-onboarding-rerun5-20260522213726.md`. |
| P0/P1 convergence verified | Done | Latest Claude gate verdict: PASS, `P0=0`, `P1=0`. |

## Step 1 Checklist Completion Report

| Item | Status | Notes |
|------|--------|-------|
| Target repository confirmed | Done | Worktree branch `feat/issue-41-tenant-onboarding` at `bbf5346`. |
| Relevant memory anchors searched | Done | qmd query found #38/#39/#40 specs and #39/#40 lessons. |
| Review-only boundary recorded | N/A | User requested implementation workflow, not review-only. |
| Concrete artifact inspected | Done | GitHub issue #41 and exposed-workshop #55 inspected. |
| User intent and boundaries clear | Done | Work order and PNG diagram requirement are explicit. |
| Ambiguous requirements clarified | Done | Ktor coroutine cache separated into #69 before this work. |

## Step 1-R Checklist Completion Report

| Item | Status | Notes |
|------|--------|-------|
| Official docs checked | Done | Spring routing and Exposed R2DBC docs checked through Context7. |
| Current repo reuse searched | Done | Chapter 10 modules #03/#04/#05 and workflow wiring inspected. |
| Third-party API assumptions checked | Done | Routing initialization and Exposed `suspendTransaction(db=...)` checked. |
| Adopt/borrow/skip decisions recorded | Done | Reuse #04/#05 routing/executor; skip Ktor, production auth, Nightly expansion. |
| Technical constraints identified | Done | Kotlin-only, H2 R2DBC, Spring WebFlux, Exposed 1.1.1, JDK 21. |
| Research summary ready | Done | Captured in Context, External Evidence, Local Reuse Evidence. |
