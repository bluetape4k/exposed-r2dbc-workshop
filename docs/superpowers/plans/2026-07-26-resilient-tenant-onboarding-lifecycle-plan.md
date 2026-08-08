# 복구 가능한 테넌트 온보딩 생명주기 구현 계획

> **에이전트 작업자 참고:** 이 계획을 task 단위로 구현하려면 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 반드시 사용한다. 단계 진행 상태는 checkbox(`- [ ]`) 문법으로 추적한다.

**목표:** 토큰과 lease로 보호하는 테넌트 온보딩, 영속 복구, 안전한 runtime 공개를 보여 주는 별도의 Workshop 모듈을 추가한다.

**아키텍처:** 모듈 06은 입문 예제로 유지하고, 새 모듈 08이 복구 가능한 흐름을 담당한다. 생명주기 repository는 영속 claim과 token/version으로 보호되는 상태 전이를 제어하고, provisioner는 자신이 claim한 한 번의 시도를 제어하며, startup reconciler는 영속 `ACTIVE` 행에서 준비된 항목만 담는 process registry를 다시 구성한다.

**기술 스택:** Kotlin 2.3, Spring Boot WebFlux, Exposed R2DBC, H2 R2DBC, Kotlin Coroutines, JUnit 5, WebTestClient, bluetape4k coroutine test utilities.

---

## 파일 구조

| 경로 | 책임 |
| --- | --- |
| `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts` | 모듈 06의 의존성을 재사용하며 library를 추가하지 않는다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleTypes.kt` | 불변 상태, 실패 category, claim, API 안전 결과 model을 정의한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepository.kt` | Exposed table과 token/version으로 보호되는 영속 상태 전이를 정의한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeRegistry.kt` | 프로세스 로컬에서 준비된 connection factory의 소유권을 관리한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeResourceFactory.kt` | pool 생성, schema/seed, probe, close 경계를 관리한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconciler.kt` | stale 및 active 행의 startup 복구를 담당한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisioner.kt` | claim한 시도의 orchestration과 취소 안전 cleanup을 담당한다. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/*.kt` | 온보딩, 생명주기 polling, 오류 mapping을 담당한다. |
| `src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/**/*.kt` | repository, recovery, HTTP, concurrency, cancellation, restart 계약을 검증한다. |

## 작업 1: 독립 실행 가능한 모듈 부트스트랩

**파일:**
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/ResilientTenantOnboardingApp.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/ResilientTenantOnboardingConfig.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/resources/application.yml`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/ResilientTenantOnboardingAppTest.kt`

- [ ] **1단계: 실패하는 context 계약을 작성한다.**

```kotlin
@SpringBootTest
class ResilientTenantOnboardingAppTest {
    @Autowired lateinit var clock: Clock

    @Test
    fun `uses UTC clock for lifecycle leases`() {
        clock.zone shouldBeEqualTo ZoneOffset.UTC
    }

    @Test
    fun `rejects a lease no longer than the total provision timeout`() {
        shouldThrow<IllegalArgumentException> {
            TenantLifecycleProperties(Duration.ofSeconds(30), Duration.ofSeconds(30))
        }
    }
}
```

- [ ] **2단계: 모듈을 만들기 전에 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantOnboardingAppTest' --no-build-cache`
기대 결과: 모듈 또는 application class가 없으므로 FAIL.

- [ ] **3단계: 모듈 06의 dependency topology만 복사하고 최소한의 boot 경계를 추가한다.**

```kotlin
@SpringBootApplication
class ResilientTenantOnboardingApp

fun main(args: Array<String>) {
    runApplication<ResilientTenantOnboardingApp>(*args)
}

@Configuration
class ResilientTenantOnboardingConfig {
    @Bean
    fun lifecycleClock(): Clock = Clock.systemUTC()
}

data class TenantLifecycleProperties(
    val leaseDuration: Duration,
    val overallProvisionTimeout: Duration,
) {
    init { require(leaseDuration > overallProvisionTimeout) }
}
```

