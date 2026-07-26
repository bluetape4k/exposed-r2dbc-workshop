# Resilient Tenant Onboarding Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separate Workshop module that demonstrates token- and lease-guarded tenant onboarding, durable recovery, and safe runtime publication.

**Architecture:** Module 06 remains the introductory example; new module 08 owns the resilient flow. A lifecycle repository controls durable claims and token/version-guarded transitions, the provisioner controls one claimed attempt, and a startup reconciler rebuilds the ready-only process registry from durable active records.

**Tech Stack:** Kotlin 2.3, Spring Boot WebFlux, Exposed R2DBC, H2 R2DBC, Kotlin Coroutines, JUnit 5, WebTestClient, bluetape4k coroutine test utilities.

---

## File map

| Path | Responsibility |
| --- | --- |
| `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts` | Reuses module 06 dependencies; adds no library. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleTypes.kt` | Immutable state, failure category, claim, and API-safe result models. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepository.kt` | Exposed table and token/version-guarded durable transitions. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeRegistry.kt` | Process-local, ready-only connection factory ownership. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeResourceFactory.kt` | Pool creation, schema/seed, probe, and close boundaries. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconciler.kt` | Startup recovery for stale and active records. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisioner.kt` | Claimed-attempt orchestration and cancellation-safe cleanup. |
| `src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/*.kt` | Onboarding, lifecycle polling, and error mapping. |
| `src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/**/*.kt` | Repository, recovery, HTTP, concurrency, cancellation, restart contracts. |

## Task 1: Bootstrap an independently runnable module

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/ResilientTenantOnboardingApp.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/ResilientTenantOnboardingConfig.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/resources/application.yml`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/ResilientTenantOnboardingAppTest.kt`

- [ ] **Step 1: Write the failing context contract.**

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

- [ ] **Step 2: Run it before creating the module.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantOnboardingAppTest' --no-build-cache`
Expected: FAIL because the module or application class is absent.

- [ ] **Step 3: Copy only the module 06 dependency topology and add the minimum boot boundary.**

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

Use the same dependency aliases as module 06. Change the migration package and Spring main class to `exposed.r2dbc.multitenant.resilientonboarding`; add no dependency. Set default application properties to an isolated in-memory H2 database.

- [ ] **Step 4: Verify bootstrapping and automatic module discovery.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantOnboardingAppTest' --no-build-cache && ./gradlew projects`
Expected: PASS; module 08 appears in the project list.

- [ ] **Step 5: Commit the bootstrap checkpoint.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux
git commit -m "Establish a recoverable tenant onboarding example" \
  -m "Constraint: Reuse the chapter's dependency topology without widening its library surface." \
  -m "Rejected: Extend module 06 directly | it would mix introductory and recovery-focused contracts." \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: Keep module 08 independently runnable." \
  -m "Tested: ResilientTenantOnboardingAppTest and Gradle project discovery."
```

## Task 2: Define lifecycle data and prove conditional ownership

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleTypes.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepository.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRepositoryTest.kt`

- [ ] **Step 1: Write failing claim/retry/stale-owner contracts.**

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

Add adjacent tests for a new claim (`attempt=1`), same active display name, live lease pending result, different display-name conflict, and expired provisioning selection.

- [ ] **Step 2: Run the repository test.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleRepositoryTest' --no-build-cache`
Expected: FAIL because lifecycle symbols do not exist.

- [ ] **Step 3: Implement exact models and repository signatures.**

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

Define `TenantMetadata` as an immutable value containing ID, display name, state, attempt, UUID reservation token, long version, lease expiry, optional failure code, and UTC timestamps. Every Exposed call runs in `suspendTransaction`. For every transition, use this predicate and treat zero updated rows as a lost ownership result:

```kotlin
(TenantLifecycleTable.tenantId eq owner.metadata.tenantId.value) and
    (TenantLifecycleTable.reservationToken eq owner.metadata.reservationToken.toString()) and
    (TenantLifecycleTable.version eq owner.metadata.version)
```

Never persist exception text, URLs, or credentials. Log only tenant ID, attempt, and failure category through `KLoggingChannel`.

- [ ] **Step 4: Run ownership tests.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleRepositoryTest' --no-build-cache`
Expected: PASS; a failed row is retained, retry increments attempt, and a stale owner cannot write.

