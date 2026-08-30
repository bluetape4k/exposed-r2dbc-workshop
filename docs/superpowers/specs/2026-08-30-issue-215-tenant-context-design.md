# Issue #215 멀티 테넌트 reference consumer carrier 전환 설계

상태: 승인된 설계, 구현 전 명세 검토 대기
작성일: 2026-08-30
대상 저장소: `exposed-r2dbc-workshop`
관련 이슈: <https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/215>

## 1. 결정 요약

Issue #215의 목적은 WebFlux와 Ktor 예제가 각각 소유한 테넌트 전달 키를
제거하고, 이미 공개된 `bluetape4k-tenant` carrier adapter를 reference
consumer로 사용하는 것이다. HTTP 헤더의 유효성 검증, 허용 테넌트 레지스트리,
테넌트별 데이터베이스 선택 같은 예제의 애플리케이션 책임은 유지한다.

두 예제 모두 로컬 catalog에 다음 versionless alias를 추가한다.

| alias | artifact | 소비 모듈 |
| --- | --- | --- |
| `bluetape4k-tenant` | `io.github.bluetape4k:bluetape4k-tenant` | 공통 carrier API 확인이 필요한 코드 |
| `bluetape4k-tenant-reactor` | `io.github.bluetape4k:bluetape4k-tenant-reactor` | WebFlux |
| `bluetape4k-ktor-tenant` | `io.github.bluetape4k:bluetape4k-ktor-tenant` | Ktor |

artifact 버전은 이 저장소에서 직접 pin하지 않는다. `bluetape4k-dependencies`
`2.0.0-SNAPSHOT`이 import한 BOM과 공개 POM이 provider 버전을 결정하는
단일 권위이다. 이 작업에서는 새 멀티 테넌트 모듈이나 공통-core 기본값을
추가하지 않는다.

WebFlux는 `ReactorTenantContext.withTenant(context, TenantId(value))`로
현재 subscription의 `Context`를 감싸고, connection factory는
`currentOrNull(contextView)?.value`로 읽는다. Ktor는
`KtorTenantContext.bindTenant(call, TenantId(value))`로 `ApplicationCall`에
한 번 바인딩하고, route/transaction 경계에서는
`KtorTenantContext.requireCurrent(call).value`를 사용한다.

Ktor adapter의 공개 계약에는 scoped override가 없다. 따라서 기존의 “중첩
다른 테넌트로 잠시 교체” fixture를 upstream 계약에 맞게 바꾼다. 중첩 성공은
현재 바인딩을 다시 읽어 바깥 테넌트와 같은 값을 관찰하고, 중첩 다른 테넌트
바인딩은 `TenantAlreadyBoundException`을 관찰한다. 두 경우 모두 바깥
바인딩은 변경되지 않으며, 별도 `ApplicationCall`에는 값이 누출되지 않는다.
upstream에 scoped API를 추가하는 일은 이번 consumer 이슈의 범위가 아니다.

## 2. 문제와 현재 상태

현재 예제는 provider artifact와 동일한 의미의 carrier를 각자 다시 구현한다.

### WebFlux

- `10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantFilter.kt`
  가 헤더를 검증한 뒤 문자열 tenant id를 로컬 Reactor key에 기록한다.
- 같은 패키지의 `TenantContextKeys.kt`가 해당 key를 소유한다.
- `TenantRoutingConnectionFactory.kt`가 `ContextView`에서 문자열을 꺼내
  connection factory를 선택한다.
- `TenantTransactionExecutor.kt`는 Reactor context를 coroutine context로
  연결한다. 이 설계에서는 bridge의 책임을 바꾸지 않고 carrier의 생산/소비
  지점만 provider adapter로 정렬한다.

### Ktor

- `10-multi-tenant/07-multitenant-ktor/src/main/kotlin/exposed/r2dbc/multitenant/ktor/tenant/TenantPlugin.kt`
  이 헤더를 검증한 뒤 로컬 `TenantAttributeKey`에 `Tenants` enum을 기록한다.
- `tenant/TenantId.kt`가 그 key와 헤더 관련 local carrier 타입을 소유한다.
- route와 transaction helper가 `ApplicationCall.attributes`의 로컬 key를
  직접 읽는다.
