# Issue #204 구현 계획 Step 3-R 통합 검토

## 검토 범위와 게이트

- 대상: 승인된 설계 `docs/superpowers/specs/2026-08-27-issue-204-spring-boot-exposed-r2dbc-repository-design.md` (`2bb7763d`)와 실행 계획 `docs/superpowers/plans/2026-08-27-issue-204-spring-boot-exposed-r2dbc-repository-plan.md`.
- worktree: `feat/issue-204-spring-boot-exposed-r2dbc`.
- 범위: Type A 새 sibling 모듈, provider repository scan, app-owned R2DBC pool/database, transaction·Flow·cancellation·lifecycle, HTTP example, EN/KO README와 SVG/PNG, CI/Nightly, Issue #204 metadata.
- 규칙: 각 관점은 plan/spec만 read-only로 검토하고, P0/P1은 구현 전에 닫는다. 구현·GitHub mutation·heavy test는 이 게이트의 범위가 아니다.

## 독립 관점 결과

| 관점 | 최초 finding | 계획 반영 | 최종 상태 |
|---|---|---|---|
| Performance | `streamAll().take(1)`와 `toList()`만으로 lazy demand·connection release를 증명할 수 없음; outer/no-outer acquire 차이와 pool contention·반복 안정성 부족 | test-only `RecordingConnectionFactory`, emitted/open/close counter, outer acquire relation, `maxSize=1`·100ms, child cancellation, bounded 8 callers, 별도 `PerformanceStabilityTest`·`repeat(5)`, HTTP materialization 한정, statement round-trip/upstream demand는 N/A | P0/P1 없음 |
| Stability | CI/Nightly registration chain, live catalog와 overlay toolchain 충돌, cancellation scope, pool lifecycle/invalid context isolation, test harness 경계 부족 | T1 resolved toolchain·`-PuseDB`/`USE_FAST_DB` preflight, `ci.yml`/Nightly/script/artifact 대조, child cancellation, isolated context close, app-owned fixture drop/create/seed, `runTest` 우선, 동일 `@ResourceLock`, H2-only external-shard N/A | P0/P1 없음 |
| Security | client `id` overwrite, 선택적 validation, required property omission, closed-pool reuse denial, 오류·로그 민감정보 노출 위험 | id 없는 DTO·unknown-field 4xx/no overwrite, `@Valid`·name 120/description 500 `Size`, WebFlux `ProblemDetail`, sanitizer·secret capture, invalid context, closed-pool denial | P0/P1 없음 |
| Operator/Ops | 운영 pool bound/no-retry, graceful drain, initializer readiness/idempotence, sanitized lifecycle/error logging, demo-only/runbook, CI artifacts 부족 | 운영 pool 상한·timeout·`acquireRetry=0`, graceful shutdown 10s와 단일 `DisposableBean`, initializer 15s fail-fast, low-cardinality log, demo-only·metrics N/A runbook, artifact/retention 대조, process-group cleanup | P0/P1 없음 |
| Developer/API | scanner가 생성하는 interface 형태, TDD 전체 compile 경계, `withTables` hidden outer tx, no-outer partial commit 모순, cancellation/lifecycle, provider `deleteById: Unit`, `saveAll` 오정보 | interface/default mapping, slice별 RED→GREEN, app-owned drop/create fixture, deterministic constraint failure, child cancellation, 204/404 `deleteIfExists`, provider capability와 예제 비범위 구분 | P0/P1 없음 |
| User/Caller | locale별 SVG/PNG·README 언어 계약, HTTP status/body/content-type/missing, 선택 기준·migration, compile-valid snippets, KDoc·runbook 불명확 | en/ko pair와 대응 README asset, 명시 HTTP 계약/ProblemDetail, 05/06 side-by-side 표, source read-back·bootRun smoke, public KDoc/metrics N/A/runbook | P0/P1 없음 |

## Main integration trace

| 통합 확인 | 근거 | 결과 |
|---|---|---|
| 승인 배치와 기존 관례 | `exposed-workshop`의 JDBC `09-spring/04`·`05`와 현재 workshop `09-spring/05`·`07`을 근거로 `09-spring/06` sibling을 선택 | PASS |
| Gradle/catalog | leaf-directory 자동 discovery, versionless provider alias, validation alias, live catalog/JVM preflight | PASS |
| 실행 순서 | T1 preflight/build → T2 config RED → T3 config GREEN → T4 scan/CRUD RED→GREEN → T5 transaction → T6 lifecycle → T7 HTTP → T8 docs → T9 diagrams → T10 metadata/workflow → T11 verification → T12 closeout | PASS |
| TDD/코루틴/DB | production code 전 RED, `runTest` 우선, setup/cleanup과 실제 repository call 분리, explicit outer `suspendTransaction`, cancellation 재전파 | PASS |
| 책임·rollback | app pool/database owner, provider proxy one-call tx, single `DisposableBean`, isolated invalid/closed pool, narrow task rollback | PASS |
| 문서·시각 자료 | EN/KO source-equivalent README, provider manual link, locale topology-equivalent SVG/PNG, semantic/asset/visual audit | PASS |
| CI·Nightly | Examples path/job, `ci.yml` dynamic script/Kover/artifact, Nightly H2 full 및 외부 DB shard N/A 근거, Java alignment | PASS |
| Issue/PR/merge | live #204 body read-back, Korean PR `## DoD Status`, fresh exact-head approval, CI green, rebase merge, root `develop` sync | PASS (T12 gate) |

## Writer gate

- SPW-01: PASS — Issue, 승인 경계, 선택 경로, provider API/source와 비범위를 명시했다.
- SPW-02: PASS — 목표, ordering, 파일 ownership, RED/GREEN 명령, docs/diagram/workflow, risks/rollback, acceptance traceability를 포함했다.
- SPW-03: PASS — `README.md` English, `README.ko.md`·KDoc·공개 metadata Korean 계약을 분리하고 API/경로/명령/URL은 보존했다.
- SPW-04: PASS — JDBC sibling 배치, shared H2/lifecycle, live catalog/BOM, provider 1.12.1 source/manual을 반영했다.
- SPW-05: PASS — plan/spec read-back, required symbol/자리표시자 검사, `git diff --check`, `audit-korean-terms.mjs` 결과를 확인했다.

## 비차단 P2 처분

- statement/execute 단위 round-trip과 upstream demand는 이 학습 예제의 계약이 아니므로 `N/A`로 제한하고 connection-scope·cancellation·cleanup만 검증한다.
- `withTables`는 hidden outer transaction을 만들기 때문에 app-owned fixture의 drop/create/seed transaction으로 대체한다. shared helper의 UTC·transaction restoration·mutex 규칙은 custom fixture에 재현하고, 동일 H2 resource key를 `@ResourceLock`으로 직렬화한다.
- H2 `regular` profile은 demo-only이며 `-PuseDB=H2`/`USE_FAST_DB=true` 지원 근거를 T1에서 read-back한다. 외부 DB matrix 실행은 호환성 증거로 세지 않는다.
- metrics/health/Actuator와 production readiness는 제공하지 않는 범위로 README와 runbook에 명시한다.

## 판정

여섯 관점의 최초 finding과 affected lane 재검토 결과 P0=0/P1=0이다. P2는 위와 같이 범위·N/A·후속 검증으로 처분했으며, 이 게이트를 통과해 T1 구현 단계로 이동할 수 있다. 현재 문서는 구현 시작 전 검토 artifact이며 구현·테스트·PR·merge 증거를 대신하지 않는다.