- [ ] **Step 5: Commit durable ownership behavior.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Protect tenant lifecycle transitions with durable ownership" \
  -m "Constraint: State writes must be safe across process boundaries." \
  -m "Rejected: JVM-only locking | it cannot protect a restarted or second process." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Guard every mutation with token and version." \
  -m "Tested: TenantLifecycleRepositoryTest."
```

## Task 3: Build ready-only runtime resources and startup recovery

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeRegistry.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeResourceFactory.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconciler.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleReconcilerTest.kt`

- [ ] **Step 1: Write failing recovery contracts.**

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

- [ ] **Step 2: Run the recovery test.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleReconcilerTest' --no-build-cache`
Expected: FAIL because registry, factory, and reconciler are undefined.

- [ ] **Step 3: Implement the resource boundary and reconciler.**

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

`reconcile(now)` first calls `findExpiredProvisioning(now)` and records `RECOVERY` through its matching owner token/version. It then probes each `findActive()` resource before calling `publish`. Probe failure conditionally becomes `FAILED(PROBE)` and closes the candidate resource. Use the injected UTC `Clock`; do not use system time directly.

- [ ] **Step 4: Run recovery tests.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantLifecycleReconcilerTest' --no-build-cache`
Expected: PASS; a durable row is not routing-ready until probe then registry publication succeed.

- [ ] **Step 5: Commit recovery behavior.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Recover durable tenant lifecycle state before routing" \
  -m "Constraint: A durable row alone cannot prove a usable live connection." \
  -m "Rejected: Publish active rows without probing | it would route to stale resources." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Probe before registry publication." \
  -m "Tested: TenantLifecycleReconcilerTest."
```

## Task 4: Expose claimed provisioning and polling contracts

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisioner.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRoutingConnectionFactory.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantOnboardingController.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantLifecycleController.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantApiExceptionHandler.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/controller/TenantOnboardingControllerTest.kt`

- [ ] **Step 1: Write failing WebFlux response contracts.**

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

Add individual assertions for same active input (`200`), live claim (`202`), different display name (`409`), retained failure followed by retry, missing operator token, and routing before runtime publication (`404`).

- [ ] **Step 2: Run controller tests.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantOnboardingControllerTest' --no-build-cache`
Expected: FAIL because the endpoints and DTOs do not exist.

- [ ] **Step 3: Implement orchestration and safe DTOs.**

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

`provision(owner)` runs inside `withTimeout(properties.overallProvisionTimeout)`, renews the lease before each external phase, then creates resources, seeds schema, probes, conditionally marks active, and only then publishes in the registry. Configuration rejects `leaseDuration <= overallProvisionTimeout`, so a live owner lease always outlasts one bounded attempt. In `catch (CancellationException)`, call `cleanupFailedAttempt` in `withContext(NonCancellable)`, then rethrow. Other failures map to the appropriate enum category and return a safe failure result. Cleanup marks failed only with the owner token/version, unregisters only that tenant, and closes only the resources created by this call. DTOs expose only ID, display name, state, attempt, and optional failure category.

- [ ] **Step 4: Run HTTP and routing contracts.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*TenantOnboardingControllerTest' --no-build-cache`
Expected: PASS with exact `201/200/202/409` response behavior and no raw exception or R2DBC URL in output.

- [ ] **Step 5: Commit HTTP lifecycle semantics.**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src
git commit -m "Surface recoverable tenant onboarding states safely" \
  -m "Constraint: Operators need retriable state without infrastructure disclosure." \
  -m "Rejected: Return raw exception details | it leaks internals and gives callers no stable contract." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep transport DTOs limited to lifecycle-safe fields." \
  -m "Tested: TenantOnboardingControllerTest."
```

## Task 5: Prove concurrency, cancellation, and restart recovery

**Files:**
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisionerConcurrencyTest.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/ResilientTenantProvisionerCancellationTest.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleRestartRecoveryTest.kt`

- [ ] **Step 1: Write failing same-tenant concurrency coverage.**

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

- [ ] **Step 2: Write failing cancellation and restart coverage.**

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

For restart, use a uniquely named temporary H2 file database, persist one active row, construct a new registry/reconciler from that file, reconcile, and assert it publishes only after probing. Close resources and remove the temporary file in cleanup.

