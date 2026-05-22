# Issue 41 Tenant Onboarding and Provisioning R2DBC Plan

Spec:
`docs/superpowers/specs/2026-05-22-issue-41-tenant-onboarding-r2dbc-design.md`

Target module: `10-multi-tenant/06-tenant-onboarding-spring-webflux`

## Execution Order

1. Commit spec/plan before implementation.
2. Create the new module by borrowing #05/#04 structure, then trim to the #41
   scope.
3. Implement core value types, registry schema, dynamic routing, and
   provisioning in that order.
4. Add controller/API behavior and admin guard.
5. Add tests from lowest-level contracts to WebFlux integration.
6. Add README EN/KO and PNG diagram asset.
7. Wire Gradle/settings/Examples workflow and run verification.
8. Run Step 6-R dual code review, fix P0/P1, create lesson, PR, PR review,
   CI, merge, and local sync.

## Implementation Tasks

### 0. Branch Hygiene

- Confirm worktree branch `feat/issue-41-tenant-onboarding` is based on current
  `origin/develop`.
- Keep root `.codex/` untracked and out of commits.
- Commit spec/plan with Lore trailers after Step 3-R closes.

### 1. Module Skeleton

- Add `10-multi-tenant/06-tenant-onboarding-spring-webflux`.
- Register the module in `settings.gradle.kts`.
- Start from #05/#04 module layout:
  - Spring WebFlux application class.
  - actor/movie domain tables and repositories.
  - test resources: `junit-platform.properties`, `logback-test.xml`.
  - `build.gradle.kts` dependencies matching #05 plus Micrometer if already
    available through Spring Boot actuator dependencies; do not add new
    dependencies without checking catalog/source first.
- Keep package root `exposed.r2dbc.multitenant.onboarding`.

### 2. Core Tenant Types and Validation

- Add `TenantId` value class with regex validation through bluetape4k
  validation helpers where practical.
- Add unit tests for valid slug and invalid blank, uppercase, slash, dot,
  colon, and overlength values.
- Add request/response DTOs:
  - request: `tenantId`, `displayName`.
  - response: `tenantId`, `displayName`, `status`, `createdAt`.
  - never include raw `r2dbc_url` in HTTP responses.

### 3. Registry Database

- Add registry table/model with:
  - `tenant_id UNIQUE NOT NULL`
  - `display_name`
  - `database_name`
  - `r2dbc_url`
  - string `status`
  - `created_at`
- Configure `registryDatabase` with explicit `H2Dialect`.
- Implement `TenantRegistryRepository`:
  - `reserve(TenantId, displayName, url)` acquires a per-tenant
    `Mutex` keyed by `TenantId`, then runs one registry `suspendTransaction`.
  - algorithm: update `FAILED -> PROVISIONING`; if no row updated, select
    current status; `ACTIVE`/`PROVISIONING` -> `DuplicateTenantException`;
    absent -> insert `PROVISIONING`; unique violation -> `DuplicateTenantException`.
  - `markActive`, `markFailed`, `delete`, `recoverStaleRows`.
  - registry outage maps to `TenantProvisioningException`.
- Add startup initializer/recovery:
  - create registry schema.
  - stale `PROVISIONING -> FAILED`.
  - stale `ACTIVE -> FAILED` for H2 in-memory restart semantics.
  - fail app startup if registry DB is unavailable.
  - run recovery before WebFlux accepts requests, using `SmartLifecycle` or an
    equivalent context-refresh gate; if using `SmartLifecycle`, use a phase
    lower than `WebServerStartStopLifecycle` such as `SmartLifecycle.DEFAULT_PHASE`.
    if recovery throws, startup fails.
  - call `recoverStaleRows` from that startup gate.
  - document the per-tenant mutex map as workshop-scoped and bounded by the
    small onboarding example lifecycle; production should evict idle mutexes.
  - gate `ACTIVE -> FAILED` recovery to the H2 in-memory profile/config.

