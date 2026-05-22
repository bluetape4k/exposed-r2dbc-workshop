# Issue 40 Spring Security Tenant Authorization R2DBC Design

## Context

Issue #40 adds the authorization layer that issue #39 deliberately excluded.
The existing chapter 10 modules show tenant routing:

- `10-multi-tenant/03-multitenant-spring-webflux`
  - schema-per-tenant.
  - mandatory `X-TENANT-ID` header.
- `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`
  - connection-factory-per-tenant.
  - mandatory `X-TENANT-ID` header.
  - tenant value is validated but not tied to an authenticated principal.

The new example must show how a WebFlux request authenticates first, then
derives or validates tenant identity from authentication claims before any
tenant-specific R2DBC query runs.

## External Evidence

Context7 was unavailable because the monthly quota was exceeded. Official Spring
Security docs were checked directly.

- Spring Security WebFlux uses `SecurityWebFilterChain` and the Kotlin DSL for
  reactive request authorization. The docs explicitly call out importing
  `org.springframework.security.config.web.server.invoke` for the Kotlin DSL.
  Source: <https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html>
- Spring Security reactive resource server JWT support requires resource-server
  support plus `spring-security-oauth2-jose`; Spring Boot can auto-configure a
  `SecurityWebFilterChain`, but applications can replace it with a bean.
  Source: <https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html>
- The same JWT docs show that the default principal is a Spring Security `Jwt`
  and `Authentication#getName` maps to `sub` when present; the
  `jwtAuthenticationConverter` hook can map JWT claims to authorities.
  Source: <https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html>
- Spring Security reactive test docs provide WebTestClient security configurers
  for OAuth2/JWT-style requests, including claim and authority customization.
  Source: <https://docs.spring.io/spring-security/reference/reactive/test/web/oauth2.html>

## Goals

1. Add a chapter 10 module for tenant-aware Spring Security authorization with
   Exposed R2DBC.
2. Keep the actor API and tenant data shape comparable to module `04`.
3. Demonstrate three tenant identity sources:
   - JWT claim: `tenant_id`.
   - API key header mapped to a tenant.
   - Web session attribute mapped to a tenant.
4. Fail closed before R2DBC access:
   - missing authentication: `401 Unauthorized`.
   - authenticated principal without tenant identity: `403 Forbidden`.
   - malformed or unknown tenant claim: `403 Forbidden`.
   - authenticated principal requesting a different tenant: `403 Forbidden`.
5. Preserve the routing invariant from #39: the R2DBC transaction must receive
   the authorized tenant through Reactor context and must never fall back to the
   default tenant on HTTP request paths.
6. Document when to choose this strategy, how it differs from routing-only
   module `04`, and why CI/Examples coverage is sufficient for this H2-only
   security example.

## Non-goals

- Do not build a production identity provider or authorization server.
- Do not add dynamic tenant onboarding/provisioning; that belongs to #41.
- Do not update chapter aggregate docs beyond direct module README links unless
  issue #42 is being implemented.
- Do not introduce a shared bluetape4k security library API. This remains a
  workshop example.
- Do not add non-H2 Testcontainers coverage for this module unless a later issue
  asks for it.

## Proposed Module Shape

Module:

```text
10-multi-tenant/05-spring-security-tenant-authorization-spring-webflux
```

Package:

```text
exposed.r2dbc.multitenant.security
```

Key production components:

- `SecurityTenantApp.kt`
  - Spring Boot entrypoint.
- `config/SecurityTenantR2dbcConfig.kt`
  - same tenant connection-factory setup as module `04`, renamed for this
    example.
- `config/SecurityConfig.kt`
  - WebFlux Spring Security chain.
  - disables CSRF for this stateless JSON workshop API.
  - configures JWT resource server support.
  - adds API-key and session tenant authentication filters for example-only
    alternate auth paths.
- `security/TenantAuthentication.kt`
  - normalizes supported authentication types into `AuthenticatedTenant`.