모듈 06과 같은 dependency alias를 사용한다. migration package와 Spring main class를 `exposed.r2dbc.multitenant.resilientonboarding`으로 변경하고 dependency는 추가하지 않는다. 기본 application properties는 격리된 인메모리 H2 database를 사용하도록 설정한다.

- [ ] **4단계: 부트스트랩과 자동 모듈 검색을 검증한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantOnboardingAppTest' --no-build-cache && ./gradlew projects`
기대 결과: PASS; project list에 모듈 08이 나타난다.

- [ ] **5단계: 부트스트랩 checkpoint를 커밋한다.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux
git commit -m "Establish a recoverable tenant onboarding example" \
  -m "Constraint: Reuse the chapter's dependency topology without widening its library surface." \
  -m "Rejected: Extend module 06 directly | it would mix introductory and recovery-focused contracts." \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: Keep module 08 independently runnable." \
  -m "Tested: ResilientTenantOnboardingAppTest and Gradle project discovery."
```

## 작업 2: 생명주기 데이터를 정의하고 조건부 소유권을 검증한다

**파일:**
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleTypes.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepository.kt`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepositoryTest.kt`

- [ ] **1단계: 실패하는 claim/retry/stale-owner 계약을 작성한다.**

```kotlin
@Test
fun `a stale reservation cannot fail a newer retry`() = runSuspendIO {
    val first = repository.claim(TenantId("acme"), "Acme", now).requireOwner()
    repository.markFailed(first, TenantFailureCode.SCHEMA, now) shouldBe true
    val retry = repository.claim(TenantId("acme"), "Acme", now.plusSeconds(1)).requireOwner()

    repository.markFailed(first, TenantFailureCode.POOL, now.plusSeconds(2)) shouldBe false
    requireNotNull(repository.find(TenantId("acme"))).reservationToken shouldBeEqualTo retry.reservationToken
}
```

새 claim(`attempt=1`), 동일한 active display name, 유효한 lease의 pending 결과, 다른 display-name conflict, 만료된 provisioning 선택을 검증하는 인접 테스트도 추가한다.

- [ ] **2단계: repository test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleRepositoryTest' --no-build-cache`
기대 결과: lifecycle symbol이 없으므로 FAIL.

- [ ] **3단계: 정확한 model과 repository signature를 구현한다.**

```kotlin
enum class TenantLifecycleStatus { PROVISIONING, ACTIVE, FAILED }
enum class TenantFailureCode { RESERVE, POOL, SCHEMA, PROBE, PUBLISH, RECOVERY }

sealed interface TenantClaim {
    data class Owner(val metadata: TenantMetadata) : TenantClaim
    data class Active(val metadata: TenantMetadata) : TenantClaim
    data class Pending(val metadata: TenantMetadata) : TenantClaim
    data class Conflict(val metadata: TenantMetadata) : TenantClaim
}

fun TenantClaim.requireOwner(): TenantClaim.Owner =
    this as? TenantClaim.Owner ?: error("Expected an owned lifecycle claim")

fun TenantMetadata.asOwner(): TenantClaim.Owner = TenantClaim.Owner(this)

data class TenantOnboardingRequest(val tenantId: TenantId, val displayName: String)

sealed interface TenantOnboardingResult {
    data class Created(val metadata: TenantMetadata) : TenantOnboardingResult
    data class Active(val metadata: TenantMetadata) : TenantOnboardingResult
    data class Pending(val metadata: TenantMetadata) : TenantOnboardingResult
    data class Conflict(val metadata: TenantMetadata) : TenantOnboardingResult
    data class Failed(val metadata: TenantMetadata) : TenantOnboardingResult
}

suspend fun claim(tenantId: TenantId, displayName: String, now: Instant): TenantClaim
suspend fun renewLease(owner: TenantClaim.Owner, now: Instant): TenantClaim.Owner?
suspend fun markActive(owner: TenantClaim.Owner, now: Instant): TenantMetadata?
suspend fun markFailed(owner: TenantClaim.Owner, code: TenantFailureCode, now: Instant): Boolean
suspend fun find(tenantId: TenantId): TenantMetadata?
suspend fun findExpiredProvisioning(now: Instant): List<TenantMetadata>
suspend fun findActive(): List<TenantMetadata>
```

