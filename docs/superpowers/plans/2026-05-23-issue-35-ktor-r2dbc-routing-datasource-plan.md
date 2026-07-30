# Issue #35 Ktor R2DBC Routing Datasource 구현 계획

## 단계 1 - Baseline

- 사용: GNO for #35, exposed-workshop #49, and prior Ktor module lessons.
- Inspect `11-high-performance/03-routing-datasource`, #34, #69, and #33
  design notes.
- 실행: targeted compile/test 다음 위치 뒤: scaffolding; no baseline source change is
  expected.

## 단계 2 - Scaffold

- 생성: `11-high-performance/06-routing-datasource-ktor-r2dbc`.
- 사용: existing version catalog dependencies: Ktor server/test, kotlinx JSON,
  Exposed R2DBC, R2DBC H2/pool/spi, bluetape4k junit5, and logback.
- 유지: package root `exposed.r2dbc.examples.routing.ktor`.
- 검증: module discovery with `./gradlew projects --console=plain`.

## 단계 3 - Runtime

- 추가: `RoutingRequest` value types for tenant, mode, key, and validated request
  routing state.
- 추가: Ktor plugin:
  - resolve `X-Tenant-Id` with default fallback;
  - parse `X-Read-Only`;
  - force read-only for `/readonly`;
  - store `RoutingRequest` in call attributes.
- 추가: resource owner:
  - create four H2 R2DBC pools;
  - expose `RoutingDatabaseRegistry`;
  - initialize marker rows;
  - close all pools on application stop.
- 추가: repository and routes:
  - `GET /routing/marker`;
  - `GET /routing/marker/readonly`;
  - `PATCH /routing/marker`;
  - stable JSON responses.

## 단계 4 - Tests

- 사용: Ktor `testApplication` with unique database prefix per test.
- Assert route output and marker value for default/acme and rw/ro paths.
- Assert write path updates only the tenant rw target.
- Assert write path rejects `X-Read-Only: true` instead of silently overriding
  conflicting caller input.
- Assert invalid tenant/read-only inputs return structured `400`.
- Assert concurrent alternating calls preserve per-call routing by correlating
  each request tenant with its own response marker.

## 단계 5 - Docs and Workflow

- 추가: bilingual module README.
- 추가: PNG diagram in `docs/images/readme-diagrams/`.
- 갱신: chapter 11 README/README.ko module table and commands.
- Wire `.github/workflows/Examples.yml` path filters, chapter 11 test command,
  and artifacts.
- 추가: lessons file.

## 단계 6 - Verification and Review

- 실행: verification targets from the spec.
- 실행: Codex 6-Tier code review and Claude Code CLI 6-Tier code review.
- Fix all P0/P1 and rerun affected checks.

## 단계 7 - PR and Merge

- 커밋: with Lore protocol.
- Open PR with `Closes #35`.
- 추가: 단계 7-R review evidence comment and attempt formal review.
- Wait for CI, rebase merge, sync develop, cleanup branch/worktree, refresh
  GNO, and verify issue/PR state.
