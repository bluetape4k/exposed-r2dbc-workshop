# Issue #215 tenant context consumer 전환 Implementation Plan

> **For agentic workers:** 승인된 설계 명세와 이 계획을 먼저 읽고, 각 작업을 체크박스와 fresh evidence로 갱신한다. 구현 전에 test-driven-development와 bluetape-kotlin-patterns 게이트를 다시 확인한다.

**Goal:** WebFlux와 Ktor 멀티 테넌트 예제가 중앙 tenant carrier adapter를 직접 소비하도록 전환하고, 기존 요청 검증·routing·schema isolation 및 결정론적 context fixture를 보존한다.

**Architecture:** TenantIdResolver와 Tenants는 각 예제의 HTTP 입력 검증과 registry 책임을 계속 소유한다. WebFlux는 ReactorTenantContext의 immutable subscription Context를 생산·소비하고, Ktor는 KtorTenantContext의 one-call ApplicationCall binding을 생산·소비한다. 두 변경은 한 이슈 안에서 dependency/catalog → WebFlux → Ktor 순서의 stacked train으로 통합한다.

**Tech Stack:** Kotlin 2.4.0/JVM 25, Spring Boot 4.1.0, Ktor 3.5.0, Exposed R2DBC 1.4.0, bluetape4k-dependencies:2.0.0-SNAPSHOT, bluetape4k-tenant, bluetape4k-tenant-reactor, bluetape4k-ktor-tenant, Reactor, Kotlin Coroutines, H2 R2DBC, JUnit 5, Gradle Version Catalog, Detekt.

---

## 1. 실행 경계와 stacked train

이 저장소의 실제 gradle/libs.versions.toml과 root build가 현재 Kotlin/JVM/
Spring/Exposed 버전을 결정한다. repository overlay에 적힌 과거 버전은 새
버전 문자열을 추가하는 근거로 사용하지 않는다. 공개 provider 계약은 명세에
고정한 bluetape4k-projects commit
08d451e6f48ad326c13ee1d01e1ddd8ba855f3fa의 실제 package/source tree를 기준으로
한다.

stack graph는 다음과 같다.

~~~text
origin/develop
  └─ feat/issue-215-webflux-tenant-context   (Stack A / PR A, base develop)
       └─ feat/issue-215-ktor-tenant-context (Stack B / PR B, base Stack A)
~~~

- 현재 worktree /Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-215-webflux-tenant-context가 Stack A이다. 이미 승인된 spec과 이 plan을 이 branch에 남긴다.
- Stack A는 catalog alias 3개, WebFlux adapter 전환, WebFlux 테스트/README를 포함한다.
- Stack B는 Stack A head에서 만들어 Ktor dependency, production carrier, 테스트/README만 추가한다.
- PR은 A → B 순서로 생성한다. merge는 별도 exact-head 승인 뒤에만 수행하며 auto-merge를 켜지 않는다.
- 빈 integration PR은 만들지 않는다. Stack B에서 두 모듈·전체 repository 검증을 수행하고, PR body에 A/B head와 의존 관계를 기록한다.

## 2. 파일 구조와 책임

### Stack A에서 변경

- Modify: gradle/libs.versions.toml — 중앙 BOM이 버전을 결정하는 세 versionless alias.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/build.gradle.kts — 공통 tenant와 Reactor adapter 의존성.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantFilter.kt — 헤더 검증 결과를 provider Context에 binding.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantRoutingConnectionFactory.kt — provider ContextView에서 tenant id 조회.
- Delete: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantContextKeys.kt — local Reactor key 제거.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantFilterTest.kt — public adapter 관찰.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantRoutingConnectionFactoryTest.kt — 모든 context 생성/조회와 nested fixture를 public adapter로 교체.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/connectionfactory/tenant/TenantTransactionExecutorTest.kt — repository assertion helper 사용.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/README.md — English carrier 경계와 fixture 설명.
- Modify: 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/README.ko.md — 동일 사실 집합의 한국어 설명.

### Stack B에서 변경

- Modify: 10-multi-tenant/07-multitenant-ktor/build.gradle.kts — 공통 tenant와 Ktor adapter 의존성.
- Modify: 10-multi-tenant/07-multitenant-ktor/src/main/kotlin/exposed/r2dbc/multitenant/ktor/tenant/TenantPlugin.kt — 검증한 id를 KtorTenantContext에 최초 binding하고 현재 tenant를 provider API로 조회.
- Modify: 10-multi-tenant/07-multitenant-ktor/src/main/kotlin/exposed/r2dbc/multitenant/ktor/tenant/TenantId.kt — local TenantAttributeKey 삭제, header/예외만 유지.
- Modify: 10-multi-tenant/07-multitenant-ktor/src/test/kotlin/exposed/r2dbc/multitenant/ktor/KtorMultitenantApplicationTest.kt — dispatcher/nested/no-pollution fixture를 public Ktor adapter 계약으로 교체.
- Modify: 10-multi-tenant/07-multitenant-ktor/README.md — call carrier 및 one-call binding 제한 설명.
- Modify: 10-multi-tenant/07-multitenant-ktor/README.ko.md — 동일 사실 집합의 한국어 설명.

변경하지 않음:

