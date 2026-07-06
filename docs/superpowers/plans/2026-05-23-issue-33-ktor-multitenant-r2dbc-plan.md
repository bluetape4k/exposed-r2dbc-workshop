# Issue #33 Ktor R2DBC Multi-Tenant Plan

## Classification

Type A - Full Design.

Reason: #33 adds a new Ktor + Exposed R2DBC chapter module with request
plugins, tenant-aware transactions, tests, README pairs, diagram assets, and
CI coverage decisions.

## Approved Spec

Spec:
`docs/superpowers/specs/2026-05-23-issue-33-ktor-multitenant-r2dbc-design.md`.

Step 2-R status: `P0=0`, `P1=0`.

Claude artifacts:

- `.omx/artifacts/claude-issue-33-spec-review-20260523045833.md`
- `.omx/artifacts/claude-issue-33-spec-review-rerun-20260523050109.md`

## Baseline Evidence

Before implementation:

```text
repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

Result: BUILD SUCCESSFUL in 33s. `:03-multitenant-spring-webflux:test`
executed 15 tests; Ktor production integration tests also passed.

## Implementation Tasks

1. Create module skeleton.
   - Add `10-multi-tenant/07-multitenant-ktor/build.gradle.kts`.
   - Use existing Ktor dependencies from
     `12-production-integration/02-ktor-production-integration`.
   - Keep runtime dependencies to H2 R2DBC, R2DBC pool, Exposed R2DBC, Logback,
     and kotlinx serialization.
   - Verify Gradle discovery through `./gradlew projects --console=plain`.
   - Add English KDoc for public module entrypoints and tenant helpers.
   - Create `src/test/resources/junit-platform.properties` and
     `src/test/resources/logback-test.xml` matching chapter 10 sibling modules.

2. Port the actor/movie domain.
   - Copy the chapter 10 actor/movie table and record shape into package
     `exposed.r2dbc.multitenant.ktor.domain`.
   - Keep names comparable with `03-multitenant-spring-webflux`.
   - Avoid Spring annotations and Reactor imports.
   - Every actor/movie/domain `data class` implements `java.io.Serializable`
     and defines `companion object { private const val serialVersionUID = 1L }`.

3. Add tenant model and schema support.
   - Add `Tenants` enum with `korean` and `english`.
   - Add `SchemaSupport` returning enum-owned `Schema` values.
   - Raw request strings must never reach `SchemaUtils.setSchema`.

4. Add transaction boundary.
   - Add local `suspendTransactionWithTenant(tenant, db, ...)`.
   - Set schema inside the Exposed R2DBC transaction before repository access.
   - Keep direct `suspendTransaction` limited to initialization helpers.
   - Add an architecture test or source scan assertion that request routes do
     not call bare `suspendTransaction`.

5. Add Ktor plugin and error handling.
   - Install `ContentNegotiation` with kotlinx JSON.
   - Install `StatusPages` for `InvalidTenantException` and malformed requests.
   - Add `TenantPlugin` that validates `X-TENANT-ID` and stores
     `Tenants.Tenant` in a private `AttributeKey`.
   - Read all `X-TENANT-ID` header values. If more than one value exists,
     accept only when every trimmed value is identical; otherwise throw
     `InvalidTenantException`.
   - Add `ApplicationCall.currentTenant()` extension that throws
     `IllegalStateException` when the plugin was not installed.
   - JSON error shape: `{"code":"INVALID_TENANT","message":"..."}`.
   - Validate path/body inputs with bluetape4k validation helpers where
     applicable; use `check()` only for internal invariants.

6. Add routes and repository.
   - Add `routes/ActorRoutes.kt`.
   - Endpoints:
     - `GET /actors`
     - `GET /actors/{id}`
     - `POST /actors`
   - `POST /actors` exists to prove write isolation and should accept a small
     serializable request body.
   - Validate actor ID as positive and request names as non-blank before DB
     access.
   - Repository methods must be side-effect obvious and return DTO records.
   - Public request/response DTO `data class` declarations implement
     `java.io.Serializable`, define `serialVersionUID`, and carry English KDoc
     when they teach the API surface.

7. Add startup initialization.
   - Create schemas and seed sample data for both tenants at module install.
   - Initialization is idempotent and fail-fast.
   - `runBlocking` is allowed only for synchronous startup initialization so
     fail-fast semantics are deterministic; it must not appear in request
     routes or repository methods.
   - Define one database/pool owner for the module. If a pooled
     `ConnectionFactory` is used, close it from `ApplicationStopped`.
   - Use pool max size `1` in the constrained-pool test configuration so tenant
     alternation actually reuses the same connection path.
   - Use distinct application/test DB URL if test isolation needs it.

8. Add tests.
   - Use Ktor `testApplication`.
   - Use a module-local Ktor test base instead of `AbstractR2dbcExposedTest`;
     this example is H2-only and tests Ktor request behavior rather than the
     shared multi-dialect matrix.
   - Cover successful reads for both tenants.
   - Cover missing, empty, whitespace, duplicate, and unknown tenant headers
     with `400` plus `INVALID_TENANT` JSON body.
   - Cover lowercase `x-tenant-id`.
   - Cover same-ID tenant isolation.
   - Cover write isolation through `POST /actors`.
   - Cover rapid tenant alternation under constrained pool size `1`.
   - Cover overlapping concurrent requests for both tenants.
   - Cover no bare route-path `suspendTransaction` usage.
   - Cover no request-path `runBlocking` usage.
   - Use bluetape4k assertions only.

9. Add documentation and diagram.
   - Add module `README.md` and `README.ko.md`.
   - Link from `10-multi-tenant/README.md` and `README.ko.md`.
   - Add PNG diagram under `docs/images/readme-diagrams/` with source file if
     the repo pattern stores one. Check the existing readme-diagram convention
     before generating assets and keep the committed PNG path stable.
   - Document that `X-TENANT-ID` is not authentication and production must bind
     routing to identity.
   - Document intentional local duplication from the WebFlux helper.
   - Document schema switch cost and forward link to #35.

10. Add workflow coverage.
    - Update `.github/workflows/Examples.yml` paths and chapter 10 job/module
      list if current workflow requires explicit module entries.
    - Run `actionlint` if the workflow changes.
    - Record Nightly coverage decision in README and lesson.

11. Verify.
    - `./gradlew projects --console=plain`
    - `./gradlew :07-multitenant-ktor:compileKotlin --warning-mode all --console=plain`
    - `repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
   - `./gradlew detekt --parallel --console=plain`
    - `git diff --check`
    - `actionlint .github/workflows/Examples.yml` if changed.
    - PNG existence and size check.
    - IntelliJ diagnostics if available; otherwise record fallback.

