# Issue 33 Ktor R2DBC Multi-Tenant Design

## Context

Issue #33 adds the Ktor equivalent of the chapter 10 schema-per-tenant R2DBC
example. The closest local implementation is
`10-multi-tenant/03-multitenant-spring-webflux`, which already proves tenant
schema initialization, `X-TENANT-ID` validation, per-tenant sample data, and
tenant-isolated actor queries. The closest Ktor shape is
`12-production-integration/02-ktor-production-integration`, which proves the
repo's Ktor module style, JSON plugin setup, route tests, and Ktor test host
usage.

The matching `exposed-workshop` issue #46 has the same workshop shape for
blocking Exposed. This R2DBC module must stay coroutine-first and avoid
ThreadLocal tenant state.

## Goals

1. Add `10-multi-tenant/07-multitenant-ktor`.
2. Demonstrate schema-per-tenant routing through Ktor request handling:
   - resolve tenant from `X-TENANT-ID`;
   - reject missing, blank, or unknown tenant values with `400`;
   - keep tenant state scoped to the Ktor call/coroutine request path;
   - execute Exposed R2DBC work only through tenant-aware transaction helpers.
3. Reuse the actor/movie domain shape from
   `03-multitenant-spring-webflux` so chapter 10 comparisons stay direct.
4. Add focused tests for successful tenant reads, missing/invalid tenant
   handling, lowercase header compatibility, and same-ID cross-tenant
   isolation.
5. Add English and Korean README files plus committed PNG diagram assets.
6. Record CI/Nightly coverage decisions without taking over aggregate
   documentation owned by #36.

## Non-Goals

- Do not implement connection-factory-per-tenant routing; #35 covers routing
  datasource behavior.
- Do not implement tenant authorization or onboarding.
- Do not change existing Spring WebFlux chapter 10 modules except chapter
  links required for discoverability.
- Do not add Java code or new external dependencies outside the existing Ktor,
  Exposed R2DBC, H2, and shared test stack.
- Do not use ThreadLocal, ReactorContext, or Spring infrastructure in the Ktor
  request path.

## Proposed Module Shape

```text
10-multi-tenant/07-multitenant-ktor/
├── README.md
├── README.ko.md
├── build.gradle.kts
├── src/main/kotlin/exposed/r2dbc/multitenant/ktor/
│   ├── KtorMultitenantApplication.kt
│   ├── routes/
│   │   └── ActorRoutes.kt
│   ├── config/
│   │   └── KtorMultitenantPlugins.kt
│   ├── domain/
│   │   ├── model/
│   │   └── repository/
│   └── tenant/
│       ├── DataInitializer.kt
│       ├── SchemaSupport.kt
│       ├── TenantId.kt
│       ├── TenantPlugin.kt
│       ├── TenantTransaction.kt
│       └── Tenants.kt
├── src/main/resources/application.conf
└── src/test/kotlin/exposed/r2dbc/multitenant/ktor/
```

## Tenant Contract

- Request header: `X-TENANT-ID`.
- Valid tenant values: `korean`, `english`.
- Missing or blank header: `400 Bad Request`.
- Unknown header: `400 Bad Request`.
- Header name matching should rely on Ktor's case-insensitive header lookup.
- Tenant state is represented by a validated `Tenants.Tenant` enum, never a
  raw string past the plugin boundary.
- `X-TENANT-ID` is a workshop routing input, not authentication or
  authorization. README files must warn that production systems must bind
  tenant routing to an authenticated principal or signed tenant claim.
- The raw header value must never be interpolated into schema names or passed
  to `SchemaUtils.setSchema`; only validated `Tenants.Tenant` enum instances
  may cross the plugin boundary.

## Request and Transaction Design

Ktor request handling uses a small application plugin:

1. Read `X-TENANT-ID`.
2. Validate it against `Tenants.findById`.
3. Store the resolved enum in `call.attributes`.
4. Route handlers read the tenant through `call.currentTenant()`.
5. `suspendTransactionWithTenant` receives the tenant explicitly and calls
   `SchemaUtils.setSchema(getSchemaDefinition(tenant))` at transaction start.

This avoids shared mutable context and avoids lifecycle cleanup requirements
that come from ThreadLocal. The per-call `Attributes` scope is discarded with
the Ktor call.

`call.currentTenant()` exposes the only `AttributeKey<Tenants.Tenant>` access.
It must not use the WebFlux module's default-tenant fallback. If the attribute
is absent after plugin installation, the extension throws `IllegalStateException`
because that is a programmer error.

The Ktor module intentionally keeps its tenant transaction helper local instead
of promoting shared code from `03-multitenant-spring-webflux`. These chapter
examples are standalone teaching modules and already use different request
context carriers: ReactorContext for WebFlux and call attributes for Ktor. The
README must call this out so future chapter wiring does not treat the duplicate
helper as accidental drift.

