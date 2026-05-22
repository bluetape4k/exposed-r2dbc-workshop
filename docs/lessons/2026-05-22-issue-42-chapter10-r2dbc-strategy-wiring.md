# Issue #42 Chapter 10 R2DBC Strategy Wiring Lesson

## Context

Issue #42 wires the chapter 10 Spring WebFlux R2DBC strategy examples into
chapter-level documentation and focused verification.

## Decision

Use `10-multi-tenant/README.md` and `README.ko.md` as the strategy entry point
instead of linking the root README directly to the newest module. The chapter
README compares schema-per-tenant, connection-factory-per-tenant, tenant
authorization, and runtime onboarding by selection criteria, isolation model,
and verification surface.

`Examples.yml` now runs all chapter 10 Spring WebFlux R2DBC strategy modules,
including `03-multitenant-spring-webflux`. Nightly's H2 full shard already runs
all modules. Non-H2 PostgreSQL/MySQL Nightly shards intentionally keep only
`03`, and the MariaDB smoke shard does not run chapter 10, because `04`, `05`,
and `06` are H2-focused workshop strategies.

## Outcome

The root README sends readers to the chapter-level strategy map. Focused example
CI now covers all chapter 10 Spring WebFlux strategy modules and uploads all
four modules' test reports.

## Verification

- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

## Future Guidance

When adding chapter-level strategy docs, link root README entries to the
chapter-level README, not to the newest leaf module. Keep `Examples.yml` aligned
with the chapter README's strategy table so discoverability and verification do
not drift.