- settings.gradle.kts: leaf auto-discovery가 이미 대상 모듈을 등록하므로 등록 변경 없음.
- TenantIdResolver.kt, Tenants.kt, TenantTransactionExecutor.kt, Ktor TenantTransaction.kt: carrier owner 외의 검증/transaction 책임을 유지한다. 단, route의 기존 currentTenant() 호출은 adapter-backed implementation으로 보존한다.
- docs/images/**, .github/workflows/**: 새 module/diagram/CI shard가 없으므로 변경하지 않는다.

## 3. 위험과 rollback

| 위험 | 조기 신호 | 완화/검증 | rollback 지점 |
|---|---|---|---|
| 공개 artifact/package drift | import 또는 dependency resolution 실패 | provider source와 timestamped POM을 compile 전에 재확인하고 versionless alias만 사용 | dependency/build commit만 revert |
| WebFlux context identity 불일치 | valid header가 routing factory에서 missing으로 관찰됨 | filter·routing·모든 fixture가 동일한 ReactorTenantContext API를 사용하도록 test RED→GREEN | Stack A production commit revert |
| Reactor nested context 오염 | interleave branch가 다른 tenant를 관찰 | withTenant(Context, TenantId)가 derived context를 만들고 outer context는 변경하지 않는 fixture 유지 | nested test/adapter call만 revert |
| Ktor scoped override 오해 | duplicate bind가 overwrite 또는 성공으로 관찰됨 | TenantAlreadyBoundException을 명시적으로 검증하고 one-call 문서화 | Stack B Ktor commit revert |
| local enum와 provider TenantId 매핑 누락 | route에서 schema 선택 실패 | currentTenant()가 requireCurrent(call).value를 local registry로 변환하는 단일 경계 유지 | TenantPlugin.kt만 revert |
| locale README drift | English/Korean 표·제약 불일치 | 같은 carrier 사실표를 두 README에 반영하고 terminology audit 실행 | docs commit revert |
| fixture resource 누수 | scheduler/executor가 테스트 뒤 남음 | 기존 finally의 dispose()/shutdownNow()/close()를 보존하고 전체 test 실행 | 해당 테스트 commit revert |

## 4. Task 0 — 실행 전 receipt·provider·stack 재확인

**Files:** 읽기 전용: .bluetape/runs/20260830T080759Z-ceba8623/receipt.jsonl,
gradle/libs.versions.toml, 승인된 spec.

- [ ] 현재 worktree와 branch가 Stack A이며 spec commit 660f6516 이후 clean인지 확인한다.

~~~bash
WT=/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-215-webflux-tenant-context
git -C "$WT" status --short --branch
git -C "$WT" log --oneline --decorate -4
git -C "$WT" diff --check
~~~

기대 결과: branch가 feat/issue-215-webflux-tenant-context, uncommitted output이
없고 git diff --check가 조용히 끝난다.

- [ ] 실행 receipt가 현재 session을 허용하는지 확인하고, 이후 각 mutation 전에 동일 target을 mutation-check한다.

~~~bash
FLOW=/Users/debop/.codex/skills/bluetape-workflow/scripts/bluetape-flow.py
python3 "$FLOW" --state-root "$WT/.bluetape" mutation-check \
  --session-id "$CODEX_THREAD_ID" --allow-state running \
  --target "$WT/gradle/libs.versions.toml"
~~~

기대 결과: JSON ok=true, run_state=running.

- [ ] provider source와 dependency handoff를 재확인한다.

~~~bash
gh api repos/bluetape4k/bluetape4k-projects/commits/08d451e6 \
  --jq '{sha:.sha, tree:.commit.tree.sha}'
gh api 'repos/bluetape4k/bluetape4k-projects/contents/ktor/tenant/src/main/kotlin/io/bluetape4k/ktor/tenant/KtorTenantContext.kt?ref=08d451e6' \
  -H 'Accept: application/vnd.github.raw'
gh pr view 215 --repo bluetape4k/bluetape4k-dependencies --json state,mergeCommit,url
~~~

기대 결과: KtorTenantContext package가 io.bluetape4k.ktor.tenant이고,
의존성 PR이 MERGED이며, public POM/metadata handoff 근거가 spec과 일치한다.

## 5. Task 1 — catalog alias와 Stack A 의존성 고정

**Files:** gradle/libs.versions.toml,
10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/build.gradle.kts.

- [ ] gradle/libs.versions.toml의 Bluetape4k 섹션에 다음 세 alias를 versionless로 추가한다. 기존 bluetape4k-dependencies version과 BOM import는 바꾸지 않는다.

~~~toml
bluetape4k-tenant = { module = "io.github.bluetape4k:bluetape4k-tenant" }
bluetape4k-tenant-reactor = { module = "io.github.bluetape4k:bluetape4k-tenant-reactor" }
bluetape4k-ktor-tenant = { module = "io.github.bluetape4k:bluetape4k-ktor-tenant" }
~~~

- [ ] WebFlux build file의 // bluetape4k dependency block에 공통 contract와 Reactor adapter를 추가한다.

~~~kotlin
implementation(libs.bluetape4k.tenant)
implementation(libs.bluetape4k.tenant.reactor)
~~~

- [ ] version이 local alias 또는 module dependency에 직접 생기지 않았는지 확인한다.

~~~bash
rg -n 'bluetape4k-(tenant|ktor-tenant)|io\\.github\\.bluetape4k:(bluetape4k-tenant|bluetape4k-ktor-tenant).*:' \
  gradle/libs.versions.toml 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/build.gradle.kts
./gradlew :04-connection-factory-per-tenant-spring-webflux:dependencies \
  --configuration compileClasspath --no-daemon --console=plain
~~~

기대 결과: 세 alias가 catalog에 한 번씩 있고, provider 버전은 BOM이 resolve하며,
명시적인 artifact version 문자열이 없다.

- [ ] Task 1 변경을 Lore commit으로 저장한다.

~~~bash
git add gradle/libs.versions.toml \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/build.gradle.kts
git commit -F - <<'EOF'
Issue #215의 WebFlux consumer가 중앙 tenant artifact를 사용하도록 준비한다

versionless catalog alias와 WebFlux의 직접 contract/adapter dependency를
중앙 BOM 경계 안에 고정한다.

Constraint: bluetape4k-dependencies BOM이 모든 provider 버전을 결정한다.
Rejected: module별 provider version pin | 중앙 dependency authority를 우회한다.
Confidence: high
Scope-risk: narrow
Directive: 새 tenant artifact도 versionless alias를 통해서만 소비한다.
Tested: Gradle compileClasspath dependency report; catalog version scan
Not-tested: carrier behavior tests는 다음 RED 단계에서 실행한다.
EOF
~~~

## 6. Task 2 — WebFlux public adapter RED fixture 작성

**Files:** TenantFilterTest.kt, TenantRoutingConnectionFactoryTest.kt.

- [ ] TenantFilterTest.kt의 import와 assertion을 다음처럼 바꾸어 local key 의존성을 제거한다.

~~~kotlin
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.tenant.reactor.ReactorTenantContext
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicReference
~~~

~~~kotlin
val seenTenantId = AtomicReference<String?>()
val chain = WebFilterChain {
    Mono.deferContextual { context ->
        seenTenantId.set(ReactorTenantContext.currentOrNull(context)?.value)
        Mono.empty()
    }
}
~~~

기존 마지막 assertion은 seenTenantId.get() shouldBeEqualTo
Tenants.Tenant.KOREAN.id 그대로 유지한다. 이 테스트는 provider ContextView에서
값을 읽는지만 바꾼다.

- [ ] TenantRoutingConnectionFactoryTest.kt에 provider imports를 추가하고 local key extension을 제거한다.

~~~kotlin
import io.bluetape4k.tenant.TenantId
import io.bluetape4k.tenant.reactor.ReactorTenantContext
import reactor.util.context.ContextView
~~~

모든 contextWrite는 다음 두 가지 형태 중 해당하는 형태로 바꾼다.

~~~kotlin
// known 또는 fixture tenant
.contextWrite { context ->
    ReactorTenantContext.withTenant(context, TenantId(Tenants.Tenant.ENGLISH.id))
}

// unknown lookup key 경계
.contextWrite { context ->
    ReactorTenantContext.withTenant(context, TenantId("unknown"))
}
~~~

outer fixture binding은 tenant.id를 사용한다.

~~~kotlin
}.contextWrite { context ->
    ReactorTenantContext.withTenant(context, TenantId(tenant.id))
}
~~~

nested Reactor scope는 immutable derived context로 다른 tenant를 관찰하는
기존 의미를 유지한다.

~~~kotlin
.contextWrite { context ->
    ReactorTenantContext.withTenant(context, TenantId(otherTenant(expectedTenant)))
}
~~~

private extension은 다음으로 대체한다.

~~~kotlin
private fun ContextView.tenantOrNull(): String? =
    ReactorTenantContext.currentOrNull(this)?.value
~~~

- [ ] public adapter를 참조하도록 바꾼 테스트를 production migration 전 실행해 RED를 확인한다.

~~~bash
./gradlew :04-connection-factory-per-tenant-spring-webflux:test \
  -PuseDB=H2 --no-daemon --console=plain
~~~

기대 결과: 컴파일은 되고, 현재 production이 local key를 계속 쓰므로 valid
filter/routing 또는 provider context fixture에서 최소 하나가 실패한다. 실패가
Unresolved reference이면 dependency/catalog를 먼저 고치고, local key mismatch가
아니면 RED 원인을 수정한 뒤 다음 단계로 간다.

## 7. Task 3 — WebFlux production carrier GREEN 구현

**Files:** TenantFilter.kt, TenantRoutingConnectionFactory.kt, 삭제할 TenantContextKeys.kt.

- [ ] TenantFilter.kt의 import와 filter를 다음 구현으로 교체한다. resolver가 반환한 normalized string은 local validation 뒤에만 TenantId로 감싼다.

~~~kotlin
import io.bluetape4k.tenant.TenantId
import io.bluetape4k.tenant.reactor.ReactorTenantContext
~~~

~~~kotlin
override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val tenantId = TenantId(resolveTenantId(exchange))
    log.debug { "Resolved tenantId: " + tenantId.value }
    return chain
        .filter(exchange)
        .contextWrite { context ->
            ReactorTenantContext.withTenant(context, tenantId)
        }
}
~~~

resolveTenantId는 다음 기존 구현을 그대로 유지한다.

~~~kotlin
private fun resolveTenantId(exchange: ServerWebExchange): String =
    TenantIdResolver.resolve(exchange.request.headers.getFirst(TENANT_HEADER))
~~~

- [ ] TenantRoutingConnectionFactory.kt를 다음으로 교체한다. context가 없으면 명시적으로 Mono.empty()를 반환하고, unknown id failure는 Spring의 setLenientFallback(false)에 맡긴다.

~~~kotlin
import io.bluetape4k.tenant.reactor.ReactorTenantContext
import org.springframework.r2dbc.connection.lookup.AbstractRoutingConnectionFactory
import reactor.core.publisher.Mono

class TenantRoutingConnectionFactory: AbstractRoutingConnectionFactory() {

    override fun determineCurrentLookupKey(): Mono<Any> =
        Mono.deferContextual { contextView ->
            ReactorTenantContext.currentOrNull(contextView)
                ?.value
                ?.let { Mono.just(it) }
                ?: Mono.empty()
        }
}
~~~

- [ ] TenantContextKeys.kt를 삭제하고 production/test source 전체에서 해당 symbol이 사라졌는지 확인한다.

~~~bash
rg -n 'TenantContextKeys|contextView\\.get<String>|put\\(TenantContextKeys' \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src || true
~~~

기대 결과: 검색 결과가 없다.

- [ ] WebFlux 테스트를 GREEN으로 실행하고 provider artifact resolution을 기록한다.

~~~bash
repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test \
  -PuseDB=H2 --continue --no-daemon --console=plain
./gradlew :04-connection-factory-per-tenant-spring-webflux:dependencyInsight \
  --configuration compileClasspath --dependency bluetape4k-tenant-reactor \
  --no-daemon --console=plain
~~~

기대 결과: WebFlux module test 전체 통과, dependencyInsight에
io.github.bluetape4k:bluetape4k-tenant-reactor가 2.0.0-SNAPSHOT BOM
경로로 나타난다.

## 8. Task 4 — WebFlux coroutine assertion과 README pair 정렬

**Files:** TenantTransactionExecutorTest.kt, WebFlux README.md/README.ko.md.

- [ ] TenantTransactionExecutorTest.kt의 assertion import만 repository 표준으로 바꾼다.

~~~kotlin
import io.bluetape4k.assertions.assertFailsWith
~~~

cancellation exception propagates from transaction block 테스트 본문과
CancellationException 검증은 변경하지 않는다. TenantTransactionExecutor 자체의
ReactorContext bridge와 Job 제거 순서는 이 작업에서 수정하지 않는다.

- [ ] English README의 carrier 설명을 다음 사실로 교체한다.

~~~markdown
## Tenant Carrier

TenantIdResolver still owns header trimming, syntax validation, and the fixed
korean/english registry. After validation, TenantFilter creates the provider
TenantId and binds it with ReactorTenantContext.withTenant in the immutable
subscription Context; TenantRoutingConnectionFactory reads the same provider key
with currentOrNull(contextView).

The default korean tenant is used only outside the mandatory HTTP-header path. It
is application configuration, not a default inside the shared carrier. The
deterministic tests also cover interleaved subscriptions, nested derived
contexts, cancellation, missing context, and no context leakage.
~~~

기존 HTTP 표의 결과, pool isolation, authorization 경고, run 명령은 유지한다.

- [ ] Korean README의 해당 설명을 다음 내용으로 교체한다.

~~~markdown
## Tenant carrier

TenantIdResolver가 header trim, 형식 검증, 고정된 korean/english registry를
계속 담당합니다. 검증이 끝나면 TenantFilter가 provider TenantId를 만들고
ReactorTenantContext.withTenant로 immutable subscription Context에 binding합니다.
TenantRoutingConnectionFactory는 같은 provider key를 currentOrNull(contextView)로
읽습니다.

korean default는 필수 HTTP header 경로 밖에서만 사용하는 application 설정입니다.
공유 carrier 안에 기본 tenant를 넣지 않습니다. 결정론적 테스트는 interleaved
subscription, nested derived context, cancellation, context 없음, 외부 context
오염 방지를 함께 검증합니다.
~~~

- [ ] 두 README가 carrier owner, default 경계, 테스트 범위를 같은 사실로 설명하는지 확인한다.

~~~bash
node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/README.ko.md
git diff --check
~~~

- [ ] Stack A 변경을 Lore commit으로 저장한다.

~~~bash
git add 10-multi-tenant/04-connection-factory-per-tenant-spring-webflux \
  gradle/libs.versions.toml
git commit -F - <<'EOF'
Issue #215의 WebFlux carrier를 공개 Reactor adapter로 전환한다

HTTP validation과 routing 정책은 유지하면서 subscription tenant carrier의
생산·소비를 provider API 하나로 정렬한다.

Constraint: Reactor Context는 immutable derived value이며 HTTP 밖 default는 local 설정이다.
Rejected: local string key 유지 | provider와 동일한 carrier identity를 보장하지 못한다.
Confidence: high
Scope-risk: moderate
Directive: routing factory와 coroutine bridge는 provider context 외의 fallback을 만들지 않는다.
Tested: WebFlux H2 module tests; dependencyInsight; Korean README audit; git diff --check
Not-tested: Stack B Ktor consumer와 repository-wide CI는 child stack에서 수행한다.
EOF
~~~

## 9. Task 5 — Stack B Ktor worktree 생성

**Files:** Git worktree metadata only; production files는 다음 task에서 변경한다.

- [ ] Stack A가 clean이고 head를 기록한 뒤 child worktree를 만든다.

~~~bash
WT_A=/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-215-webflux-tenant-context
WT_B=/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-215-ktor-tenant-context
git -C "$WT_A" status --short --branch
git -C "$WT_A" rev-parse HEAD
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop worktree add \
  "$WT_B" -b feat/issue-215-ktor-tenant-context \
  feat/issue-215-webflux-tenant-context
~~~

기대 결과: Stack B branch가 Stack A head에서 시작하고, 두 worktree가 서로의
uncommitted 변경을 덮어쓰지 않는다. 기존 #198/#204/#205 worktree는 보존한다.

- [ ] worktree별 receipt 격리를 지키기 위해 Stack B 전용 Phase 2 receipt를
  새로 초기화한다. coordinator manifest가 하나의 `repo_root`만 소유하므로
  Stack A receipt를 Stack B에 복사하거나 같은 run으로 가장하지 않는다.

~~~bash
FLOW=/Users/debop/.codex/skills/bluetape-workflow/scripts/bluetape-flow.py
OWNER_B="$WT_B/.bluetape/handles/issue-215-ktor.owner"
INIT_B=$(python3 "$FLOW" --state-root "$WT_B/.bluetape" init \
  --workflow-type A --repo-root "$WT_B" --owner-file "$OWNER_B" \
  --component issue-215-ktor --session-id "$CODEX_THREAD_ID")
RUN_B=$(printf '%s' "$INIT_B" | jq -r '.run_id')
python3 "$FLOW" --state-root "$WT_B/.bluetape" run-approve \
  --run-id "$RUN_B" --owner-file "$OWNER_B" \
  --evidence-summary "Issue #215 Stack B 계획과 승인된 spec을 확인했다" \
  --evidence-kind approval
python3 "$FLOW" --state-root "$WT_B/.bluetape" run-start \
  --run-id "$RUN_B" --owner-file "$OWNER_B" \
  --evidence-summary "Stack B Ktor worktree의 독립 receipt를 시작한다" \
  --evidence-kind execution
~~~

- [ ] Stack B production/test 파일 mutation 전 child receipt에 대해 mutation-check를
  실행한다.

~~~bash
python3 "$FLOW" --state-root "$WT_B/.bluetape" mutation-check \
  --session-id "$CODEX_THREAD_ID" --allow-state running \
  --target "$WT_B/10-multi-tenant/07-multitenant-ktor/src/main/kotlin/exposed/r2dbc/multitenant/ktor/tenant/TenantPlugin.kt"
~~~

기대 결과: 현재 session과 Stack B `repo_root`에 bound된 ok=true receipt. 이
receipt의 run id/owner는 Stack A와 별개이며, 두 run의 evidence를 최종 handoff에
함께 기록한다.

## 10. Task 6 — Ktor public adapter RED fixture 작성

**Files:** KtorMultitenantApplicationTest.kt, 07-multitenant-ktor/build.gradle.kts.

- [ ] Ktor build file dependency block에 다음 두 direct dependency를 추가한다. catalog alias는 Stack A에서 이미 추가했다.

~~~kotlin
implementation(libs.bluetape4k.tenant)
implementation(libs.bluetape4k.ktor.tenant)
~~~

- [ ] 테스트 import에서 local key를 제거하고 provider API를 추가한다.

~~~kotlin
import io.bluetape4k.ktor.tenant.KtorTenantContext
import io.bluetape4k.ktor.tenant.TenantAlreadyBoundException
import io.bluetape4k.tenant.TenantId
~~~

다음 import는 삭제한다.

~~~kotlin
import exposed.r2dbc.multitenant.ktor.tenant.TenantAttributeKey
~~~

- [ ] deterministic route fixture의 nested block을 다음 의미로 교체한다. 성공 branch는 기존 binding을 재관찰하고, failure branch는 다른 tenant의 duplicate bind를 시도해 TenantAlreadyBoundException을 기록한다.

~~~kotlin
val nestedTenant = expectedTenant.other()
if (traceId.endsWith("-failure")) {
    try {
        KtorTenantContext.bindTenant(call, TenantId(nestedTenant.id))
        error("duplicate tenant binding unexpectedly succeeded")
    } catch (_: TenantAlreadyBoundException) {
        recorder.record(
            expectedTenant = expectedTenant.id,
            observedTenant = call.currentTenant().id,
            subscriptionId = "ktor-" + traceId,
            dispatcher = TRACE_DISPATCHER,
            phase = TracePhase.NESTED_FAILURE,
        )
    }
} else {
    recorder.record(
        expectedTenant = expectedTenant.id,
        observedTenant = call.currentTenant().id,
        subscriptionId = "ktor-" + traceId,
        dispatcher = TRACE_DISPATCHER,
        phase = TracePhase.NESTED_SUCCESS,
    )
}
~~~

NESTED_ENTER 기록도 call.currentTenant().id를 관찰하고,
NESTED_ENTER/NESTED_SUCCESS/NESTED_FAILURE 모두 expectedTenant.id와
같아야 한다. 기존의 expectedTenant.other().id assertion을 제거한다. outer
RESUME와 COMPLETE 기록, dispatcher hop, finally { traceDispatcher.close() }는
유지한다.

- [ ] Ktor production migration 전 테스트를 실행해 duplicate binding 시도만 RED인지 확인한다.

~~~bash
repo-test-summary -- ./gradlew :07-multitenant-ktor:test \
  -PuseDB=H2 --continue --no-daemon --console=plain
~~~

기대 결과: compile은 provider dependency를 resolve하고, current production이
provider binding을 하지 않기 때문에 failure trace가 실패한다. unresolved import
또는 기존 database test failure이면 dependency/base를 먼저 고친다.

## 11. Task 7 — Ktor production carrier GREEN 구현

**Files:** TenantPlugin.kt, TenantId.kt; route call site는 adapter-backed extension을 통해 유지한다.

- [ ] TenantPlugin.kt import를 추가하고 plugin binding과 currentTenant()를 다음으로 교체한다.

~~~kotlin
import io.bluetape4k.ktor.tenant.KtorTenantContext
import io.bluetape4k.tenant.TenantId
~~~

~~~kotlin
val TenantPlugin = createApplicationPlugin(name = "TenantPlugin") {
    onCall { call ->
        val rawValues = call.request.headers.getAll(TenantHeader).orEmpty()
        val tenantId = normalizeTenantHeader(rawValues)
        val tenant = Tenants.findById(tenantId)
            ?: throw InvalidTenantException("Unknown tenant id: " + tenantId)
        KtorTenantContext.bindTenant(call, TenantId(tenant.id))
    }
}
~~~

~~~kotlin
fun ApplicationCall.currentTenant(): Tenants.Tenant =
    KtorTenantContext.requireCurrent(this).value.let { tenantId ->
        Tenants.findById(tenantId)
            ?: error("TenantPlugin resolved an unregistered tenant: " + tenantId)
    }
~~~

normalizeTenantHeader의 missing/blank/conflicting header 처리와
InvalidTenantException 메시지는 그대로 둔다. plugin은 call당 한 번만 실행되므로
정상 HTTP 경로에서 duplicate bind가 발생하지 않는다.

- [ ] TenantId.kt에서 다음 local key 선언과 io.ktor.util.AttributeKey import를 삭제하고 header constant/예외만 남긴다.

~~~kotlin
internal const val TenantHeader = "X-TENANT-ID"

/** request tenant header가 지원 tenant로 해석되지 않을 때 발생한다. */
class InvalidTenantException(message: String): IllegalArgumentException(message)
~~~

