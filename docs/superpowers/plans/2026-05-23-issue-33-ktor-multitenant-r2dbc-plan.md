# Issue #33 Ktor R2DBC Multi-Tenant 구현 계획

## 분류

Type A - Full Design.

#33은 a new Ktor + Exposed R2DBC chapter module을 추가한다. 범위: request plugins, tenant-aware transactions, tests, README pairs, diagram assets, and
CI coverage decision.

## 승인된 명세

명세:
`docs/superpowers/specs/2026-05-23-issue-33-ktor-multitenant-r2dbc-design.md`.

단계 2-R status: `P0=0`, `P1=0`.

Claude 산출물:

- `.omx/artifacts/claude-issue-33-spec-review-20260523045833.md`
- `.omx/artifacts/claude-issue-33-spec-review-rerun-20260523050109.md`

## 기준 증거

구현 전:

```text
repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

결과: BUILD SUCCESSFUL in 33s. `:03-multitenant-spring-webflux:test`
executed 15 tests; Ktor production integration tests also passed.

## 구현 작업

1. 생성: module skeleton.
   - 추가: `10-multi-tenant/07-multitenant-ktor/build.gradle.kts`.
   - 사용: existing Ktor dependencies from
     `12-production-integration/02-ktor-production-integration`.
   - 유지: runtime dependencies to H2 R2DBC, R2DBC pool, Exposed R2DBC, Logback,
     and kotlinx serialization.
   - 검증: Gradle discovery through `./gradlew projects --console=plain`.
   - 추가: English KDoc for public module entrypoints and tenant helpers.
   - 생성: `src/test/resources/junit-platform.properties` and
     `src/test/resources/logback-test.xml` matching chapter 10 sibling modules.

2. actor/movie domain을 포팅한다.
   - Copy the chapter 10 actor/movie table and record shape into package
     `exposed.r2dbc.multitenant.ktor.domain`.
   - 유지: names comparable with `03-multitenant-spring-webflux`.
   - 회피: Spring annotations and Reactor imports.
   - Every actor/movie/domain `data class` implements `java.io.Serializable`
     and defines `companion object { private const val serialVersionUID = 1L }`.

3. 추가: tenant model and schema support.
   - 추가: `Tenants` enum with `korean` and `english`.
   - 추가: `SchemaSupport` returning enum-owned `Schema` values.
   - Raw request strings must never reach `SchemaUtils.setSchema`.

4. 추가: transaction boundary.
   - 추가: local `suspendTransactionWithTenant(tenant, db, ...)`.
   - Set schema inside the Exposed R2DBC transaction before repository access.
   - 유지: direct `suspendTransaction` limited to initialization helpers.
   - 추가: an architecture test or source scan assertion that request routes do
     not call bare `suspendTransaction`.

5. 추가: Ktor plugin and error handling.
   - Install `ContentNegotiation` with kotlinx JSON.
   - Install `StatusPages` for `InvalidTenantException` and malformed requests.
   - 추가: `TenantPlugin` that validates `X-TENANT-ID` and stores
     `Tenants.Tenant` in a private `AttributeKey`.
   - 모든 `X-TENANT-ID` 헤더 값을 읽는다. 값이 둘 이상이면
     trim한 모든 값이 같을 때만 허용하고, 그렇지 않으면
     `InvalidTenantException`.
   - 추가: `ApplicationCall.currentTenant()` extension that throws
     `IllegalStateException` when the plugin was not installed.
   - JSON error shape: `{"code":"INVALID_TENANT","message":"..."}`.
   - 검증: path/body inputs with bluetape4k validation helpers where
     applicable; use `check()` only for internal invariants.

6. 추가: routes and repository.
   - 추가: `routes/ActorRoutes.kt`.
   - Endpoint:
     - `GET /actors`
     - `GET /actors/{id}`
     - `POST /actors`
   - `POST /actors` exists to prove write isolation and should accept a small
     serializable request body.
   - 검증: actor ID as positive and request names as non-blank before DB
     access.
   - Repository methods must be side-effect obvious and return DTO records.
   - Public request/response DTO `data class` declarations implement
     `java.io.Serializable`, define `serialVersionUID`, and carry English KDoc
     when they teach the API surface.

7. 추가: startup initialization.
   - 생성: schemas and seed sample data for both tenants at module install.
   - Initialization is idempotent and fail-fast.
   - `runBlocking` is allowed only for synchronous startup initialization so
     fail-fast semantics are deterministic; it must not appear in request
     routes or repository methods.
   - Define one database/pool owner for the module. If a pooled
     `ConnectionFactory` is used, close it from `ApplicationStopped`.
   - 사용: pool max size `1` in the constrained-pool test configuration so tenant
     alternation actually reuses the same connection path.
   - 사용: distinct application/test DB URL if test isolation needs it.

8. 추가: tests.
   - 사용: Ktor `testApplication`.
   - 사용: a module-local Ktor test base instead of `AbstractR2dbcExposedTest`;
     this example is H2-only and tests Ktor request behavior rather than the
     shared multi-dialect matrix.
   - 커버: successful 읽는다: for both tenants.
   - 커버: missing, empty, whitespace, duplicate, and unknown tenant headers
     with `400` plus `INVALID_TENANT` JSON body.
   - 커버: lowercase `x-tenant-id`.
   - 커버: same-ID tenant isolation.
   - 커버: write isolation through `POST /actors`.
   - 커버: rapid tenant alternation under constrained pool size `1`.
   - 커버: overlapping concurrent requests for both tenants.
   - 커버: no bare route-path `suspendTransaction` 사용 여부.
   - 커버: no request-path `runBlocking` 사용 여부.
   - 사용: bluetape4k assertions only.

9. 추가: documentation and diagram.
   - 추가: module `README.md` and `README.ko.md`.
   - Link from `10-multi-tenant/README.md` and `README.ko.md`.
   - 추가: PNG diagram under `docs/images/readme-diagrams/` with source file if
     the repo pattern stores one. Check the existing readme-diagram convention
     before generating assets and keep the committed PNG path stable.
   - 문서화: that `X-TENANT-ID` is not authentication and production must bind
     routing to identity.
   - 문서화: intentional local duplication from the WebFlux helper.
   - 문서화: schema switch cost and forward link to #35.

10. 추가: workflow coverage.
    - 갱신: `.github/workflows/Examples.yml` paths and chapter 10 job/module
      list if current workflow requires explicit module entries.
    - 실행: `actionlint` if the workflow changes.
    - Record Nightly coverage decision in README and lesson.

11. Verify.
    - `./gradlew projects --console=plain`
    - `./gradlew :07-multitenant-ktor:compileKotlin --warning-mode all --console=plain`
    - `repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
   - `./gradlew detekt --parallel --console=plain`
    - `git diff --check`
    - `actionlint .github/workflows/Examples.yml` if changed.
    - PNG existence and size check.
    - IntelliJ diagnostics if available; otherwise record fallback.

