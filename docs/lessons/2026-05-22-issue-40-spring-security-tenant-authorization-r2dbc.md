# Issue #40 Spring Security Tenant Authorization R2DBC Lesson

## Context

Issue #40 added a chapter 10 workshop module for tenant-aware Spring Security on
top of the existing Exposed R2DBC multi-tenant routing example.

## Decision

Keep request tenant routing and authentication separate. Demo API keys, demo
sessions, and JWTs establish the authenticated tenant in Spring Security. Only
`AuthorizedTenantContextWebFilter` writes `TenantContextKeys.TENANT_ID`, and it
does so only after the authenticated tenant matches `X-TENANT-ID`.

JWT tenant claim problems are authorization failures, not authentication
failures. The JWT converter therefore accepts the token and leaves missing or
unknown tenant claims to the tenant authorization filter, where they become 403.

## Outcome

The new `05-spring-security-tenant-authorization-spring-webflux` module covers
API-key, JWT, and demo-session tenant authorization paths. `Examples.yml` now
runs the new module together with the existing chapter 10 routing example.

## Verification

- Claude spec/plan re-gate: `P0=0`, `P1=0`, PASS.
- Claude code-review retry with `claude -p --model opus --verbose --output-format stream-json`: `P0 = 0`, `P1 = 0`, PASS.
- `./gradlew :05-spring-security-tenant-authorization-spring-webflux:compileKotlin --warning-mode all --console=plain`
- `./gradlew :05-spring-security-tenant-authorization-spring-webflux:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :05-spring-security-tenant-authorization-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- `./gradlew projects --console=plain`
- `./gradlew detekt --parallel --console=plain`

## Future Guidance

For tenant authorization examples, never let a request header directly write the
R2DBC routing context. Add an architecture test that pins the single writer, and
cover both authenticated bad-header 400 and unauthenticated bad-header 401
ordering so filter-order regressions are visible.