- [ ] route가 local key를 직접 읽지 않는지 확인한다. 기존 call.currentTenant() API는 adapter-backed mapping이므로 ActorRoutes.kt의 transaction/schema 흐름은 변경하지 않는다.

~~~bash
rg -n 'TenantAttributeKey|attributes\\.(get|put|getOrNull)|AttributeKey<' \
  10-multi-tenant/07-multitenant-ktor/src/main \
  || true
~~~

기대 결과: local tenant AttributeKey 관련 결과가 없다.

- [ ] Ktor 테스트를 GREEN으로 실행하고 provider artifact를 확인한다.

~~~bash
repo-test-summary -- ./gradlew :07-multitenant-ktor:test \
  -PuseDB=H2 --continue --no-daemon --console=plain
./gradlew :07-multitenant-ktor:dependencyInsight \
  --configuration compileClasspath --dependency bluetape4k-ktor-tenant \
  --no-daemon --console=plain
~~~

기대 결과: actor/schema/header 테스트와 dispatcher/nested duplicate/no-pollution
fixture가 모두 통과하고 dependencyInsight가 중앙 BOM 경로를 보여준다.

## 12. Task 8 — Ktor README pair와 Stack B commit

**Files:** Ktor README.md, README.ko.md.

- [ ] English README의 request context 표와 Notes를 다음 사실로 갱신한다.

