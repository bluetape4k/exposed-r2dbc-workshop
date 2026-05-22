# Issue 39 Connection-factory-per-tenant R2DBC

## Context

Issue #39 added chapter 10 module
`04-connection-factory-per-tenant-spring-webflux` to demonstrate tenant-specific
R2DBC connection factories and pools in Spring WebFlux + Exposed R2DBC.

## Decision

Use Spring `AbstractRoutingConnectionFactory` with a fixed tenant registry:
`korean` and `english`. HTTP requests fail closed through mandatory
`X-TENANT-ID`; direct non-HTTP routing can still use Spring's default target
when no lookup key exists. Exposed request work goes through
`TenantTransactionExecutor`, which preserves Reactor context across the suspend
transaction boundary.

## Outcome

The module keeps the actor API shape from the schema-per-tenant example while
switching isolation from schema state to tenant-owned R2DBC pools. README files
document the pool-count tradeoff and that tenant authorization remains issue
#40 scope.

## Verification

- `./gradlew projects --console=plain`
- `./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`:
  33 tests passed.
- `git diff --check`
- Claude gates: Step 2-R, Step 3-R, and Step 6-R all reached `P0=0`, `P1=0`.
- IDE diagnostics were not available because this worktree was not open in
  IntelliJ; compile/test evidence was used as fallback.

## Future Notes

- Keep `TenantTransactionExecutor` as the only request-path Exposed transaction
  boundary unless the architecture test allowlist is deliberately updated.
- `R2dbcDatabase` instances reuse registry-owned pools; do not add independent
  initializer pools unless ownership and shutdown are redesigned.
- If non-H2 profiles are added later, revisit Nightly shard coverage.