- `src/test/kotlin/.../KtorMultitenantApplicationTest.kt`의 중첩 fixture는
  local attribute를 다른 테넌트로 덮어썼다가 복원하는 동작을 검증한다.

이 상태는 헤더 검증 정책과 전달 carrier의 책임을 섞고, 두 실행 모델이
서로 다른 key의 identity/중첩 규칙을 갖게 한다. Issue #215는 공개 provider
계약을 실제 consumer가 사용하는 형태로 바꾸고, 그 경계를 결정론적 fixture로
고정하도록 요구한다.

## 3. 외부 계약과 확인 근거

대상 provider는 `bluetape4k-projects`의 공개 API이며, 이 작업의 기준 commit은
`08d451e6` 계열의 tenant context 구현이다. 다음 공개 타입과 함수만 사용한다.

| 타입 | 공개 계약 | consumer 적용 |
| --- | --- | --- |
| `io.bluetape4k.tenant.TenantId` | inline value class, blank 값만 거부 | local 검증 후 `TenantId(value)` 생성 |
| `io.bluetape4k.tenant.TenantContext` | `currentOrNull(): TenantId?`, `requireCurrent(): TenantId`, `withTenant(tenantId, block)`을 정의하는 공통 interface | provider carrier의 공통 의미를 확인하는 계약 |
| `io.bluetape4k.tenant.reactor.ReactorTenantContext` | `currentOrNull(ContextView)`, `requireCurrent(ContextView)`, `withTenant(Context, TenantId)` | WebFlux producer/consumer |
| `io.bluetape4k.ktor.tenant.KtorTenantContext` | `currentOrNull(ApplicationCall)`, `requireCurrent(ApplicationCall)`, `bindTenant(ApplicationCall, TenantId)` | Ktor producer/consumer |

확인한 Ktor 규칙은 다음과 같다.

1. carrier key는 private identity이므로 같은 이름의 외부 `AttributeKey`로
   충돌시킬 수 없다.
2. 하나의 call에 두 번째 tenant를 bind하면 기존 값을 덮어쓰지 않고
   `TenantAlreadyBoundException`을 던진다.
3. dispatcher hop, 예외, cancellation 뒤에도 call 단위 값이 유지되며,
   다른 call로 값이 전파되지 않는다.

근거 링크:

- `TenantContext`: <https://github.com/bluetape4k/bluetape4k-projects/blob/08d451e6/bluetape4k/tenant/src/main/kotlin/io/bluetape4k/tenant/TenantContext.kt>
- `TenantId`: <https://github.com/bluetape4k/bluetape4k-projects/blob/08d451e6/bluetape4k/tenant/src/main/kotlin/io/bluetape4k/tenant/TenantId.kt>
- `ReactorTenantContext`: <https://github.com/bluetape4k/bluetape4k-projects/blob/08d451e6/bluetape4k/tenant-reactor/src/main/kotlin/io/bluetape4k/tenant/reactor/ReactorTenantContext.kt>
- `KtorTenantContext`: <https://github.com/bluetape4k/bluetape4k-projects/blob/08d451e6/ktor/tenant/src/main/kotlin/io/bluetape4k/ktor/tenant/KtorTenantContext.kt>
- `TenantAlreadyBoundException`: <https://github.com/bluetape4k/bluetape4k-projects/blob/08d451e6/ktor/tenant/src/main/kotlin/io/bluetape4k/ktor/tenant/TenantAlreadyBoundException.kt>
- upstream Ktor contract tests: <https://github.com/bluetape4k/bluetape4k-projects/tree/08d451e6/ktor/tenant/src/test/kotlin>

의존성 handoff도 구현 전에 확인했다. `bluetape4k-dependencies` PR #215가
merge되어 develop `9495811cbfeb84e378bd6eaae3e4fb85d50f4ca5`에 반영되었고,
CI run `33295992239`와 Publish Snapshot run `33296408331`이 성공했다. 공개
timestamped POM
`bluetape4k-dependencies-2.0.0-20260830.061434-4.pom`은 HTTP 200이며,
`bluetape4k-bom:2.0.0-SNAPSHOT`과 `bluetape4k-exposed-bom:2.0.0-SNAPSHOT`
계약을 포함한다. 이 근거는 consumer의 versionless alias가 provider artifact를
해석할 수 있다는 전제만 뒷받침하며, consumer 테스트 성공을 대신하지 않는다.