12. 검토, lesson, PR, merge.
    - 실행: 단계 6-R current Codex 6-tier review and Claude CLI 6-tier review.
    - Fix all P0/P1 and rerun relevant checks.
    - 추가: `docs/lessons/2026-05-23-issue-33-ktor-multitenant-r2dbc.md`.
    - 커밋: with Lore trailers.
    - Push, create PR with `Closes #33`, post 단계 7-R PR comment and formal
      review.
    - CI success를 기다리고, rebase merge, branch/worktree cleanup, GNO sync를 수행한다.

## 위험 제어

- 유지: #34/#35/#69 out of scope.
- 금지: promote shared code from the Spring WebFlux module in this issue.
- Treat Ktor call attributes as the request-scope carrier; no fallback tenant.
- Tests must prove pool schema reset because schema state is connection state.
- 유지: root develop clean; all #33 artifacts stay inside the worktree branch.

## 단계 3-R 상태

초기 Claude 산출물:
`.omx/artifacts/claude-issue-33-plan-review-20260523050300.md`.

수락한 P1/P2 수정:

- 추가함: required new-module test resources.
- 추가함: Serializable + `serialVersionUID` requirement for data classes.
- 할당함: duplicate-header handling to `TenantPlugin`.
- 명확화함: Ktor test base, diagram convention check, and public DTO KDoc.

Claude 재실행 산출물:
`.omx/artifacts/claude-issue-33-plan-review-rerun-20260523050436.md`.

최신 통합 gate 상태: `P0=0`, `P1=0`, Gate `PASS`.