- `security/JwtTenantAuthenticationConverter.kt`
  - preserves JWT authentication even when `tenant_id` is absent or unusable.
  - maps ordinary authorities, but does not turn tenant-claim problems into
    authentication failures.
- `security/ApiKeyAuthenticationWebFilter.kt`
  - maps fixed demo API keys to authenticated tenants.
- `security/SessionTenantAuthenticationWebFilter.kt`
  - maps a fixed demo session header to an authenticated tenant without adding a
    stateful login flow.
- `security/TenantAuthenticationResolver.kt`
  - normalizes authenticated tenant identity from JWT/API-key/demo-session
    authentication.
- `security/AuthorizedTenantContextWebFilter.kt`
  - compares the requested tenant header against the authenticated tenant.
  - runs after normal Spring Security authentication and authenticated-request
    authorization.
  - writes the authorized tenant to Reactor context only after tenant
    authorization succeeds.
- `tenant/TenantFilter.kt`
  - no longer trusts the request header by itself.
  - validates syntax and delegates authorization to the security layer.
- `tenant/TenantTransactionExecutor.kt`
  - same explicit routing database boundary as module `04`.
- actor/movie domain, repository, initializer, and controller copied from
  module `04` with package renames.

## Request Contract

HTTP API paths remain:

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/actors` | List actors for the authorized tenant |
| `GET` | `/actors/{id}` | Read one actor for the authorized tenant |

Tenant selector remains:

```http
X-TENANT-ID: korean
```

Authentication examples:

| Source | Example | Tenant source |
|---|---|---|
| JWT | `Authorization: Bearer <jwt>` | JWT `tenant_id` claim |
| API key | `X-API-KEY: demo-korean-key` | configured API key map |
| Session | `X-DEMO-SESSION: korean-session` | configured demo session map |

The example supports fixed demo values so tests and README snippets stay
self-contained.

## Error Contract

| Case | Result |
|---|---|
| valid auth tenant equals `X-TENANT-ID` | `200 OK` |
| missing authentication | `401 Unauthorized` |
| valid auth but missing `X-TENANT-ID` | `400 Bad Request` |
| blank or malformed `X-TENANT-ID` | `400 Bad Request` |
| authenticated tenant is unknown | `403 Forbidden` |
| `X-TENANT-ID` differs from authenticated tenant | `403 Forbidden` |
| JWT lacks `tenant_id` | `403 Forbidden` |
| invalid API key or demo session key | `401 Unauthorized` |

The distinction is intentional:

- `400` means the caller did not send a valid tenant selector.
- `401` means Spring Security could not authenticate the caller.
- `403` means the caller is authenticated but not authorized for the requested
  tenant.

## Security Boundary

Routing-only module `04` validates tenant syntax but trusts the header. This
module must prove the stronger boundary:

1. Authentication establishes a tenant identity.
2. The request header selects the target tenant.
3. Normal Spring Security request authorization requires authentication.
4. `AuthorizedTenantContextWebFilter` performs tenant authorization only after
   authentication is available.
5. Authorization allows the request only when both tenants match.
6. Only the authorized tenant is written into Reactor context.
7. Exposed R2DBC opens connections through the routing connection factory using
   that authorized Reactor context.

If a request can reach R2DBC with a header-only tenant or with default fallback,
the example fails its primary purpose.

Tenant-claim failures are authorization failures, not authentication failures.
For example, a syntactically valid JWT without `tenant_id` still establishes a
JWT principal, then `AuthorizedTenantContextWebFilter` denies the request with
`403 Forbidden`. Invalid tokens or invalid API/session credentials remain
`401 Unauthorized`.

## CI and Nightly Coverage Decision

This module is H2-only and does not add container-backed runtime coverage. Root
CI already runs `./gradlew test` for H2, so the module is covered by CI once it
is included by `settings.gradle.kts`. `Examples.yml` must also add this module
to the chapter 10 job because the user explicitly requires examples to be
registered there and successful.

Nightly DB matrix does not need a new shard entry for this issue because no
PostgreSQL/MySQL/MariaDB tenant databases are introduced.
