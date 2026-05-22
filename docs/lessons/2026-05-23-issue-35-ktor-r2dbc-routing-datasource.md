# Issue 35 - Ktor R2DBC Routing Datasource

## Context

Issue #35 adds the Ktor chapter 11 counterpart to the Spring WebFlux
`03-routing-datasource` example. The important difference is request context:
Spring uses Reactor Context and transaction operators, while Ktor uses
call-scoped attributes plus explicit value passing into the repository.

## Decisions

- Add `11-high-performance/06-routing-datasource-ktor-r2dbc` because chapter
  11 already has `04` and `05` Ktor cache modules.
- Use `RoutingRequestPlugin` to validate `X-Tenant-Id` and `X-Read-Only` once
  per call.
- Pass `RoutingRequest` into repository methods explicitly instead of reading
  ThreadLocal, Reactor Context, or global mutable state.
- Seed four H2 R2DBC targets (`default:rw`, `default:ro`, `acme:rw`,
  `acme:ro`) with distinct marker rows so tests prove the selected database,
  not only echoed route metadata.
- Reject `PATCH /routing/marker` when `X-Read-Only: true` is present rather
  than silently overriding conflicting caller input.
- Do not keep Ktor `application.conf`; explicit test modules and `main()` avoid
  double-installing plugins and leaking default resources.

## Verification

- `./gradlew projects :06-routing-datasource-ktor-r2dbc:compileKotlin :06-routing-datasource-ktor-r2dbc:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- `repo-test-summary -- ./gradlew :03-routing-datasource:test :04-cache-strategies-ktor-r2dbc:test :05-cache-strategies-ktor-r2dbc-coroutines:test :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

Codex 6-Tier review and Claude Code CLI 6-Tier review both passed with
P0 = 0 and P1 = 0 after the blank-marker and `application.conf` findings were
fixed.