## Database and Initialization

- Use a single H2 R2DBC database by default:
  `r2dbc:h2:mem:///ktor-multitenant;DB_CLOSE_DELAY=-1;USER=sa;`.
- At module startup, create one schema per tenant and seed language-specific
  actor/movie rows.
- Keep initialization idempotent: if actor rows already exist for a tenant, do
  not duplicate them.
- Fail Ktor application startup if schema creation or seed initialization fails
  for any tenant. Serving partial tenant state is not acceptable for this
  example.
- All request-path Exposed access must go through
  `suspendTransactionWithTenant`; direct `suspendTransaction` is limited to
  schema creation and data initialization helpers.

## Error Contract

Install Ktor `StatusPages` and `ContentNegotiation` so tenant validation errors
return JSON, not the default text body.

```json
{
  "code": "INVALID_TENANT",
  "message": "Missing tenant id header: X-TENANT-ID"
}
```

Tests must assert both HTTP status and response body for missing, blank, and
unknown tenant cases.

## Required Tests

- Successful `/actors` and `/actors/{id}` reads for every tenant.
- Missing header, empty header, whitespace header, and unknown header all return
  `400` with `INVALID_TENANT`.
- Lowercase `x-tenant-id` resolves the same tenant.
- Duplicate `X-TENANT-ID` values are rejected with `400` unless they are exactly
  the same value after trimming.
- Same actor ID returns tenant-specific names across `korean` and `english`.
- Write-isolation proof: insert a tenant-specific actor under one tenant and
  verify the other tenant cannot read that row.
- Rapid tenant alternation under a constrained R2DBC pool proves schema reset on
  the transaction connection: `korean`, `english`, `korean`, `english`, ...
- Overlapping Ktor `testApplication` requests for two tenants prove concurrent
  coroutine request handling does not leak tenant state.
- Tests use Ktor `testApplication` and Ktor client APIs; production paths must
  not use `runBlocking`.

## Documentation and Diagram

- Add module `README.md` and `README.ko.md`.
- Link the module from `10-multi-tenant/README.md` and
  `10-multi-tenant/README.ko.md`.
- Add a PNG diagram under `docs/images/readme-diagrams/` and reference the
  same relative path from both README files.
- Explain why Ktor uses call-scoped attributes instead of ReactorContext.
- Explain the schema switch cost and link forward to #35 for connection-factory
  routing when pool-level routing is a better production fit.

## Verification Targets

- `./gradlew projects --console=plain`
- `repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- `./gradlew :07-multitenant-ktor:compileKotlin --warning-mode all --console=plain`
- `git diff --check`
- README diagram path and PNG existence check.
- IntelliJ diagnostics if the worktree is open; otherwise record fallback to
  compile/test/static scans.

## Risks

- Schema state is connection state. Keep `SchemaUtils.setSchema(...)` inside
  the tenant transaction boundary and cover alternating tenant calls in tests.
- Ktor plugin errors must be rendered as JSON/HTTP `400`, not unhandled server
  failures.
- R2DBC pool reuse must not leak prior tenant schema state. Constrained-pool
  alternating tests are required evidence.
- The module name must be reflected in Gradle discovery and in README commands;
  `settings.gradle.kts` uses `includeModules("10-multi-tenant", false, false)`.
- #36 owns aggregate docs/verification, so this issue should only add the new
  module to chapter docs and record the future CI decision.

## Step 2-R Review Notes

### Codex Spec Review

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P2 | Error response body shape was implicit. | Accepted | Added `INVALID_TENANT` JSON contract and body assertions. |
| P2 | Startup initialization failure behavior was implicit. | Accepted | Added fail-fast startup rule. |

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-issue-33-spec-review-20260523045833.md`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Tenant header trust boundary unspecified. | Accepted | README/spec must state header is not auth and production must bind tenant to identity. |
| P1 | Connection-pool schema-state leakage needs explicit test. | Accepted | Added constrained-pool tenant alternation and concurrent request tests. |
| P1 | Duplicate tenant transaction helper decision was silent. | Accepted | Keep local duplication intentionally; document standalone module rationale. |
| P1 | Test matrix missed concurrency and write isolation. | Accepted | Added write-isolation and overlapping request tests. |
| P2 | Raw header must never reach schema selection. | Accepted | Added enum-only boundary invariant. |
| P2 | Ktor current tenant must not default. | Accepted | Added no-fallback extension contract. |

Claude rerun artifact:
`.omx/artifacts/claude-issue-33-spec-review-rerun-20260523050109.md`.

Latest integrated gate status: `P0=0`, `P1=0`, Gate `PASS`.