## 4. 승인된 설계

### 4.1 의존성 및 catalog

`gradle/libs.versions.toml`의 Bluetape4k 섹션에 세 alias를 versionless로
추가한다. 기존 `bluetape4k-dependencies` 버전 선언과 다른 버전 source를
만들지 않는다. 두 target module의 `build.gradle.kts`는 각각 필요한 adapter
alias를 `implementation`으로 선언한다.

추가하지 않는 것:

- provider artifact의 직접 version 문자열
- `bluetape4k-bom` 또는 `bluetape4k-exposed-bom`의 module-level 직접 import
- 새 `multi-tenant` 또는 carrier common module
- local adapter wrapper가 소유하는 두 번째 context key

### 4.2 WebFlux producer/consumer

흐름은 다음과 같다.

```text
HTTP header
  -> TenantIdResolver (trim/형식/registry 검증, local 책임)
  -> TenantId(value)
  -> ReactorTenantContext.withTenant(exchange.context(), tenantId)
  -> downstream subscription Context
  -> TenantRoutingConnectionFactory
       -> ReactorTenantContext.currentOrNull(contextView)?.value
       -> tenant pool 선택
```

`TenantFilter`의 invalid/unknown header 응답과 HTTP 경로 바깥의
`Tenants.KOREAN` default fallback은 유지한다. default는 carrier에 넣지
않으며, request 경로에서 header가 유효하면 반드시 adapter를 통해 context를
설정한다. `TenantRoutingConnectionFactory`가 context에 값이 없을 때
`Mono.empty()`를 반환하는 기존 명시적 실패 경계도 유지한다.

`TenantContextKeys.kt`는 production source에서 제거한다. 테스트는 private
local key를 다시 만들지 않고 `ReactorTenantContext` API를 통해 context를
생성/조회한다.

### 4.3 Ktor producer/consumer

흐름은 다음과 같다.

```text
X-Tenant-Id header
  -> TenantPlugin (trim/형식/registry 검증, local 책임)
  -> TenantId(value)
  -> KtorTenantContext.bindTenant(call, tenantId)
  -> route/transaction
       -> KtorTenantContext.requireCurrent(call).value
       -> tenant schema 선택
```

`TenantPlugin`은 call마다 한 번만 bind한다. validation 실패와 unknown tenant의
기존 status 응답은 유지한다. `TenantAttributeKey`와 그 key를 직접 읽는
production 코드는 제거한다. route와 `TenantTransaction`은 shared adapter의
`requireCurrent`로 현재 값을 얻는다.

중첩 fixture는 다음 상태 전이를 검증한다.

| 단계 | 기대 상태 |
| --- | --- |
| outer bind(`korean`) | `requireCurrent(call) == korean` |
| nested success | 새 bind 없이 `currentOrNull(call) == korean` |
| nested duplicate bind(`english`) | `TenantAlreadyBoundException`; outer 값은 `korean` |
| fixture 종료 | outer 값은 계속 `korean` |
| 새 call | `currentOrNull(newCall) == null` |

이 fixture는 “중첩 성공”을 scoped tenant override가 아니라 call-bound 값을
재관찰하는 성공으로 정의한다. 다른 테넌트로 임시 교체하는 API가 필요하다는
요구는 별도 upstream 이슈로 분리해야 하며, 이 작업에서 private key/reflection
또는 local fallback으로 흉내 내지 않는다.

### 4.4 문서

각 예제의 `README.md`와 `README.ko.md`를 같은 사실 집합으로 갱신한다.

- 예제가 provider carrier의 reference consumer라는 목적
- WebFlux의 Reactor subscription carrier와 Ktor의 call carrier 차이
- 헤더 검증은 application-local 책임이고 carrier identity는 provider 책임이라는
  경계