### 4. Dynamic Registry and Routing

- Implement `TenantConnectionFactoryRegistry`:
  - stores `ConnectionPool` and cached `R2dbcDatabase` per `TenantId`.
  - uses `ConcurrentHashMap` plus per-tenant `Mutex`.
  - uses a global onboarding mutex for `maxTenants` count/reserve.
  - register/remove/lookup APIs.
  - remove evicts DB wrapper and closes pool.
  - `destroy()` closes each pool independently and logs failures.
- Implement `TenantRoutingConnectionFactory` directly as `ConnectionFactory`:
  - document why it does not extend `AbstractRoutingConnectionFactory`: dynamic
    registry mutation must not rely on Spring's initialized target map.
  - `create()` reads Reactor context key.
  - converts to `TenantId`.
  - delegates to active tenant factory.
  - unknown/inactive -> `UnknownTenantException`.
  - `getMetadata().getName()` returns exact `"H2"` and never reads context.
- Configure `tenantRoutingDatabase` with explicit `H2Dialect`.
- Implement `TenantTransactionExecutor` using the #04/#05
  `Mono.deferContextual { mono(coroutineContext + ReactorContext(...)) { ... } }`
  bridge.

### 5. Tenant Provisioning

- Implement `TenantProvisioner`:
  - validates request through `TenantId`.
  - generates UUID-derived lowercase safe suffix.
  - checks `maxTenants` before reservation under global mutex.
  - creates tenant pool with `initial-size=1`, `max-size=4`,
    `acquire-timeout=5s`, `max-idle-time=30s`.
  - creates tenant `R2dbcDatabase` with explicit `H2Dialect`.
  - creates actor/movie schema and starter rows.
  - registers tenant in runtime registry.
  - marks metadata `ACTIVE`.
  - logs per-step structured events with no raw URL.
  - increments onboarding failure counter and exposes active tenant gauge.
- Cleanup:
  - failure/cancellation triggers reverse cleanup in `NonCancellable`.
  - drop tenant objects/catalog where possible.
  - deregister runtime tenant.
  - close pool.
  - delete runtime-failure metadata.
  - rethrow `CancellationException`; wrap other failures as
    `TenantProvisioningException`.
- Add `ProvisioningFailureSimulator`:
  - production no-op `@ConditionalOnMissingBean`.
  - test override only.
  - enum points: `AFTER_RESERVE`, `AFTER_POOL`, `AFTER_SCHEMA`, `AFTER_SEED`,
    `AFTER_REGISTER`.
  - no env/property activation in production.

### 6. WebFlux API

- Implement admin guard for `POST /api/tenants`:
  - header `X-ADMIN-TOKEN`.
  - property `app.onboarding.admin-token`.
  - constant-time byte comparison.
  - missing/wrong -> `401`.
- Implement `TenantOnboardingController`:
  - `POST /api/tenants`.
  - status mapping: `201`, `400`, `401`, `409`, `429`, `500`.
- Implement actor endpoints from #04/#05:
  - use `X-TENANT-ID` only for routing.
  - do not treat `X-ADMIN-TOKEN` as tenant authorization.
  - unknown tenant -> `404`.
- Add exception handler/problem responses without raw URLs or stack traces.

### 7. Tests

- `TenantIdTest`: regex boundary cases.
- `TenantRegistryRepositoryTest`:
  - reserve new tenant.
  - duplicate active/provisioning maps to `DuplicateTenantException`.
  - concurrent reserve of the same `TenantId` launches N coroutines and yields
    exactly one success plus N-1 `DuplicateTenantException`s.
  - retry stale `FAILED` row.
  - unique violation fallback maps to duplicate.
  - unique violation fallback test injects a row between update and insert in a
    controlled repository seam to avoid flaky race timing.
  - concurrent duplicate test asserts no stale `PROVISIONING` loser row remains.