~~~markdown
| Request context | KtorTenantContext binds one validated TenantId to one ApplicationCall; no ThreadLocal, no ReactorContext |
~~~

~~~markdown
TenantPlugin owns header validation and the local korean/english registry. After
validation it calls KtorTenantContext.bindTenant(call, TenantId(tenant.id)). Routes read
KtorTenantContext.requireCurrent(call) through the local currentTenant() mapping
before selecting the tenant schema.

The adapter is deliberately one-call/one-tenant. A second bind does not overwrite
the first value; it raises TenantAlreadyBoundException. The deterministic fixture
therefore treats nested success as re-observing the outer tenant, and nested failure
as a rejected duplicate bind. Dispatcher hops, overlapping calls, and the existing
HTTP/header tests remain covered.
~~~

기존 authorization 경고, endpoint 표, schema-per-tenant 설명은 유지한다.

- [ ] Korean README에 같은 결정을 자연스러운 한국어로 반영한다.

~~~markdown
| Request context | KtorTenantContext가 검증된 TenantId를 하나의 ApplicationCall에 binding; ThreadLocal/ReactorContext 미사용 |
~~~

~~~markdown
TenantPlugin은 header 검증과 local korean/english registry를 담당합니다.
검증 후 KtorTenantContext.bindTenant(call, TenantId(tenant.id))를 호출하고, route는
local currentTenant() mapping을 통해 KtorTenantContext.requireCurrent(call)을
읽은 뒤 tenant schema를 선택합니다.

