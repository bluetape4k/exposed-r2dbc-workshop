# Issue 35 Ktor R2DBC Routing Datasource Design

## Context

Issue #35 adds the Ktor equivalent of chapter 11
`03-routing-datasource`. The existing Spring WebFlux module demonstrates
tenant plus read/write routing with Reactor Context and Spring transaction
operators. The Ktor version must keep the same observable workshop behavior
without Spring transaction infrastructure or Reactor request context.

The matching `exposed-workshop` issue is #49. The local Ktor examples from
#34 and #69 establish the preferred Ktor module shape, JSON error handling,
resource ownership, README localization, PNG diagram placement, and workflow
verification pattern.

## Goals

1. Add `11-high-performance/06-routing-datasource-ktor-r2dbc`.
2. Demonstrate route-driven and header-driven R2DBC target selection:
   - tenant header `X-Tenant-Id`;
   - read-only header `X-Read-Only`;
   - `/readonly` path suffix;
   - explicit repository calls that receive the resolved route key.
3. Keep routing state coroutine-safe by storing only validated routing input in
   Ktor call attributes and passing a value object into repository methods.
4. Seed separate H2 R2DBC pools for `default:rw`, `default:ro`, `acme:rw`, and
   `acme:ro`, each with its own marker row.
5. Add tests proving representative routes select the expected database path
   and updates affect only the read-write target for the selected tenant.
6. Add English/Korean README files and a committed PNG diagram.

## Non-Goals

- Do not reuse Spring WebFlux, Reactor Context, `TransactionalOperator`, or
  ThreadLocal request state.
- Do not implement authentication or tenant authorization.
- Do not introduce a general-purpose routing datasource library abstraction.
- Do not change the existing Spring routing module except chapter README and CI
  discoverability links.
- Do not run multi-database Testcontainers for this example; H2 is sufficient
  because the lesson is routing selection, not backend SQL compatibility.

## Module Shape

```text
11-high-performance/06-routing-datasource-ktor-r2dbc/
├── README.md
├── README.ko.md
├── build.gradle.kts
├── src/main/kotlin/exposed/r2dbc/examples/routing/ktor/
│   ├── KtorRoutingDatasourceApplication.kt
│   ├── config/
│   │   ├── KtorRoutingDatasourcePlugins.kt
│   │   └── KtorRoutingDatasourceResources.kt
│   ├── domain/
│   │   ├── RoutingMarkerRepository.kt
│   │   ├── RoutingMarkerTable.kt
│   │   └── RoutingRecords.kt
│   ├── routing/
│   │   ├── RoutingDatabase.kt
│   │   ├── RoutingDatabaseRegistry.kt
│   │   ├── RoutingInput.kt
│   │   └── RoutingRequestPlugin.kt
│   └── routes/
│       └── RoutingMarkerRoutes.kt
└── src/test/kotlin/exposed/r2dbc/examples/routing/ktor/
```

## Routing Contract

- Default tenant: `default`.
- Supported tenants: `default`, `acme`.
- Missing or blank `X-Tenant-Id` falls back to `default`, matching the Spring
  module. This is intentional: the tenant header is optional.
- Unknown tenant returns `400 INVALID_ROUTING_REQUEST`.
- `X-Read-Only: true` routes to `ro`; `false` routes to `rw`.
- Invalid `X-Read-Only` values return `400 INVALID_ROUTING_REQUEST`. This is
  intentionally stricter than tenant fallback because a present read-only header
  is an explicit routing hint and must be parseable.
- `/routing/marker/readonly` forces read-only routing, even without the header.
- `PATCH /routing/marker` always writes through `rw`; if a caller sends
  `X-Read-Only: true`, the request is rejected with
  `400 INVALID_ROUTING_REQUEST` instead of silently overriding the caller's
  hint. Write requests without `X-Read-Only` or with `false` use `rw`.

## Runtime Design

Ktor installs:

1. `ContentNegotiation` with strict kotlinx JSON.
2. `StatusPages` for stable JSON error responses.
3. A routing request plugin that resolves tenant/read-only headers and stores a
   validated `RoutingRequest` value in `call.attributes`.

The repository receives `RoutingRequest` explicitly:

- `findMarker(route)` selects `registry.database(route.key)`.
- `updateMarker(route, marker)` writes to `route.asReadWrite()`.

Each key maps to a distinct H2 R2DBC pool and `R2dbcDatabase`. Startup seeding
creates `routing_marker` in all four databases and replaces the marker with
the key string (`default-rw`, `default-ro`, `acme-rw`, `acme-ro`). Resource
closing disposes all pools at `ApplicationStopped`. Graceful request draining
is outside the workshop scope; production services should coordinate shutdown
with the server lifecycle before disposing pools.

## Error Contract

```json
{
  "code": "INVALID_ROUTING_REQUEST",
  "message": "Unknown tenant id: unknown"
}
```

## Required Tests

- `GET /routing/marker` without headers returns `default-rw`.
- `GET /routing/marker` with `X-Tenant-Id: acme` returns `acme-rw`.
- `GET /routing/marker/readonly` with `X-Tenant-Id: acme` returns `acme-ro`.
- `GET /routing/marker` with `X-Tenant-Id: acme` and `X-Read-Only: true`
  returns `acme-ro`.
- `PATCH /routing/marker` with `X-Tenant-Id: acme` updates only `acme-rw`;
  a later read-only route still returns `acme-ro`.
- Missing/blank tenant fallback and unknown tenant rejection.
- Invalid `X-Read-Only` rejection.
- `PATCH /routing/marker` with `X-Read-Only: true` returns structured `400`.
- Concurrent alternating requests for `default` and `acme` prove call attribute
  routing does not leak between coroutines by correlating each request's tenant
  input with that response's `tenant`, `readOnly`, and marker value.

## Documentation and Workflow

- Add module README and README.ko.
- Add PNG diagram under `docs/images/readme-diagrams/` and reference the PNG
  from both module READMEs.
- Link the module from chapter 11 README/README.ko.
- Add the module to `.github/workflows/Examples.yml` chapter 11 path filters,
  test command, and artifact paths.
- Add a lesson note after implementation.

## Verification Targets

- `./gradlew projects --console=plain`
- `./gradlew :06-routing-datasource-ktor-r2dbc:compileKotlin :06-routing-datasource-ktor-r2dbc:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- Chapter 11 workflow-equivalent H2 tests.
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- Scan for `runBlocking`, `kotlinx.coroutines.runBlocking`, `ThreadLocal`,
  `ReactorContext`, `TransactionalOperator`, `GlobalScope`, direct
  `GenericContainer`, and swallowed `CancellationException`.

## Risks

- Routing bugs can be hidden if tests only inspect response fields. Tests must
  prove the marker value comes from the selected database.
- Ktor call attributes are safe per request, but repository APIs must not read
  global mutable state.
- Read-only header input is a routing hint, not a security boundary; README
  must warn that production systems need authenticated policy checks.
