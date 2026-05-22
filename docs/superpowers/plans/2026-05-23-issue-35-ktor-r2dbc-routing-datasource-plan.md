# Issue 35 Ktor R2DBC Routing Datasource Plan

## Step 1 - Baseline

- Use qmd for #35, exposed-workshop #49, and prior Ktor module lessons.
- Inspect `11-high-performance/03-routing-datasource`, #34, #69, and #33
  design notes.
- Run targeted compile/test after scaffolding; no baseline source change is
  expected.

## Step 2 - Scaffold

- Create `11-high-performance/06-routing-datasource-ktor-r2dbc`.
- Use existing version catalog dependencies: Ktor server/test, kotlinx JSON,
  Exposed R2DBC, R2DBC H2/pool/spi, bluetape4k junit5, and logback.
- Keep package root `exposed.r2dbc.examples.routing.ktor`.
- Verify module discovery with `./gradlew projects --console=plain`.

## Step 3 - Runtime

- Add `RoutingRequest` value types for tenant, mode, key, and validated request
  routing state.
- Add Ktor plugin:
  - resolve `X-Tenant-Id` with default fallback;
  - parse `X-Read-Only`;
  - force read-only for `/readonly`;
  - store `RoutingRequest` in call attributes.
- Add resource owner:
  - create four H2 R2DBC pools;
  - expose `RoutingDatabaseRegistry`;
  - initialize marker rows;
  - close all pools on application stop.
- Add repository and routes:
  - `GET /routing/marker`;
  - `GET /routing/marker/readonly`;
  - `PATCH /routing/marker`;
  - stable JSON responses.

## Step 4 - Tests

- Use Ktor `testApplication` with unique database prefix per test.
- Assert route output and marker value for default/acme and rw/ro paths.
- Assert write path updates only the tenant rw target.
- Assert write path rejects `X-Read-Only: true` instead of silently overriding
  conflicting caller input.
- Assert invalid tenant/read-only inputs return structured `400`.
- Assert concurrent alternating calls preserve per-call routing by correlating
  each request tenant with its own response marker.

## Step 5 - Docs and Workflow

- Add bilingual module README.
- Add PNG diagram in `docs/images/readme-diagrams/`.
- Update chapter 11 README/README.ko module table and commands.
- Wire `.github/workflows/Examples.yml` path filters, chapter 11 test command,
  and artifacts.
- Add lessons file.

## Step 6 - Verification and Review

- Run verification targets from the spec.
- Run Codex 6-Tier code review and Claude Code CLI 6-Tier code review.
- Fix all P0/P1 and rerun affected checks.

## Step 7 - PR and Merge

- Commit with Lore protocol.
- Open PR with `Closes #35`.
- Add Step 7-R review evidence comment and attempt formal review.
- Wait for CI, rebase merge, sync develop, cleanup branch/worktree, refresh
  qmd, and verify issue/PR state.