이 adapter는 의도적으로 one-call/one-tenant 계약을 사용합니다. 두 번째 binding은
첫 값을 덮어쓰지 않고 TenantAlreadyBoundException을 발생시킵니다. 따라서
결정론적 fixture에서 nested success는 바깥 tenant를 다시 관찰하는 경우이고,
nested failure는 duplicate binding 거절입니다. dispatcher hop, 겹치는 call,
기존 HTTP/header 테스트도 계속 검증합니다.
~~~

- [ ] locale pair와 용어를 검사한다.

~~~bash
node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs \
  10-multi-tenant/07-multitenant-ktor/README.ko.md
git diff --check
~~~

- [ ] Stack B 구현과 문서를 Lore commit으로 저장한다.

~~~bash
git add 10-multi-tenant/07-multitenant-ktor
git commit -F - <<'EOF'
Issue #215의 Ktor consumer를 one-call tenant adapter로 정렬한다

Ktor call attribute의 local key를 제거하고 공개 adapter의 최초 binding,
requireCurrent 조회, duplicate binding 실패 계약을 예제와 fixture에 반영한다.

Constraint: KtorTenantContext는 하나의 ApplicationCall에 한 tenant만 binding한다.
Rejected: local attribute overwrite/restore helper | provider의 one-call 계약을 숨긴다.
Confidence: high
Scope-risk: moderate
Directive: scoped override가 필요하면 별도 upstream 이슈로 다루고 consumer에서 흉내 내지 않는다.
Tested: Ktor H2 module tests; dependencyInsight; Korean README audit; git diff --check
Not-tested: repository-wide CI와 PR head checks는 verification 단계에서 수행한다.
EOF
~~~