`TenantMetadata`는 ID, display name, state, attempt, UUID reservation token, long version, lease expiry, 선택적 failure code, UTC timestamp를 담는 불변 값으로 정의한다. 모든 Exposed 호출은 `suspendTransaction`에서 실행한다. 모든 상태 전이에는 다음 predicate를 사용하고, 갱신된 행이 0개이면 소유권을 잃은 결과로 처리한다.

```kotlin
(TenantLifecycleTable.tenantId eq owner.metadata.tenantId.value) and
    (TenantLifecycleTable.reservationToken eq owner.metadata.reservationToken.toString()) and
    (TenantLifecycleTable.version eq owner.metadata.version)
```

exception text, URL, credential는 절대 저장하지 않는다. `KLoggingChannel`을 통해 tenant ID, attempt, failure category만 logging한다.

- [ ] **4단계: ownership test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleRepositoryTest' --no-build-cache`
기대 결과: PASS; 실패한 행은 유지되고 retry 시 attempt가 증가하며 stale owner는 쓸 수 없다.

- [ ] **5단계: 영속 소유권 동작을 커밋한다.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Protect tenant lifecycle transitions with durable ownership" \
  -m "Constraint: State writes must be safe across process boundaries." \
  -m "Rejected: JVM-only locking | it cannot protect a restarted or second process." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Guard every mutation with token and version." \
  -m "Tested: TenantLifecycleRepositoryTest."
```

## 작업 3: 준비된 runtime resource와 startup recovery를 구현한다

**파일:**
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeRegistry.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeResourceFactory.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconciler.kt`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconcilerTest.kt`

- [ ] **1단계: 실패하는 recovery 계약을 작성한다.**

```kotlin
@Test
fun `expired provisioning is retained as recovery failure`() = runSuspendIO {
    repository.claim(TenantId("late"), "Late", now.minusSeconds(600)).requireOwner()

    reconciler.reconcile(now)

    requireNotNull(repository.find(TenantId("late"))).apply {
        status shouldBeEqualTo TenantLifecycleStatus.FAILED
        lastFailureCode shouldBeEqualTo TenantFailureCode.RECOVERY
    }
}

@Test
fun `active tenant is published only after probe`() = runSuspendIO {
    val active = activeTenant("ready")
    reconciler.reconcile(now)

    resourceFactory.probedTenantIds shouldContain active.tenantId
    registry.connectionFactoryOrNull(active.tenantId) shouldNotBe null
}
```

- [ ] **2단계: recovery test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleReconcilerTest' --no-build-cache`
기대 결과: registry, factory, reconciler가 정의되지 않았으므로 FAIL.

- [ ] **3단계: resource 경계와 reconciler를 구현한다.**

```kotlin
interface TenantRuntimeResourceFactory {
    suspend fun create(metadata: TenantMetadata): TenantResources
    suspend fun probe(resources: TenantResources)
    suspend fun close(resources: TenantResources)
}

class TenantRuntimeRegistry {
    fun publish(tenantId: TenantId, resources: TenantResources)
    suspend fun unregister(tenantId: TenantId)
    fun connectionFactoryOrNull(tenantId: TenantId): ConnectionFactory?
}
```

`reconcile(now)`는 먼저 `findExpiredProvisioning(now)`을 호출하고 일치하는 owner token/version으로 `RECOVERY`를 기록한다. 그런 다음 각 `findActive()` resource를 probe한 뒤 `publish`를 호출한다. probe 실패는 조건에 따라 `FAILED(PROBE)`가 되며 후보 resource를 close한다. 주입된 UTC `Clock`을 사용하고 system time을 직접 사용하지 않는다.

