# Issue #41 Tenant Onboarding R2DBC Lesson

## Context

Issue #41 added chapter 10 module
`06-tenant-onboarding-spring-webflux` to demonstrate runtime tenant metadata
reservation, R2DBC pool provisioning, Exposed schema creation, seed data, and
request routing without restarting the application.

## Decision

Keep tenant onboarding as an explicit admin operation. `TenantProvisioner`
serializes cap-check, reservation, pool creation, schema creation, runtime
registration, and activation under one onboarding mutex so a tenant limit cannot
race with concurrent requests. Per-tenant mutexes still protect direct registry
reuse.

Use domain exceptions in the tenant layer and map them in
`TenantApiExceptionHandler`; only the controller keeps HTTP-specific
`ResponseStatusException` for the admin-token boundary. Cleanup runs in
`NonCancellable` and preserves the original provisioning failure by isolating
schema drop, pool close, and metadata delete failures.

## Outcome

The module documents when onboarding is a better fit than shared-table tenancy,
fixed tenant pools, or tenant authorization. README diagrams use a committed PNG
under `docs/images/readme-diagrams/` with the SVG source kept next to it.
`Examples.yml` now runs the onboarding module with the other chapter 10
examples.

## Verification

- Claude Step 6-R full/slim/delta review artifacts reached `P0=0`, `P1=0`.
- Codex 6-tier review found no remaining P0/P1 after cleanup, ordering, and
  failure-path fixes.
- `repo-test-summary -- ./gradlew :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`:
  14 tests passed.
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check`

## Future Guidance

- Keep tenant pool lifecycle nonblocking on request paths. Use
  `awaitSingle()`/`awaitSingleOrNull()` around R2DBC pool warmup and disposal
  when called from suspend code.
- Failure injection tests should cover every provisioning checkpoint, not only
  post-registration cleanup.
- `bufferUntilChanged` grouping requires an explicit SQL `orderBy` on the group
  key before rows are converted to flows.
- The local `bluetape4k-github` qmd sync did not include
  `exposed-r2dbc-workshop` during this work. Refresh that collection or extend
  the sync script before relying on qmd for this repository's issue snapshot.