## 13. Task 9 — cross-stack static·test·문서 검증

**Files:** Stack B 전체 변경 범위; 추가 수정은 실패 증거가 있을 때만 한다.

- [ ] 두 target module의 H2 테스트와 compile을 순차 실행한다.

~~~bash
repo-test-summary -- ./gradlew \
  :04-connection-factory-per-tenant-spring-webflux:test \
  :07-multitenant-ktor:test -PuseDB=H2 --continue --no-daemon --console=plain
./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin \
  :07-multitenant-ktor:compileKotlin --warning-mode all --no-daemon --console=plain
~~~

기대 결과: 두 module test와 compile이 성공하고, Ktor nested fixture의 nested
success/failure 관찰 값이 모두 outer tenant와 같다.

- [ ] local carrier leakage와 unsupported API 사용을 검사한다.

~~~bash
rg -n 'TenantContextKeys|TenantAttributeKey|AttributeKey<|contextView\\.get<String>|attributes\\.put\\(' \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/src \
  10-multi-tenant/07-multitenant-ktor/src || true
rg -n 'io\\.bluetape4k\\.(tenant\\.reactor|ktor\\.tenant)' \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux \
  10-multi-tenant/07-multitenant-ktor
~~~

기대 결과: 첫 검색은 결과가 없고, 두 번째 검색은 Ktor
io.bluetape4k.ktor.tenant 및 Reactor io.bluetape4k.tenant.reactor의 실제
import만 보여준다.

- [ ] README/spec/plan의 Markdown 무결성과 한국어 surface를 검사한다.

~~~bash
git diff --check
node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs \
  docs/superpowers/specs/2026-08-30-issue-215-tenant-context-design.md \
  docs/superpowers/plans/2026-08-30-issue-215-tenant-context-plan.md \
  10-multi-tenant/04-connection-factory-per-tenant-spring-webflux/README.ko.md \
  10-multi-tenant/07-multitenant-ktor/README.ko.md
~~~

기대 결과: 모든 changed Korean file에서 findings=0.

- [ ] repository static/build 검증을 실행한다.

~~~bash
./gradlew detekt --no-daemon --console=plain
./gradlew test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
~~~

Gradle 출력이 shell wrapper로 redirect되면 repository의 repo-test-summary 또는
context-mode 실행 경로를 사용하되, 실제 exit code와 report를 읽는다. 실패한
test/skip을 성공으로 기록하지 않는다.