- [ ] **4단계: recovery test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleReconcilerTest' --no-build-cache`
기대 결과: PASS; probe와 registry 공개가 차례로 성공하기 전에는 영속 행이 routing-ready 상태가 아니다.

- [ ] **5단계: recovery 동작을 커밋한다.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Recover durable tenant lifecycle state before routing" \
  -m "Constraint: A durable row alone cannot prove a usable live connection." \
  -m "Rejected: Publish active rows without probing | it would route to stale resources." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Probe before registry publication." \
  -m "Tested: TenantLifecycleReconcilerTest."
```

## 작업 4: claim한 provisioning과 polling 계약을 공개한다

**파일:**
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisioner.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRoutingConnectionFactory.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantOnboardingController.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantLifecycleController.kt`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantApiExceptionHandler.kt`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantOnboardingControllerTest.kt`

- [ ] **1단계: 실패하는 WebFlux response 계약을 작성한다.**

```kotlin
webTestClient.post().uri("/api/admin/tenants")
    .header("X-Admin-Token", adminToken)
    .bodyValue(mapOf("tenantId" to "acme", "displayName" to "Acme"))
    .exchange()
    .expectStatus().isCreated
    .expectHeader().valueMatches("Location", ".*/api/tenants/acme")
    .expectBody().jsonPath("$.status").isEqualTo("ACTIVE")

webTestClient.get().uri("/api/tenants/acme")
    .header("X-Admin-Token", adminToken)
    .exchange()
    .expectStatus().isOk
    .expectBody().jsonPath("$.attempt").isEqualTo(1)
```

동일한 active 입력(`200`), 유효한 claim(`202`), 다른 display name(`409`), 보존된 실패 뒤의 retry, operator token 누락, runtime 공개 전 routing(`404`)을 각각 검증하는 assertion을 추가한다.

- [ ] **2단계: controller test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantOnboardingControllerTest' --no-build-cache`
기대 결과: endpoint와 DTO가 없으므로 FAIL.

- [ ] **3단계: orchestration과 안전한 DTO를 구현한다.**

```kotlin
suspend fun onboard(request: TenantOnboardingRequest): TenantOnboardingResult = when (
    val claim = repository.claim(request.tenantId, request.displayName, clock.instant())
) {
    is TenantClaim.Active -> TenantOnboardingResult.Active(claim.metadata)
    is TenantClaim.Pending -> TenantOnboardingResult.Pending(claim.metadata)
    is TenantClaim.Conflict -> TenantOnboardingResult.Conflict(claim.metadata)
    is TenantClaim.Owner -> provision(claim)
}
```

`provision(owner)`는 `withTimeout(properties.overallProvisionTimeout)` 안에서 실행한다. 각 external phase 전에 lease를 갱신한 뒤 resource 생성, schema seed, probe, 조건부 active 전이, registry 공개를 차례로 수행한다. 설정은 `leaseDuration <= overallProvisionTimeout`을 거부하므로 살아 있는 owner lease가 제한된 한 번의 시도보다 항상 오래 유지된다. `catch (CancellationException)`에서는 `withContext(NonCancellable)` 안에서 `cleanupFailedAttempt`를 호출한 뒤 다시 던진다. 그 밖의 실패는 적절한 enum category로 변환해 안전한 failure result를 반환한다. cleanup은 owner token/version으로만 failed 전이를 수행하고, 해당 tenant만 unregister하며, 이번 호출이 만든 resource만 close한다. DTO에는 ID, display name, state, attempt, 선택적 failure category만 공개한다.

- [ ] **4단계: HTTP와 routing 계약을 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantOnboardingControllerTest' --no-build-cache`
기대 결과: 정확한 `201/200/202/409` response 동작으로 PASS하며 output에 raw exception이나 R2DBC URL이 없다.

- [ ] **5단계: HTTP 생명주기 의미를 커밋한다.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Surface recoverable tenant onboarding states safely" \
  -m "Constraint: Operators need retriable state without infrastructure disclosure." \
  -m "Rejected: Return raw exception details | it leaks internals and gives callers no stable contract." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep transport DTOs limited to lifecycle-safe fields." \
  -m "Tested: TenantOnboardingControllerTest."