- Ktor adapter가 one-call binding이며 duplicate bind를 거부한다는 제한
- HTTP 경로 바깥 WebFlux default fallback은 carrier default가 아니라 fixture
  편의 동작이라는 설명
- 실행/검증 명령과 기대하는 deterministic fixture 범위

이번 변경은 기존 이미지/다이어그램을 바꾸지 않는다. 설명만으로 carrier
경계와 one-call 제한을 검증할 수 있고 새 시각 자산은 필요하지 않다.

## 5. 실패 모드와 보호 규칙

| 실패 모드 | 관찰해야 할 결과 | 보호 규칙 |
| --- | --- | --- |
| 헤더 누락/공백/형식 오류 | 기존 4xx 응답, adapter bind 없음 | resolver 검증을 carrier 생성보다 먼저 수행 |
| registry에 없는 tenant | 기존 unknown-tenant 오류, 다른 pool/schema 접근 없음 | local `Tenants`/registry를 유지 |
| WebFlux subscription cancellation | 취소된 branch가 다른 branch의 context를 바꾸지 않음 | `Context` immutable semantics와 deterministic interleave fixture 유지 |
| WebFlux context 없음 | routing factory가 명시적으로 empty를 반환 | HTTP 밖 default를 routing fallback으로 재사용하지 않음 |
| Ktor duplicate bind | `TenantAlreadyBoundException`, 기존 값 불변 | nested override를 구현하지 않고 공개 bind 계약만 사용 |
| dispatcher hop/exception | 같은 call의 값 유지 | call attribute adapter를 통해서만 읽기 |
| 새 call 또는 병렬 call | tenant 값 누출 없음 | private provider key와 call scope에 의존 |
| 공개 POM/catalog drift | dependency resolution 또는 compile 실패 | alias versionless, POM/metadata를 먼저 재확인 |

## 6. 테스트 설계

기존 `AbstractR2dbcExposedTest`, `withDb`, `withTables` 수명주기와 H2 fast
경로를 유지한다. 테스트는 local key를 직접 검사하지 않고 공개 adapter를
검사한다.

### WebFlux

`TenantFilterTest`:

- valid header가 downstream `ContextView`에서 `TenantId.value`로 관찰되는지
  확인한다.
- invalid/unknown header가 bind 전에 거절되는지 확인한다.

`TenantRoutingConnectionFactoryTest`:

- 서로 다른 tenant subscription을 번갈아 interleave해도 각 branch가 자기
  pool을 선택하는지 확인한다.
- nested success/failure, cancellation, 외부 context pollution fixture를
  provider adapter API로 이식한다.
- context가 없으면 기존 empty 경계가 유지되는지 확인한다.

`TenantTransactionExecutorTest`는 coroutine/Reactor bridge가 adapter가
설정한 context를 잃지 않는지만 확인하며, assertion은 저장소 표준
`io.bluetape4k.assertions.assertFailsWith`를 사용하도록 정렬한다.

### Ktor

`KtorMultitenantApplicationTest`:

- dispatcher hop에서 `requireCurrent(call)`이 같은 tenant를 읽는지 확인한다.
- nested success가 outer binding을 재관찰하는지 확인한다.
- nested duplicate bind가 `TenantAlreadyBoundException`을 던지고 outer
  binding을 유지하는지 확인한다.
- 서로 겹치는 요청/새 call에서 attribute pollution이 없는지 확인한다.
- 기존 actor read/write/schema isolation 및 header 계약 테스트를 유지한다.

최소 검증 명령은 다음과 같다.

```bash
./gradlew :10-multi-tenant:04-connection-factory-per-tenant-spring-webflux:test \
  :10-multi-tenant:07-multitenant-ktor:test -PuseDB=H2 --no-daemon --console=plain
./gradlew test --no-daemon --console=plain
git diff --check
```

실행 환경이 Docker-backed database를 요구할 경우 repository의 Colima/
Testcontainers 규칙을 따르고, 실패/skip을 성공으로 해석하지 않는다.

## 7. 범위, 호환성, DoD

### 포함