- [ ] dependency resolution을 두 module에서 다시 확인한다.

~~~bash
./gradlew :04-connection-factory-per-tenant-spring-webflux:dependencyInsight \
  --configuration runtimeClasspath --dependency bluetape4k-tenant-reactor \
  --no-daemon --console=plain
./gradlew :07-multitenant-ktor:dependencyInsight \
  --configuration runtimeClasspath --dependency bluetape4k-ktor-tenant \
  --no-daemon --console=plain
~~~

기대 결과: 직접 version override 없이 public provider artifact와 중앙 BOM 경로가
보인다.

## 14. Task 10 — PR metadata와 CI handoff

**Files:** GitHub issue/PR metadata, PR body; local source는 verification 결과에 따라 최소 수정.

- [ ] Stack A와 Stack B의 head, base, changed-file 범위, test evidence를 기록한다.

~~~bash
git -C "$WT_A" rev-parse HEAD
git -C "$WT_B" rev-parse HEAD
gh issue view 215 --repo bluetape4k/exposed-r2dbc-workshop \
  --json number,title,body,assignees,labels,milestone,state
~~~

- [ ] PR A를 base develop, head feat/issue-215-webflux-tenant-context로 생성하고, PR B를 base feat/issue-215-webflux-tenant-context, head feat/issue-215-ktor-tenant-context로 생성한다. 각 PR 직전에 live gh pr view와 git rev-parse로 exact head를 다시 읽는다.

PR body는 한국어로 작성하고 다음 섹션을 포함한다.

~~~markdown
## DoD Status
- [x] 공개 tenant artifact를 versionless alias와 중앙 BOM으로 resolve
- [x] local Reactor/Ktor carrier key 제거
- [x] deterministic interleave/nested/cancellation/dispatcher/no-pollution fixture 통과
- [x] README.md와 README.ko.md 동기화
- [x] targeted tests, repository tests, detekt, diff check 통과

## Stacked Train
- Stack A: Task 10 직전에 git rev-parse HEAD로 기록한 실제 SHA -> develop
- Stack B: Task 10 직전에 git rev-parse HEAD로 기록한 실제 SHA -> feat/issue-215-webflux-tenant-context

## Dependency Evidence
- bluetape4k-dependencies:2.0.0-SNAPSHOT
- provider artifact/version resolution output and CI/POM evidence

## Verification
- exact commands and fresh result summary
~~~

- [ ] issue #215와 PR의 assignee/labels/milestone 및 body DoD 표현이 parity인지 확인한다. workflow/metadata가 자동으로 대체하지 않았는지 gh pr view --json body,assignees,labels,milestone로 검증한다.
- [ ] 두 PR의 CI가 fresh head에서 성공하고, 실패하면 gh-fix-ci 규칙으로 원인만 수정한다. auto-merge는 활성화하지 않는다.

## 15. Task 11 — exact-head 승인 후 rebase merge와 local sync

**Files:** GitHub PR state, local worktrees/branches.

- [ ] merge 직전에 사용자의 새 승인을 exact live head에 묶어 확인한다. approval 이전에 merge 명령을 실행하지 않는다.
- [ ] PR B를 rebase merge한 뒤 PR A를 rebase merge한다. 실제 GitHub merge method가 rebase인지 gh pr view의 merged state와 commit history로 확인한다.
- [ ] merge 후 develop을 fast-forward로 동기화한다.

~~~bash
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop fetch origin develop
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop switch develop
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop pull --ff-only origin develop
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop status --short --branch
git -C /Users/debop/work/bluetape4k/exposed-r2dbc-workshop rev-parse HEAD origin/develop
~~~

기대 결과: root develop과 origin/develop이 같은 SHA이며 dirty state가 없다.
기존 active/dirty worktree는 제거하지 않는다. 이번 작업의 두 worktree는 merge
후 clean·merged branch임을 별도 확인하고, 삭제가 필요하면 사용자에게 명시적
cleanup authority를 받은 뒤 수행한다.

## 16. Spec-to-task coverage

| 명세 요구 | 계획 task |
|---|---|
| versionless tenant aliases와 public POM/BOM authority | Task 0, 1, 9 |
| WebFlux provider producer/consumer 및 local key 제거 | Task 2, 3 |
| WebFlux interleave/nested/cancellation/no pollution | Task 2, 3, 9 |
| Ktor one-call bind/require 및 duplicate failure | Task 5, 6, 7 |
| Ktor dispatcher/nested/no pollution | Task 6, 7, 9 |
| 기존 validation/default/routing/schema isolation 유지 | Task 2, 3, 6, 7, 9 |
| paired README 동기화 | Task 4, 8, 9 |
| Kotlin assertion/cancellation/dispatcher 경계 | Task 4, 6, 9 |
| static/test/diff/CI/PR metadata/stack merge | Task 9, 10, 11 |
| no new module/common-core default/no diagrams | §2 변경하지 않음, Task 10 범위 확인 |

## 17. Step 3-R plan review record

검토 기준: 승인된 spec
docs/superpowers/specs/2026-08-30-issue-215-tenant-context-design.md, 현재
source tree, review-perspectives.md, step-3r-plan-review.md.