```

## 작업 5: concurrency, cancellation, restart recovery를 검증한다

**파일:**
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisionerConcurrencyTest.kt`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisionerCancellationTest.kt`
- 테스트: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRestartRecoveryTest.kt`

- [ ] **1단계: 동일 tenant concurrency를 다루는 실패 테스트를 작성한다.**

```kotlin
SuspendedJobTester()
    .workers(8)
    .rounds(32)
    .add { provisioner.onboard(TenantOnboardingRequest(TenantId("acme"), "Acme")) }
    .run()

requireNotNull(repository.find(TenantId("acme"))).apply {
    status shouldBeEqualTo TenantLifecycleStatus.ACTIVE
    attempt shouldBeEqualTo 1
}
resourceFactory.createdCount(TenantId("acme")) shouldBeEqualTo 1
```

- [ ] **2단계: 실패하는 cancellation 및 restart 테스트를 작성한다.**

```kotlin
val enteredCreate = CompletableDeferred<Unit>()
val continueCreate = CompletableDeferred<Unit>()
resourceFactory.onCreate = { enteredCreate.complete(Unit); continueCreate.await() }

val job = async { provisioner.onboard(TenantOnboardingRequest(TenantId("cancelled"), "Cancelled")) }
enteredCreate.await()
job.cancelAndJoin()

requireNotNull(repository.find(TenantId("cancelled"))).status shouldBeEqualTo TenantLifecycleStatus.FAILED
resourceFactory.closedTenantIds shouldContain TenantId("cancelled")
```

restart 검증에는 고유한 이름의 임시 H2 file database를 사용한다. active 행 하나를 저장하고 해당 파일에서 새 registry/reconciler를 구성한 뒤 reconcile을 실행하며, probe 후에만 공개되는지 검증한다. cleanup에서 resource를 close하고 임시 파일을 삭제한다.

- [ ] **3단계: 구현을 변경하기 전에 resilience test를 실행한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantProvisioner*Test' --tests '*TenantLifecycleRestartRecoveryTest' --no-build-cache`
기대 결과: resource factory hook과 cleanup/rehydration이 계약을 만족할 때까지 FAIL.

- [ ] **4단계: 해당 테스트에 필요한 seam만 추가한다.** test source에는 테스트가 제어하는 resource factory를 둔다. production code는 계속 `TenantRuntimeResourceFactory`에 의존한다. global JVM lock, 새 dependency, external database, test container는 추가하지 않는다.

- [ ] **5단계: 깨끗한 test output에서 실행하고 커밋한다.**

실행: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:cleanTest :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache`
기대 결과: PASS; resource는 한 번의 attempt만 생성하고, 취소된 작업은 `FAILED`를 유지하며, 새 startup은 probe한 active tenant를 rehydrate한다.

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test
git commit -m "Prove tenant onboarding recovery under interruption" \
  -m "Constraint: Cancellation and restart must preserve ownership evidence." \
  -m "Rejected: Happy-path-only tests | they cannot establish recovery semantics." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep cancellation cleanup token-owned and non-cancellable." \
  -m "Tested: resilient provisioner concurrency, cancellation, and restart tests."
```

## 작업 6: 이중 언어 문서와 CI coverage를 등록한다

**파일:**
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md`
- 생성: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md`
- 수정: `10-multi-tenant/README.md`
- 수정: `10-multi-tenant/README.ko.md`
- 수정: `README.md`
- 수정: `README.ko.md`
- 수정: `.github/workflows/Examples.yml`

- [ ] **1단계: 실패하는 discoverability check를 실행한다.**

```bash
rg -q '08-resilient-tenant-onboarding-spring-webflux' README.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' README.ko.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' 10-multi-tenant/README.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' 10-multi-tenant/README.ko.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' .github/workflows/Examples.yml
```

