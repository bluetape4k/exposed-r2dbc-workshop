# Issue #38 Schema-Per-Tenant R2DBC 설계

## 맥락

Issue #38 is part of the Chapter 10 multi-tenant epic (#37). The existing
`10-multi-tenant/03-multitenant-spring-webflux` module already contains a
Spring WebFlux + Exposed R2DBC implementation around the actors/movies domain.
This work re-baselines on that implementation and closes the gaps against the
issue acceptance criteria.

## 기존 Inventory

다음 파일은 유지하며 발전시킨다:

- `ExposedMultitenantWebfluxApp.kt`
- `config/ExposedR2dbcConfig.kt`
- `config/NettyConfig.kt`
- `config/SwaggerConfig.kt`
- `config/TenantConfig.kt`
- `controller/ActorController.kt`
- `domain/model/Mappers.kt`
- `domain/model/MovieRecords.kt`
- `domain/model/MovieSchema.kt`
- `domain/repository/ActorR2dbcRepository.kt`
- `domain/repository/MovieR2dbcRepository.kt`
- `tenant/DataInitializer.kt`
- `tenant/SchemaSupport.kt`
- `tenant/TenantFilter.kt`
- `tenant/TenantId.kt`
- `tenant/TenantInitializer.kt`
- `tenant/Tenants.kt`
- `AbstractMultitenantTest.kt`
- `ActorControllerTest.kt`
- `ExposedR2dbcConfigTest.kt`
- `ConnectionPoolSizingTest.kt`

대체하지 않는다: the actors/movies domain with a new orders aggregate. The current
domain is already used by the README and tests, and it is sufficient to prove
schema-per-tenant isolation.

## 목표

Spring WebFlux + Kotlin coroutine 예제에서 shared R2DBC database infrastructure로 tenant별 schema routing을 보여준다.

## 범위

- 보존: the existing actors/movies domain and `/actors` API.
- 해결: tenants from the `X-TENANT-ID` request header.
- 전파: the resolved tenant through Reactor context into coroutine
  controllers.
- 사용: `suspendTransactionWithCurrentTenant` to set the Exposed R2DBC schema for
  every tenant-aware transaction.
- 강화: error handling and tests for missing tenant, unknown tenant, and
  cross-tenant isolation.
- 문서화: schema-per-tenant tradeoffs in `README.md` and `README.ko.md`.
- 기록: CI/Nightly coverage decisions.

## 비목표

- 금지: implement connection-factory-per-tenant routing. That belongs to #39.
- 금지: implement Spring Security tenant authorization. That belongs to #40.
- 금지: implement tenant onboarding/provisioning flows. That belongs to #41.
- 금지: add a new aggregate or public API beyond what is needed for #38.

## Tenant 계약

- Header: `X-TENANT-ID`
- 지원 tenant ID: `korean`, `english`
- 누락 또는 blank header: `400 Bad Request`
- 알 수 없는 tenant ID: `400 Bad Request`

The existing default-on-missing behavior is changed because Issue #38 explicitly
requires a missing-tenant test. In a schema-per-tenant example, silent fallback
can hide data isolation bugs, so the request must name its tenant.

## 맥락 Propagation

사용: the existing `TenantFilter` and `TenantId` helpers:

- `TenantFilter` 읽는다: `X-TENANT-ID`, 검증한다, and 저장한다: `TenantId` in the
  Reactor context.
- Controller는 호출한다: `suspendTransactionWithCurrentTenant`, which 읽는다: the
  Reactor-context tenant through `currentReactorTenant()`.
- `currentTenant()` remains the coroutine-context helper for non-WebFlux direct
  calls; WebFlux controllers use `currentReactorTenant()` through
  `suspendTransactionWithCurrentTenant`.
- `TenantId.DEFAULT` and the fallback inside `currentReactorTenant()` are
  retained only for non-WebFlux direct calls and tests, with a warning log when
  the fallback is used. The HTTP WebFlux path must reject missing or blank
  headers before any fallback is used.