- `TenantRegistryRecoveryTest`:
  - registry schema init.
  - stale `PROVISIONING -> FAILED`.
  - stale `ACTIVE -> FAILED`.
  - startup failure fails context.
- `TenantConnectionFactoryRegistryTest`:
  - register/lookup/remove.
  - remove closes only removed pool.
  - destroy closes every pool and does not skip later pools after one close
    failure.
  - cleanup/destroy uses manual try/catch, not `runCatching`, around suspend
    cleanup paths; `CancellationException` is rethrown where applicable.
- `TenantRoutingConnectionFactoryTest`:
  - metadata returns `"H2"` without context.
  - unknown tenant -> `UnknownTenantException`.
  - context tenant delegates to expected factory.
- `TenantTransactionExecutorTest`:
  - preserves Reactor context.
  - propagates `CancellationException`.
- `TenantProvisionerTest`:
  - successful onboarding creates metadata/schema/pool/runtime registration.
  - each failure enum point cleans up correctly.
  - cancellation cleans up and rethrows.
  - schema timeout cleans up.
  - registry outage at reservation and after reservation maps to
    `TenantProvisioningException`.
  - already registered tenant actor API still routes when registry writes are
    unavailable.
  - captured onboarding logs do not include raw `r2dbc_url`.
  - retry after runtime failure succeeds.
  - `maxTenants` cap blocks before metadata write.
  - `N + 1` concurrent distinct tenants with max `N` yields exactly `N`
    successes and one `429`.
- `TenantIsolationTest`:
  - two onboarded tenants isolate actor rows.
  - newly onboarded tenant immediately routes through `X-TENANT-ID`.
- `TenantOnboardingControllerTest`:
  - `201`, `400`, `401`, `409`, `429`, `500`.
  - failure cleanup not routable.
  - wrong admin token is not accepted as tenant auth.
  - successful `201` response omits raw `r2dbc_url`.
- `ArchitectureTest`:
  - Kotlin-source scan style from #04/#05.
  - only provisioning/registry/init infra can call direct `suspendTransaction`.
  - controllers and actor repositories cannot call direct `suspendTransaction`.
  - `TenantProvisioner` and routing infrastructure accept `TenantId` rather
    than raw `String` after controller boundary.

Use `runSuspendIO` for R2DBC IO and bluetape4k assertions for new tests.

### 8. Documentation and Diagram

- Add module `README.md` and `README.ko.md`:
  - strategy selection vs #03/#04/#05.
  - onboarding flow, duplicate/failure cleanup, admin guard, max tenant cap.
  - production warnings: real auth, plaintext credentials, audit/metrics,
    token rotation, offboarding non-goal.
  - H2 in-memory restart semantics: stale `ACTIVE` rows are marked `FAILED`;
    users should re-onboard tenants after restart.
  - CI/Nightly decision: Examples covers module; Nightly expansion deferred to
    #42.
- Generate PNG diagram:
  - `docs/assets/readme-diagrams/issue-41-tenant-onboarding-r2dbc-01.png`.
  - README files reference PNG path, not Mermaid.
  - Source SVG may be kept beside PNG if needed.
- Add English KDoc for public classes/value types introduced by the module.

### 9. CI and Build Wiring

- Register module in `settings.gradle.kts`.
- Update `.github/workflows/Examples.yml`:
  - path filters.
  - chapter 10 Gradle command:
    `:06-tenant-onboarding-spring-webflux:test`.
  - artifact paths.
- Run `actionlint .github/workflows/Examples.yml`.
- Do not expand Nightly in this issue; record #42 deferral in README and lesson.

### 10. Verification Commands

Run in order:

```bash
./gradlew projects --console=plain
./gradlew :06-tenant-onboarding-spring-webflux:compileKotlin --warning-mode all --console=plain
./gradlew :06-tenant-onboarding-spring-webflux:compileTestKotlin --warning-mode all --console=plain
repo-test-summary -- ./gradlew :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain
./gradlew :06-tenant-onboarding-spring-webflux:test --tests '*Concurrent*' -PuseDB=H2 --console=plain
repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain
actionlint .github/workflows/Examples.yml
./gradlew detekt --parallel --console=plain
git diff --check
```

If IntelliJ MCP diagnostics are unavailable, record fallback and rely on
targeted Gradle compile/tests plus `detekt`.

## Step 3-R Review Notes

### Codex Plan Review Iteration 1

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Registry reserve must include the spec-required per-tenant mutex, not only a transaction. | Accepted | Added per-tenant `Mutex` to `TenantRegistryRepository.reserve()` task. |
| P1 | Startup recovery must complete before WebFlux accepts requests. | Accepted | Added `SmartLifecycle` or equivalent startup gate task. |
| P1 | Same-tenant concurrent reserve test was missing. | Accepted | Added N-coroutine duplicate reserve test. |
| P2 | Custom routing factory deviation, URL leak assertions, registry-outage routing, suspend cleanup, and post-controller `TenantId` contract needed explicit tasks. | Accepted | Added plan tasks/tests for each item. |

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-plan-issue-41-tenant-onboarding-20260522214039.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P1 | Per-tenant mutex missing from reserve task. | Accepted | Plan §3 updated. |
| P1 | Startup recovery ordering not enforced. | Accepted | Plan §3 updated. |
| P1 | Concurrent same-tenant reserve test missing. | Accepted | Plan §7 updated. |
| P2 | Additional leak/outage/routing/suspend-cleanup tests and documentation tightening. | Accepted | Plan §4/§7/§8/§10/§11 updated. |
| P2 | Rerun advised explicit startup phase, mutex lifecycle note, H2-only `ACTIVE -> FAILED` gate, and non-flaky unique fallback test seam. | Accepted | Plan §3/§7 updated before implementation. |

### Integrated Step 3-R Findings

| Priority | Count | Status |
|---|---:|---|
| P0 | 0 | None in initial advisor review. |
| P1 | 0 | Closed after Claude rerun `.omx/artifacts/claude-plan-issue-41-tenant-onboarding-rerun-20260522214344.md`. |
| P2 | 0 | Actionable P2s applied before implementation. |
| P3 | 0 | No blocking nits retained. |

### 11. Review, Lessons, PR, Merge

- Step 6-R dual code review:
  - Codex 6-tier review on diff.
  - Claude Code CLI review on same diff/module slice.
  - close only when `P0=0`, `P1=0`.
- Add lesson:
  - `docs/lessons/2026-05-22-issue-41-tenant-onboarding-r2dbc.md`.
  - cross-link #42 Nightly/docs deferral and #04/#05 routing/executor reuse.
- Commit with Lore trailers.
- Push branch and open PR in English.
- Step 7-R:
  - PR comment with review summary.
  - formal GitHub review entry.
- CI gate:
  - `statusCheckRollup` all `SUCCESS`/`SKIPPED`.
- Merge using rebase merge after requested workflow gate is satisfied.
- Sync local `develop` after merge, prune/cleanup worktree, run qmd update/embed.

## Step 3 Checklist Completion Report

| Item | Status | Notes |
|------|--------|-------|
| Spec mapped to concrete tasks | Done | Tasks cover module, registry, routing, provisioning, API, tests, docs, CI. |
| Ordering is implementable | Done | Lower-level contracts precede controllers and integration tests. |
| Verification commands named | Done | Gradle, repo-test-summary, actionlint, detekt, diff check listed. |
| README locale set covered | Done | `README.md` and `README.ko.md` plus PNG asset. |
| New module wiring covered | Done | settings, Examples, Nightly decision, artifacts. |
| Lessons/PR/merge/sync covered | Done | Step 11 covers full requested sequence. |