기대 결과: 모듈을 등록하기 전에는 FAIL.

- [ ] **2단계: English와 Korean 모듈 README를 작성한다.** 두 README 모두 영속 상태, `201/200/202/409` 결과, polling, failure-code 경계, retry ownership, restart reconciliation을 설명해야 하며, H2 file persistence가 multi-node production control plane이 아니라 테스트 수단임을 명시해야 한다.

```markdown
| Result | Meaning |
| --- | --- |
| `201 Created` | This request claimed, prepared, probed, and published the tenant. |
| `202 Accepted` | A non-expired claim is already being prepared; poll the lifecycle resource. |
```

- [ ] **3단계: 모든 README와 `Examples.yml`의 세 위치에서 모듈 08을 07 뒤에 등록한다.** 같은 모듈 경로를 changed-path filter, Gradle task list/matrix, test-artifact collection list에 추가한다. 관련 없는 job condition은 변경하지 않는다.

- [ ] **4단계: 문서와 workflow syntax를 검증한다.**

실행: `./gradlew projects && actionlint .github/workflows/Examples.yml && git diff --check`
기대 결과: PASS; 두 언어에서 08을 찾을 수 있고 CI 실행과 report collection 대상으로 선택된다.

- [ ] **5단계: 문서와 CI parity를 커밋한다.**

```bash
git add README.md README.ko.md 10-multi-tenant/README.md 10-multi-tenant/README.ko.md \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md \
  .github/workflows/Examples.yml
git commit -m "Make resilient tenant onboarding discoverable and verified" \
  -m "Constraint: Workshop examples must be visible in both languages and CI." \
  -m "Rejected: Module-only documentation | it makes the example undiscoverable and untested in CI." \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: Keep README and Examples workflow registrations aligned." \
  -m "Tested: Gradle projects, actionlint, and diff check."
```

## 작업 7: 최종 gate를 실행하고 delivery를 분리한다

**파일:**
- 검증 명령에서 defect가 드러날 때만 위에서 계획한 파일을 수정한다.

- [ ] **1단계: 모든 최종 check를 실행한다.**

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
./gradlew :08-resilient-tenant-onboarding-spring-webflux:cleanTest :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
./gradlew projects
actionlint .github/workflows/Examples.yml
git diff --check
```

기대 결과: 모든 명령이 `0`으로 종료된다.

- [ ] **2단계: spec coverage를 검토한다.** 모듈 06을 변경하지 않았는지 확인한다. 모든 상태 전이에 token/version ownership이 포함되고, 어떤 실패도 metadata를 삭제하지 않으며, routing은 성공한 probe 뒤의 registry 공개를 요구하는지 확인한다. API/README가 raw error와 connection detail을 제외하는지, English/Korean 문서와 모든 CI 항목이 08을 열거하는지도 확인한다.

- [ ] **3단계: Lore protocol에 따라 검증된 repair work만 커밋한다.**

```bash
git status --short
git log -1 --format=full
```

기대 결과: 범위를 벗어난 파일이 없고, 각 커밋에 constraint, rejected alternative, confidence, scope risk, directive, fresh test evidence가 기록된다.

- [ ] **4단계: review-ready local branch에서 멈춘다.** pull request 생성, merge, deploy는 하지 않는다. 해당 작업에는 이후의 명시적 요청이 필요하다.

## Rollback 및 위험 제어

- 모듈 08은 격리되어 있다. 모듈 06을 변경하지 않고 기능을 제거하려면 모듈 08 디렉터리, 최상위/chapter README 링크 4개, `Examples.yml` 항목 3개를 되돌린다.
- H2 file persistence는 restart recovery를 테스트하기 위한 용도로만 존재한다. 문서에서 distributed lock, external secret management, PostgreSQL semantics, production authorization을 지원한다고 오해하게 해서는 안 된다.
- runtime registry는 프로세스 로컬 cache다. 영속 `ACTIVE` 행은 새 probe와 registry 공개가 성공한 뒤에만 routing-ready 상태가 된다.
