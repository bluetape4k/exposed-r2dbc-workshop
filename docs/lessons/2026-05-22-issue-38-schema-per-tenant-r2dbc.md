# Issue 38 Schema-per-tenant R2DBC lesson

## Context

Issue #38 targeted the existing `10-multi-tenant/03-multitenant-spring-webflux`
example, not a new empty module. The module already had actor/movie WebFlux
controllers, schema initialization, tenant propagation, README files, and focused
tests.

## Decision

Keep the existing actor/movie domain and `X-TENANT-ID` header contract, but make
HTTP tenant resolution fail closed:

- Missing or blank tenant header returns `400 Bad Request`.
- Unknown tenant header returns `400 Bad Request`.
- Direct non-WebFlux helper fallback to `Tenants.DEFAULT_TENANT` is retained only
  for example calls and now logs a warning when used.
- Read isolation is proved with positive fixture assertions for the same actor
  ID across `korean` and `english` schemas.

## Outcome

The example now documents the request tenant contract in `README.md` and
`README.ko.md`, aligns `TenantFilter` behavior with that contract, and keeps CI
coverage notes explicit for both root CI and Nightly.

## Verification

- `./gradlew :03-multitenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
  passed.
- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`
  passed 15 tests in 6.4s after the advisor fixes.
- `git diff --check` passed before final commit verification.
- IntelliJ diagnostics were attempted but unavailable because this worktree was
  not open in the IDE project list; compile/test evidence was used instead.
- Claude advisor gate passed with `P0=0, P1=0` after KDoc, fallback logging, and
  positive fixture assertion fixes.

## Future Guidance

- Query the current module contents before trusting an issue description or old
  plan; this module was already implemented.
- `WebTestClient` rejects a single-space header value before the request reaches
  the app. Use an empty string header to exercise the application-side blank
  tenant branch.
- If the helper fallback is removed later, update README snippets and tests in
  the same PR so the WebFlux and direct-coroutine paths remain distinguishable.
