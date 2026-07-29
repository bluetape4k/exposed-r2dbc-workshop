# Issue #38 Schema-Per-Tenant R2DBC 구현 계획

## 분류

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

유지:

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

수정:

- `tenant/TenantFilter.kt` — require `X-TENANT-ID`, keep unknown tenant `400`.
- `controller/ActorControllerTest.kt` — align missing-header behavior and
  strengthen isolation evidence.
- `README.md` and `README.ko.md` — clarify mandatory header, strategy choice,
  and CI/Nightly coverage.

금지: add new orders-domain files or replacement tenant registry files.

## 구현 작업

1. Lock current baseline.
   - 실행: targeted module tests before editing to classify existing failures.
   - Confirm whether missing-header currently defaults to `korean`.

2. Tighten tenant validation.
   - In `TenantFilter`, treat missing or blank `X-TENANT-ID` as
     `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`.
   - 유지: unknown tenant as `400 Bad Request`.
   - 유지: `TENANT_HEADER = "X-TENANT-ID"` for README/test compatibility.
   - Rewrite `TenantFilter` KDoc so it no longer documents default-tenant
     fallback for missing or blank HTTP headers.
   - 문서화: `TenantId.DEFAULT` as a non-WebFlux direct-call fallback and log a
     warning when that fallback is used.

3. Preserve routing helper semantics.
   - 유지: `suspendTransactionWithCurrentTenant` as the canonical WebFlux
     controller boundary.
   - 유지: `currentTenant()` for direct coroutine context use.
   - 금지: call plain `suspendTransaction` from controllers.
   - Review `SchemaUtils.setSchema` usage for connection-state leakage notes.

4. 갱신: tests.
   - `ActorControllerTest`: successful 읽는다: for every tenant.
   - `ActorControllerTest`: same actor query path must not leak rows across
     tenants.
   - `ActorControllerTest`: missing header returns `400`.
   - `ActorControllerTest`: unknown tenant returns `400`.
   - 유지: config/pool tests unchanged unless compilation requires updates.

5. 갱신: docs.
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
   - Rerun Claude spec/plan advisor 다음 위치 뒤: this re-baseline.
   - 구현: only 다음 위치 뒤: the spec/plan gate reports `P0=0`, `P1=0`.
   - 실행: current-session code review and Claude code review 다음 위치 뒤:
     implementation.
   - Fix all P0/P1 findings before PR.

8. Capture and publish.
   - 추가: `docs/lessons/2026-05-22-issue-38-schema-per-tenant-r2dbc.md`.
   - 커밋: with Lore trailers.
   - 푸시: branch and create PR against `develop`, assigned to `debop`.
   - Watch GitHub checks; do not merge automatically.

## 위험 제어

- 유지: #39 connection-factory routing out of scope.
- 유지: #40 authorization out of scope.
- 사용: explicit tenant validation before any database operation.
- Treat `SchemaUtils.setSchema` as connection state and keep it behind the
  transaction helper.
- Prefer tests that alternate tenants through the same application instance.
- New public KDoc must be English; internal docs may be Korean.

## 검토 메모

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
- 추가함: keep/modify decisions for existing tests and README files.
- 추가함: concrete IDE fallback command.

최신 gate 상태: `P0=0`, `P1=0`, Gate `PASS`.
