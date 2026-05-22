# Issue #38 Schema-Per-Tenant R2DBC Plan

## Classification

Type A - Full Design.

Reason: Issue #38 changes a multi-layer Spring WebFlux + Exposed R2DBC example:
request filtering, coroutine/Reactor tenant context, transaction-level schema
routing, tests, docs, and CI/Nightly evidence.

## Acceptance Criteria

- `10-multi-tenant/03-multitenant-spring-webflux` builds with its existing
  Kotlin implementation.
- Focused tests cover tenant isolation and error handling.
- `README.md` and `README.ko.md` explain when to choose schema-per-tenant.
- CI/Nightly coverage decision is recorded.
- Claude advisor/code-review gates show `P0=0` and `P1=0` before PR.

## Current File Decisions

Keep:

- `ExposedMultitenantWebfluxApp.kt`
- `config/ExposedR2dbcConfig.kt`
- `config/NettyConfig.kt`
- `config/SwaggerConfig.kt`
- `config/TenantConfig.kt`
- `controller/ActorController.kt`
- `domain/model/*`
- `domain/repository/*`
- `tenant/DataInitializer.kt`
- `tenant/SchemaSupport.kt`
- `tenant/TenantId.kt`
- `tenant/TenantInitializer.kt`
- `tenant/Tenants.kt`
- `AbstractMultitenantTest.kt`
- `ExposedR2dbcConfigTest.kt`
- `ConnectionPoolSizingTest.kt`

Modify:

- `tenant/TenantFilter.kt` — require `X-TENANT-ID`, keep unknown tenant `400`.
- `controller/ActorControllerTest.kt` — align missing-header behavior and
  strengthen isolation evidence.
- `README.md` and `README.ko.md` — clarify mandatory header, strategy choice,
  and CI/Nightly coverage.

Do not add new orders-domain files or replacement tenant registry files.

## Implementation Tasks

1. Lock current baseline.
   - Run targeted module tests before editing to classify existing failures.
   - Confirm whether missing-header currently defaults to `korean`.

2. Tighten tenant validation.
   - In `TenantFilter`, treat missing or blank `X-TENANT-ID` as
     `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`.
   - Keep unknown tenant as `400 Bad Request`.
   - Keep `TENANT_HEADER = "X-TENANT-ID"` for README/test compatibility.
   - Rewrite `TenantFilter` KDoc so it no longer documents default-tenant
     fallback for missing or blank HTTP headers.
   - Document `TenantId.DEFAULT` as a non-WebFlux direct-call fallback and log a
     warning when that fallback is used.

3. Preserve routing helper semantics.
   - Keep `suspendTransactionWithCurrentTenant` as the canonical WebFlux
     controller boundary.
   - Keep `currentTenant()` for direct coroutine context use.
   - Do not call plain `suspendTransaction` from controllers.
   - Review `SchemaUtils.setSchema` usage for connection-state leakage notes.

4. Update tests.
   - `ActorControllerTest`: successful reads for every tenant.
   - `ActorControllerTest`: same actor query path must not leak rows across
     tenants.
   - `ActorControllerTest`: missing header returns `400`.
   - `ActorControllerTest`: unknown tenant returns `400`.
   - Keep config/pool tests unchanged unless compilation requires updates.

5. Update docs.
   - `README.md`: document schema-per-tenant choice, mandatory `X-TENANT-ID`,
     actors API, and CI/Nightly coverage.
   - `README.ko.md`: mirror the same user-facing content.

6. Verification.
   - `./gradlew projects --console=plain`
   - `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
   - `./gradlew :03-multitenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
   - `git diff --check`
   - IDE diagnostics if this worktree is open in IntelliJ; otherwise record
     fallback to compile/test evidence.
   - If the module name changes, update this command and the Nightly
     `:03-multitenant-spring-webflux:test` shard entry together.

7. Review gates.
   - Rerun Claude spec/plan advisor after this re-baseline.
   - Implement only after the spec/plan gate reports `P0=0`, `P1=0`.
   - Run current-session code review and Claude code review after
     implementation.
   - Fix all P0/P1 findings before PR.

8. Capture and publish.
   - Add `docs/lessons/2026-05-22-issue-38-schema-per-tenant-r2dbc.md`.
   - Commit with Lore trailers.
   - Push branch and create PR against `develop`, assigned to `debop`.
   - Watch GitHub checks; do not merge automatically.

## Risk Controls

- Keep #39 connection-factory routing out of scope.
- Keep #40 authorization out of scope.
- Use explicit tenant validation before any database operation.
- Treat `SchemaUtils.setSchema` as connection state and keep it behind the
  transaction helper.
- Prefer tests that alternate tenants through the same application instance.
- New public KDoc must be English; internal docs may be Korean.

## Review Notes

Initial Claude advisor review artifact:
`.omx/artifacts/claude-issue-38-spec-plan-review-20260522.md`.

Claude advisor re-review artifact:
`.omx/artifacts/claude-issue-38-spec-plan-rereview-20260522.md`.

Accepted P0/P1 fixes:

- Replaced stale "no source exists" baseline with current file inventory.
- Removed new orders-domain plan.
- Reused existing file names and helpers.
- Standardized on `X-TENANT-ID`.
- Chose missing/unknown tenant `400`.
- Added keep/modify decisions for existing tests and README files.
- Added concrete IDE fallback command.

Latest gate status: `P0=0`, `P1=0`, Gate `PASS`.