Spring 공식 문서는 다음을 확인한다: WebFlux supports coroutine controllers
and Kotlin coroutine-specific WebFilter support. Spring은 또한 다음을 문서화한다: Reactor
context as the propagation path for nested reactive operations.

## Schema Routing

Keep the current `SchemaUtils.setSchema(getSchemaDefinition(tenant))` strategy
instead of replacing all tables with dynamic schema-qualified table classes.
The existing repositories use unqualified actors/movies tables, and the
transaction helper is the module's intended teaching point.

Risk control:

- 설정: the tenant schema at the beginning of every tenant-aware transaction.
- 추가: tests that execute requests for different tenants through the same app
  instance and verify no rows leak.
- 회피: using plain `suspendTransaction` in controllers.
- 향후 변경이 추가하면 non-tenant transactions to this module, reset schema
  state explicitly or use schema-qualified table factories for that path.

The design accepts that `SchemaUtils.setSchema` is connection state. The example
teaches how to contain that state in a small transaction helper; #39 will cover
connection-factory isolation.

## API Surface

- `GET /actors`
  - Header: `X-TENANT-ID`
  - 해결된 tenant schema의 actor만 반환한다.
- `GET /actors/{id}`
  - Header: `X-TENANT-ID`
  - 해결된 tenant schema의 actor만 반환한다.

#38에는 새 endpoint가 필요하지 않다.

## 테스트

기존 test는 유지하고 focused case를 갱신/추가한다:

- `ActorControllerTest`
  - tenant parameterized successful read 유지.
  - unknown tenant error test 유지, aligned to `400`.
  - default-tenant missing-header expectation을 다음으로 대체: `400`.
  - cross-tenant isolation evidence 추가 또는 강화.
- `ExposedR2dbcConfigTest`
  - keep repository/schema initialization coverage.
- `ConnectionPoolSizingTest`
  - 변경 없이 유지.
- `AbstractMultitenantTest`
  - 변경 없이 유지 unless test bootstrap needs explicit reset.

## 문서화

Update the existing English/Korean README files without deleting current
architecture sections:

- clarify when schema-per-tenant is appropriate.
- clarify `X-TENANT-ID` is mandatory.
- record that #39 handles connection-factory-per-tenant.
- record CI/Nightly coverage decisions.

## CI/Nightly 결정

No new workflow is required for #38:

- CI runs the module through root `./gradlew test`.
- Nightly already references `:03-multitenant-spring-webflux:test` in
  `.github/workflows/nightly.yml`.
- Local verification must run
  `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`.
- If the module name changes, update this local command and the Nightly
  `:03-multitenant-spring-webflux:test` shard entry together.

## 증거

- Issue #38 requires schema routing, missing tenant, unknown tenant, and
  cross-tenant isolation tests.
- Existing source already implements actors/movies schema-per-tenant routing
  with `TenantFilter`, `TenantId`, `Tenants`, and
  `suspendTransactionWithCurrentTenant`.
- Existing Exposed examples use `SchemaUtils.createSchema` and
  schema-qualified test tables.
- Spring docs confirm coroutine controller support and Reactor context
  propagation.

## 검토 메모

초기 Claude advisor review 산출물:
`.omx/artifacts/claude-issue-38-spec-plan-review-20260522.md`.

Claude advisor re-review 산출물:
`.omx/artifacts/claude-issue-38-spec-plan-rereview-20260522.md`.

수락한 P0/P1 수정:

- 재기준화: on existing source and tests.
- 유지함: actors/movies domain instead of adding orders.
- 유지함: `X-TENANT-ID`.
- 선택함: missing/unknown tenant `400`.
- 유지함: `SchemaUtils.setSchema` and documented pooled connection-state risk.
- 추가함: explicit keep/modify decisions for existing tests and README files.

최신 gate 상태: `P0=0`, `P1=0`, Gate `PASS`.
