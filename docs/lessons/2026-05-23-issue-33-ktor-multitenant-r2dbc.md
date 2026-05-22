# Issue 33 Ktor R2DBC Multi-Tenant

## Context

Issue #33 added `10-multi-tenant/07-multitenant-ktor`, a Ktor equivalent of
the chapter 10 schema-per-tenant Spring WebFlux R2DBC example.

## Decision

Use Ktor call attributes as the request tenant carrier and keep a local
`suspendTransactionWithTenant` helper in the Ktor module. The duplication with
the WebFlux helper is intentional: WebFlux teaches ReactorContext propagation,
while Ktor teaches call-scoped tenant resolution without ThreadLocal or Spring
filters.

`X-TENANT-ID` remains a workshop routing signal only. The README warns that
production systems must bind tenant routing to authenticated identity.

## Outcome

The module exposes `GET /actors`, `GET /actors/{id}`, and `POST /actors` over
Ktor. Tests cover structured tenant errors, duplicate headers, write isolation,
rapid tenant alternation through a pool of size `1`, and overlapping tenant
requests.

## Verification

- `./gradlew projects --console=plain`
- `./gradlew :07-multitenant-ktor:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`: 9 tests passed.
- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain`: chapter 10 set passed.
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- PNG diagram: `docs/assets/readme-diagrams/10-multi-tenant-07-multitenant-ktor-architecture-01.png`, 1400 x 760.
- IntelliJ diagnostics were unavailable for this worktree (`project_not_found`); compile/test/static checks were used as fallback.

## Future Notes

- Keep raw tenant header values out of schema selection. Only validated
  `Tenants.Tenant` enum instances should cross the Ktor plugin boundary.
- Keep request routes free of bare `suspendTransaction` and `runBlocking`.
- If a later issue adds non-H2 Ktor tenant coverage, revisit Examples/Nightly
  shard scope and H2-only README wording.