12. Review, lesson, PR, merge.
    - Run Step 6-R current Codex 6-tier review and Claude CLI 6-tier review.
    - Fix all P0/P1 and rerun relevant checks.
    - Add `docs/lessons/2026-05-23-issue-33-ktor-multitenant-r2dbc.md`.
    - Commit with Lore trailers.
    - Push, create PR with `Closes #33`, post Step 7-R PR comment and formal
      review.
    - Wait for CI success, rebase merge, clean branch/worktree, sync qmd.

## Risk Controls

- Keep #34/#35/#69 out of scope.
- Do not promote shared code from the Spring WebFlux module in this issue.
- Treat Ktor call attributes as the request-scope carrier; no fallback tenant.
- Tests must prove pool schema reset because schema state is connection state.
- Keep root develop clean; all #33 artifacts stay inside the worktree branch.

## Step 3-R Status

Initial Claude artifact:
`.omx/artifacts/claude-issue-33-plan-review-20260523050300.md`.

Accepted P1/P2 edits:

- Added required new-module test resources.
- Added Serializable + `serialVersionUID` requirement for data classes.
- Assigned duplicate-header handling to `TenantPlugin`.
- Clarified Ktor test base, diagram convention check, and public DTO KDoc.

Claude rerun artifact:
`.omx/artifacts/claude-issue-33-plan-review-rerun-20260523050436.md`.

Latest integrated gate status: `P0=0`, `P1=0`, Gate `PASS`.
