# Issue 40 Spring Security Tenant Authorization R2DBC Plan

## Scope

Add `10-multi-tenant/05-spring-security-tenant-authorization-spring-webflux`, a
Spring WebFlux + Exposed R2DBC workshop module that authorizes tenant access
before routing to a tenant connection factory.

Approved spec draft:

- `docs/superpowers/specs/2026-05-22-issue-40-spring-security-tenant-authorization-r2dbc-design.md`

This plan keeps the module H2-only and updates `Examples.yml` so chapter 10
examples include the new security module.

## Implementation Tasks

### 1. Scaffold module from chapter 10 connection-factory example

Complexity: M

- Copy module `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux`
  to `10-multi-tenant/05-spring-security-tenant-authorization-spring-webflux`.
- Rename package from `exposed.r2dbc.multitenant.connectionfactory` to
  `exposed.r2dbc.multitenant.security`.
- Rename the Spring Boot entrypoint to `SecurityTenantApp.kt`.
- Update `springBoot.mainClass`, build-info name, and README titles.
- Keep actor/movie API and tenant fixture data comparable with module `04`.

### 2. Add Spring Security dependencies and configuration

Complexity: M

- Add version-catalog aliases for:
  - `spring-boot-starter-security`.
  - `spring-boot-starter-oauth2-resource-server`.
  - `spring-security-oauth2-jose`, if the starter does not bring it directly
    enough for explicit compilation.
- Add module dependencies through `libs.*` aliases.
- Implement `config/SecurityConfig.kt`.
  - `@EnableWebFluxSecurity`.
  - `SecurityWebFilterChain` bean.
  - CSRF disabled for this stateless JSON example.
  - `/actuator/health` permitted.
  - `/actors/**` authenticated and tenant-authorized.
  - JWT resource server enabled.
  - API-key and demo-session authentication filters registered at
    `SecurityWebFiltersOrder.AUTHENTICATION`.
  - `AuthorizedTenantContextWebFilter` registered after normal authentication
    and authenticated-request authorization, at `SecurityWebFiltersOrder.LAST`,
    so it can write Reactor context for the downstream handler only after
    tenant authorization succeeds.

### 3. Implement tenant identity extraction

Complexity: L

- Add `security/AuthenticatedTenant.kt`.
- Add `security/JwtTenantAuthenticationConverter.kt`.
  - extracts `tenant_id`.
  - never fails JWT authentication for tenant-claim problems. Missing,
    malformed, or unknown tenant claims must remain authorization failures so
    they return `403 Forbidden`, not `401 Unauthorized`.
  - stores the raw claim in the authentication token details or leaves it on the
    `Jwt` for `TenantAuthenticationResolver`.
  - preserves normal JWT authority mapping where useful.
- Add `security/ApiKeyAuthenticationWebFilter.kt`.
  - fixed demo key map:
    - `demo-korean-key` -> `korean`.
    - `demo-english-key` -> `english`.
  - invalid key does not authenticate.
- Add `security/SessionTenantAuthenticationWebFilter.kt`.
  - fixed demo session map:
    - `korean-session` -> `korean`.
    - `english-session` -> `english`.
  - no stateful login or persistent session store.

### 4. Implement tenant authorization and routing bridge

Complexity: L

- Keep `TenantIdResolver` for header syntax and known-tenant validation.
- Replace header-trusting `TenantFilter` with a security-aware boundary:
  - validate `X-TENANT-ID` syntax.
  - never write Reactor tenant context directly from the header before
    authorization.
- Add `security/TenantAuthenticationResolver.kt`.
  - reads authenticated tenant identity from JWT claim, API-key authentication,
    or demo-session authentication.
  - returns explicit missing/malformed/unknown states so the authorization
    filter can return `403`.
- Add `security/AuthorizedTenantContextWebFilter.kt`.
  - registered at `SecurityWebFiltersOrder.LAST`.
  - read authenticated tenant from `Authentication`.
  - compare it with the normalized request header tenant.
  - throw `AccessDeniedException` for missing auth tenant, unknown auth tenant,
    or mismatch.
  - on grant, call `chain.filter(exchange).contextWrite { ... }` and write only
    the authorized tenant to Reactor context.
- Ensure `TenantTransactionExecutor` uses only the authorized Reactor tenant.
- Add an architecture test that production code writes
  `TenantContextKeys.TENANT_ID` only in `AuthorizedTenantContextWebFilter`.