- 두 target module의 public provider adapter dependency와 local catalog alias
- WebFlux producer/consumer carrier migration
- Ktor producer/consumer carrier migration 및 one-call fixture 재정의
- deterministic test migration과 assertion 표준화
- 두 locale README의 동등한 설명

### 제외

- provider API 변경, 특히 Ktor scoped override API
- 새 common-core 또는 multi-tenant module
- header policy, tenant registry, database/schema routing 정책 변경
- 관련 없는 module/version cleanup
- 다이어그램 및 이미지 재생성

### 완료 조건

1. 두 module이 public tenant artifact를 versionless alias로 resolve한다.
2. production source에 local Reactor key 또는 Ktor `TenantAttributeKey`가 남지
   않는다.
3. WebFlux interleave/nested/cancellation/no-pollution fixture가 통과한다.
4. Ktor dispatcher/nested duplicate/no-pollution fixture가 통과한다.
5. 기존 HTTP validation, routing, schema isolation 동작이 유지된다.
6. `README.md`와 `README.ko.md`가 같은 carrier/제한/검증 사실을 설명한다.
7. module test, repository test, static/diff check가 fresh evidence로 통과한다.
8. PR body에 `## DoD Status`, issue/PR metadata parity, dependency evidence가
   반영되고 stacked train의 각 head가 정확히 기록된다.

구현 전 self-review에서 다음을 다시 확인한다.

- 이 명세가 Ktor adapter에 존재하지 않는 scoped API를 전제로 하지 않는가?
- local validation과 provider carrier 책임이 분리되어 있는가?
- default fallback이 request context 오염으로 재해석되지 않는가?
- 테스트가 private key가 아닌 public adapter 계약을 검증하는가?
- acceptance criteria와 변경 파일/명령의 대응이 추적 가능한가?

이 명세의 승인 후에만 구현 계획을 작성하고, TDD 및 Kotlin pattern gate를
재확인한 뒤 stacked implementation을 시작한다.

## 8. 명세 writer 게이트 기록

| 게이트 | 상태 | 근거 |
| --- | --- | --- |
| SPW-01 audience/purpose/evidence | 완료 | 대상 독자는 workshop consumer 구현자이며, §1–§3에 이슈 URL, 현재 source path, upstream API URL/commit, dependency run/POM 근거를 기록했다. |
| SPW-02 artifact contract | 완료 | §2 문제, §4 설계/경계, §5 실패 모드, §6 테스트, §7 범위·호환성·DoD를 포함한다. |
| SPW-03 Korean technical register | 완료 | code/API/command/URL/정확한 예외명은 보존하고, 일반 문장은 한국어 기술 문체로 검토했다. |
| SPW-04 meaning/traceability | 완료 | 현재 WebFlux/Ktor source path와 upstream 공개 계약을 설계 항목 및 acceptance criteria에 연결했다. |
| SPW-05 final read-back | 완료 | 최종 Markdown을 다시 읽어 headings, tables, lists, code fences, 링크와 구현 전 보류 상태를 확인했다. |

자연스러움 checklist도 다음과 같이 닫았다.

- KO-01: 외부 commit, run id, POM 파일명, API signature, 예외명과 미확정
  구현 상태를 보존했다.
- KO-02: 일반적인 효율성/중요성 주장을 넣지 않고 carrier 상태 전이와 테스트
  관찰 결과로만 설명했다.
- KO-03: 번역투 연결어와 모호한 명사화를 줄이고 주체와 동작을 직접 썼다.
- KO-04: `bind`, `currentOrNull`, `requireCurrent`, `TenantId` 등 식별자와
  tenant/carrier/context 용어를 문서 전체에서 일관되게 사용했다.
- KO-05: 비유나 홍보 문구 없이 결정과 제약을 기술했다.
- KO-06: 한국어 본문, 표, 코드 fence, 링크를 다시 확인했다. 영어 README는
  구현 단계에서 한국어 사실 집합과 동기화한다.
- KO-07: `audit-korean-terms.mjs` 실행 결과 `findings=0`이다.

남은 게이트는 이 명세에 대한 사용자 검토와 승인, 그 후의 구현 계획 작성 및
코드/테스트 검증이다. 구현과 Gradle 검증을 완료했다고 주장하지 않는다.