| 우선순위 | 관점 | 근거와 판정 | 필요한 수정 |
|---|---|---|---|
| P0/P1 없음 | Performance | 두 carrier 모두 request-bound lookup이며 새 blocking/round trip을 만들지 않는다. allocation/latency benchmark는 학습 예제의 범위를 넘으므로 N/A를 기록하고 dependencyInsight와 전체 test로 hot path를 compile/runtime 검증한다. | 없음 |
| P0/P1 없음 | Stability | Reactor cancellation, immutable nested context, Ktor duplicate binding, dispatcher hop, scheduler/executor cleanup을 Task 2/6/9에 명시했다. Testcontainers 신규 자원은 없고 기존 H2 lifecycle을 유지한다. | 없음 |
| P0/P1 없음 | Security | header 검증/registry와 authorization 부재 경고를 유지하고, provider carrier에 raw header를 넣지 않는다. unknown/blank/conflict negative tests가 Task 2/6에 있다. | 없음 |
| P0/P1 없음 | Operator/Ops | rollback 지점, dependency/POM evidence, CI/PR metadata, rebase merge와 local sync가 Task 0/3/8–11에 있다. 새 runtime config나 release가 없어 operational migration은 N/A다. | 없음 |
| P0/P1 없음 | Developer/API | 실제 provider package/signature, exact files, alias names, branch graph, RED→GREEN order를 고정했다. Exposed API/import 변경과 새 module이 없어 관련 receiver/deprecated-import check는 N/A이며 source scan으로 확인한다. | 없음 |
| P0/P1 없음 | User/caller | 두 README가 validation/carrier/default/one-call 제한과 runnable verification command를 설명한다. Ktor scoped override를 지원한다고 오해할 문구가 없다. | 없음 |

main-session integration 결과:

- Spec의 모든 acceptance/DoD가 §16의 task에 매핑되었다.
- Task ordering은 catalog/dependency → RED tests → production GREEN → docs → cross-stack verification 순이며 later artifact 의존이 없다.
- Ktor의 io.bluetape4k.ktor.tenant 실제 package와 TenantContext interface 사실을 spec correction commit 660f6516에 반영했다.
- 새 module, Spring auto-configuration, Exposed schema API, Docker resource가 없으므로 해당 conditional checks는 evidence-backed N/A로 닫았다.
- P0=0, P1=0. P2/P3 follow-up도 없으며, scoped override 요구는 명세에서 별도 upstream 범위로 명시적으로 제외했다.

## 18. Plan writer gate record

| 검사 | 상태 | 근거 |
|---|---|---|
| SPW-01 구조·범위 | 완료 | Goal, Architecture, Tech Stack, stack graph와 변경/비변경 파일을 §1–§2에 고정했다. |
| SPW-02 실행 순서 | 완료 | dependency/catalog → RED → GREEN → 문서 → cross-stack 검증 순서를 Task 0–9에 고정했다. |
| SPW-03 검증·rollback | 완료 | 위험별 조기 신호·검증·rollback과 명령별 기대 결과를 §3–§15에 기록했다. |
| SPW-04 공개 metadata | 완료 | PR body DoD, stacked base/head, CI, exact-head 승인과 rebase merge를 Task 10–11에 기록했다. |
| SPW-05 self-review | 완료 | template-marker/type scan, six-lens Step 3-R, spec-to-task coverage와 diff check를 §16–§19에 기록했다. |
| KO-01 독자·목적 | 완료 | 한국어 계획 제목과 목적/범위를 첫 문단에서 명확히 했다. |
| KO-02 용어 일관성 | 완료 | tenant, carrier, binding, provider API를 코드/API 이름과 구분해 사용했다. |
| KO-03 문장 자연스러움 | 완료 | 명령·식별자·URL은 원문을 보존하고 설명·판정은 한국어로 작성했다. |
| KO-04 표·목록 가독성 | 완료 | 파일 책임, 위험, coverage, review record를 표로 분리했다. |
| KO-05 독립 이해성 | 완료 | 각 task에 파일, 구현 의도, 명령, 기대 결과를 함께 기록했다. |
| KO-06 변경 영향 | 완료 | compatibility, no-new-module, rollback, locale pair 영향을 명시했다. |
| KO-07 감사 가능성 | 완료 | 실행 receipt, provider commit/POM, test/CI/merge evidence 경로를 기록했다. |

## 19. Plan self-review and handoff

- [x] spec §1–§8을 다시 읽어 각 acceptance가 §16에 연결되는지 확인한다.
- [x] 금지된 template marker 검색 결과가 없는지 확인한다. 실제 receipt 경로와
  Task 10 실행 시 기록할 SHA의 절차를 사용하므로 문서 템플릿 marker를 남기지 않는다.

~~~bash
rg -n 'TODO|FIXME|TBD|Similar to Task|fill in|적절히|추후|<head-sha>|\.bluetape/runs/\.\.\.' \
  docs/superpowers/plans/2026-08-30-issue-215-tenant-context-plan.md \
  | awk '$0 !~ /rg -n/ { print }' || true
~~~

- [x] import/package/type 이름이 모든 task에서 일관적인지 확인한다.

~~~bash
rg -n 'io\\.bluetape4k\\.(tenant\\.reactor|ktor\\.tenant)|TenantId\\(|TenantAlreadyBoundException|currentTenant\\(' \
  docs/superpowers/specs/2026-08-30-issue-215-tenant-context-design.md \
  docs/superpowers/plans/2026-08-30-issue-215-tenant-context-plan.md
~~~

- [x] plan 문서 자체의 writer gate를 완료하고 Lore commit으로 저장한다.

~~~bash
git add docs/superpowers/plans/2026-08-30-issue-215-tenant-context-plan.md
git commit -F - <<'EOF'
Issue #215의 승인된 carrier 설계를 실행 순서와 증거로 분해한다

WebFlux와 Ktor의 public adapter 전환을 stacked branch, TDD fixture,
검증·metadata·rebase merge 순서로 고정해 구현 handoff를 준비한다.

Constraint: 사용자 승인 명세와 bluetape4k Type A/Kotlin workflow gate를 모두 따른다.
Rejected: 두 예제를 한 번에 수정하는 단일 branch | PR train의 독립 검증과 rollback 경계를 흐린다.
Confidence: high
Scope-risk: moderate
Directive: Ktor one-call binding을 scoped override로 확장하지 말고 duplicate failure를 유지한다.
Tested: plan self-review; six-lens Step 3-R integration; template-marker/type consistency scan
Not-tested: plan 승인 전 production code와 Gradle execution은 시작하지 않는다.
EOF
~~~

계획 commit 후 사용자에게 계획 검토를 요청한다. 계획 승인 전에는 production
source, tests, README, catalog를 수정하지 않는다.