### 5. Update tests

Complexity: L

Use JUnit 5, `runSuspendIO`, WebTestClient, and bluetape4k assertions.

- Security tests:
  - valid JWT claim accesses matching tenant through the demo
    `ReactiveJwtDecoder` and real bearer-token WebTestClient requests.
  - JWT tenant/header mismatch returns `403`.
  - missing JWT tenant claim returns `403`.
  - missing authentication returns `401`.
  - API key accesses matching tenant.
  - invalid API key returns `401`.
  - demo session accesses matching tenant using the demo session header.
  - session tenant/header mismatch returns `403`.
- Tenant/routing tests:
  - missing, blank, malformed, and unknown tenant headers still return `400`
    for authenticated callers.
  - same actor ID returns tenant-specific fixture values for each auth source.
  - concurrent authorized Korean/English requests never cross-route.
- Architecture tests:
  - request-path production code does not call bare `suspendTransaction`.
  - only security authorization code writes request tenant context.
  - module package does not import classes from the connection-factory example.
  - implement these scans as focused source-text architecture tests, matching
    existing repository style and avoiding a new architecture-test dependency.

### 6. Update README and workflows

Complexity: M

- Write `README.md` and `README.ko.md`.
- Explain:
  - tenant routing vs tenant authorization.
  - JWT/API-key/session demo identity sources.
  - `X-DEMO-SESSION` is a demo header, not a production session cookie.
  - CSRF is disabled because this workshop API is stateless JSON; real
    cookie-backed session flows need CSRF protection.
  - request/error contract.
  - when to choose this strategy.
  - CI coverage and why Nightly remains unchanged.
- Update `.github/workflows/Examples.yml`:
  - include module path in path filters.
  - add `:05-spring-security-tenant-authorization-spring-webflux:test` to the
    chapter 10 Gradle command.
- Run `actionlint` after workflow edit.

### 7. Verify, review, and publish

Complexity: M

Run in order:

1. `./gradlew projects --console=plain`
2. `./gradlew :05-spring-security-tenant-authorization-spring-webflux:compileKotlin --warning-mode all --console=plain`
3. `repo-test-summary -- ./gradlew :05-spring-security-tenant-authorization-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
4. `actionlint .github/workflows/Examples.yml`
5. `git diff --check`
6. IDE diagnostics if available; otherwise record compile/test fallback.
7. Claude Step 6-R code review gate with `P0=0`, `P1=0`.
8. Create `docs/lessons/2026-05-22-issue-40-spring-security-tenant-authorization-r2dbc.md`.
9. Commit with Lore trailers.
10. Push branch and open a draft PR assigned to `debop`.
11. Add PR comment + formal review, wait for CI/Examples, then merge and clean
    local worktrees/branches because the user requested the full cycle.

## Step 3-R Local Review

| Perspective | P0 | P1 | P2/P3 notes |
|---|---:|---:|---|
| Security boundary | 0 | 0 | Header-only tenant trust is explicitly rejected. |
| Testability | 0 | 0 | WebTestClient tests cover JWT, API key, session, mismatch, and missing auth. |
| CI scope | 0 | 0 | Examples workflow must include the new module; Nightly remains unchanged. |

## Claude Step 2-R/3-R Advisor Iteration 1

Artifact: `.omx/artifacts/claude-issue-40-spec-plan-compact-20260522130111.md`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P0 | Missing JWT `tenant_id` could become `401` if converter rejects authentication. | Accepted | Spec/plan now require JWT authentication to succeed and tenant-claim problems to be denied by authorization as `403`. |
| P0 | `ReactiveAuthorizationManager` cannot reliably write Reactor context downstream. | Accepted | Replaced with `AuthorizedTenantContextWebFilter` registered at `SecurityWebFiltersOrder.LAST`. |
| P1 | API-key/session filter order was underspecified. | Accepted | Plan pins them to `SecurityWebFiltersOrder.AUTHENTICATION`. |
| P1 | WebTestClient security mutators were not named. | Accepted | Plan now requires real bearer-token WebTestClient requests through the demo decoder and demo headers for API/session paths. |
| P2 | Architecture test tooling unnamed. | Accepted | Plan uses focused source-text architecture tests, avoiding a new dependency. |
| P3 | Demo session header could be mistaken for production session. | Accepted | README task now requires explicit caveat. |
