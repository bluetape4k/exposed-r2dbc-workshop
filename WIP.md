# 작업 현황 - exposed-r2dbc-workshop

스냅샷: 2026-06-02 KST
범위: 2026-01-01 이후 생성되고 `debop`에게 할당된 열린 GitHub 이슈.
열린 이슈 수: 1개.

## 최근 완료

- CI/Nightly, version catalog migration, Spring Boot 4 정렬, dependency governance, compatibility guard가 병합됨.
- 테스트 정리와 Kluent → `bluetape4k-assertions` 마이그레이션이 병합됨.
- README hero/architecture refresh가 병합됨.
- 새 `bluetape4k-exposed` API를 위한 CTE Query Builder 예제 브랜치는 별도로 존재함.
- QMD 기반 audit가 shared `withTables()` cancellation handling 이슈 `#54`를 등록함.

## 현재 방향

chapter 10-12 예제를 확장하기 전에 shared R2DBC test infrastructure의 cancellation safety를 유지한다. 새 예제는 structured coroutine cancellation을 보존하고 cleanup failure를 명확히 보고하는 helper에 의존해야 한다.

## 우선순위 대기열

| 우선순위 | 이슈 | 난이도 | 메모 |
|---|---|---:|---|
| P2 | [#54](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/54) `withTables()` can swallow coroutine cancellation during cleanup | M | shared test helper가 suspend cleanup 주변에서 `runCatching`과 넓은 `Throwable` 처리를 사용함. |
| P3 | [#32](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/32) Ktor examples for chapters 10 and 11 epic | L | `#33`-`#36`의 parent. |
| P3 | [#33](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/33) Ktor R2DBC multi-tenant example | M | `#32`의 child. |
| P3 | [#34](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/34) Ktor R2DBC cache strategies example | M | `#32`의 child. |
| P3 | [#35](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/35) Ktor R2DBC routing datasource example | M | `#32`의 child. routing lifecycle/cancellation behavior를 명확히 유지한다. |
| P3 | [#36](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/36) wire Ktor R2DBC chapter examples into docs and verification | S | `#33`-`#35` 완료 후 마무리. |
| P3 | [#37](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/37) Spring WebFlux R2DBC multi-tenant strategy epic | L | `#38`-`#42`의 parent. |
| P3 | [#38](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/38) schema-per-tenant Spring WebFlux R2DBC example | M | `#37`의 child. |
| P3 | [#39](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/39) connection-factory-per-tenant Spring WebFlux R2DBC example | M | `#37`의 child. |
| P3 | [#40](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/40) Spring Security tenant authorization R2DBC example | M | `#37`의 child. |
| P3 | [#41](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/41) tenant onboarding/provisioning R2DBC example | M | `#37`의 child. |
| P3 | [#42](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/42) wire chapter 10 examples into docs and verification | S | `#38`-`#41` 완료 후 마무리. |
| P3 | [#43](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/43) chapter 12 R2DBC production integration epic | L | `#44`-`#49`의 parent. |
| P3 | [#44](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/44) Spring Boot 4 and Ktor application architecture examples | M | `#43`의 child. |
| P3 | [#45](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/45) authentication/session examples | M | `#43`의 child. |
| P3 | [#46](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/46) outbox realtime examples | M | `#43`의 child. |
| P3 | [#47](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/47) HTTP client outbox/idempotency examples | M | `#43`의 child. |
| P3 | [#48](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/48) observability/readiness examples | M | `#43`의 child. |
| P3 | [#49](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/49) wire chapter 12 examples into docs and verification | S | `#44`-`#48` 완료 후 마무리. |

## 의존성 맵

```text
#54 withTables cancellation-safe cleanup
  -> 향후 R2DBC multi-database example을 위한 shared test baseline
  -> chapter 10/11/12 test 확장 전에 수정

#32 Ktor chapters 10/11 epic
  -> #33 Ktor R2DBC multi-tenant
  -> #34 Ktor R2DBC cache strategies
  -> #35 Ktor R2DBC routing datasource
  -> #36 docs and verification

#37 Spring WebFlux R2DBC chapter 10 multi-tenant epic
  -> #38 schema-per-tenant
  -> #39 connection-factory-per-tenant
  -> #40 Spring Security tenant authorization
  -> #41 onboarding/provisioning
  -> #42 docs and verification

#43 chapter 12 R2DBC production integration epic
  -> #44 application architecture
  -> #45 authentication/session
  -> #46 outbox realtime
  -> #47 HTTP client outbox/idempotency
  -> #48 observability/readiness
  -> #49 docs and verification
```

## WIP 제한

| 작업 레인 | 제한 | 현재 다음 작업 |
|---|---:|---|
| shared test correctness | 1 | R2DBC example을 넓게 확장하기 전에 `#54` 처리. |
| Ktor R2DBC examples | 1 | `#32` 아래 child 하나를 시작하고, children 완료 후 `#36` 마무리. |
| Spring WebFlux R2DBC multi-tenant examples | 1 | `#37` 아래 child 하나를 시작하고, children 완료 후 `#42` 마무리. |
| Production integration examples | 1 | `#43` 아래 child 하나를 시작하고, children 완료 후 `#49` 마무리. |