- [ ] **Step 3: Run resilience tests before changing implementation.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --tests '*ResilientTenantProvisioner*Test' --tests '*TenantLifecycleRestartRecoveryTest' --no-build-cache`
Expected: FAIL until resource factory hooks and cleanup/rehydration satisfy the contracts.

- [ ] **Step 4: Add only the seams required by those tests.** Keep a test-controlled resource factory in test sources; production code remains dependent on `TenantRuntimeResourceFactory`. Do not add a global JVM lock, a new dependency, external database, or test container.

- [ ] **Step 5: Run from clean test output and commit.**

Run: `./gradlew :08-resilient-tenant-onboarding-spring-webflux:cleanTest :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache`
Expected: PASS; only one attempt creates resources, cancelled work retains `FAILED`, and fresh startup rehydrates a probed active tenant.

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test
git commit -m "Prove tenant onboarding recovery under interruption" \
  -m "Constraint: Cancellation and restart must preserve ownership evidence." \
  -m "Rejected: Happy-path-only tests | they cannot establish recovery semantics." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep cancellation cleanup token-owned and non-cancellable." \
  -m "Tested: resilient provisioner concurrency, cancellation, and restart tests."
```

## Task 6: Register bilingual documentation and CI coverage

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md`
- Modify: `10-multi-tenant/README.md`
- Modify: `10-multi-tenant/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `.github/workflows/Examples.yml`

- [ ] **Step 1: Run failing discoverability checks.**

```bash
rg -q '08-resilient-tenant-onboarding-spring-webflux' README.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' README.ko.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' 10-multi-tenant/README.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' 10-multi-tenant/README.ko.md
rg -q '08-resilient-tenant-onboarding-spring-webflux' .github/workflows/Examples.yml
```

Expected: FAIL before the module is registered.

- [ ] **Step 2: Write the English and Korean module READMEs.** Both must explain durable states, the `201/200/202/409` outcomes, polling, failure-code boundary, retry ownership, restart reconciliation, and that H2 file persistence is a test mechanism rather than a multi-node production control plane.

```markdown
| Result | Meaning |
| --- | --- |
| `201 Created` | This request claimed, prepared, probed, and published the tenant. |
| `202 Accepted` | A non-expired claim is already being prepared; poll the lifecycle resource. |
```

- [ ] **Step 3: Register module 08 after 07 in all READMEs and all three `Examples.yml` locations.** Add the same module path to the changed-path filter, Gradle task list/matrix, and test-artifact collection list. Do not change unrelated job conditions.

- [ ] **Step 4: Verify docs and workflow syntax.**

Run: `./gradlew projects && actionlint .github/workflows/Examples.yml && git diff --check`
Expected: PASS; 08 is discoverable in both languages and selected for CI execution and report collection.

- [ ] **Step 5: Commit documentation and CI parity.**

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

## Task 7: Run final gates and keep delivery separate

**Files:**
- Modify only planned files above if a verification command exposes a defect.

- [ ] **Step 1: Run all final checks.**

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
./gradlew :08-resilient-tenant-onboarding-spring-webflux:cleanTest :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
./gradlew projects
actionlint .github/workflows/Examples.yml
git diff --check
```

Expected: every command exits `0`.

- [ ] **Step 2: Perform the spec-coverage review.** Confirm module 06 is untouched; every transition includes token/version ownership; no failure deletes metadata; routing requires successful probe then registry publication; API/README omit raw errors and connection details; English/Korean docs and all CI entries list 08.

- [ ] **Step 3: Commit only verified repair work using the Lore protocol.**

```bash
git status --short
git log -1 --format=full
```

Expected: no out-of-scope file is present; each commit records constraint, rejected alternative, confidence, scope risk, directive, and fresh test evidence.

- [ ] **Step 4: Stop on a review-ready local branch.** Do not create a pull request, merge, or deploy; those actions need a later explicit request.

## Rollback and risk controls

- Module 08 is isolated. Revert its directory, the four top-level/chapter README links, and the three `Examples.yml` entries to remove the feature without changing module 06.
- H2 file persistence exists only to test restart recovery. The documentation must not imply distributed locks, external secret management, PostgreSQL semantics, or production authorization.
- The runtime registry is a process-local cache. A durable `ACTIVE` row becomes routing-ready only after a fresh probe and successful registry publication.
