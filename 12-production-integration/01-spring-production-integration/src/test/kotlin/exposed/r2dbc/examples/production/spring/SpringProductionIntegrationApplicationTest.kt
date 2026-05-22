package exposed.r2dbc.examples.production.spring

import exposed.r2dbc.examples.production.spring.app.AccountView
import exposed.r2dbc.examples.production.spring.app.AuthProfileView
import exposed.r2dbc.examples.production.spring.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.spring.app.DispatchOutboundView
import exposed.r2dbc.examples.production.spring.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.spring.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.spring.app.OutboxEventView
import exposed.r2dbc.examples.production.spring.app.OutboxStatus
import exposed.r2dbc.examples.production.spring.app.OutboundDelivery
import exposed.r2dbc.examples.production.spring.app.OutboundDispatchResult
import exposed.r2dbc.examples.production.spring.app.PublishOutboxView
import exposed.r2dbc.examples.production.spring.app.ReadinessView
import exposed.r2dbc.examples.production.spring.app.RealtimeDelivery
import exposed.r2dbc.examples.production.spring.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.spring.app.SessionView
import exposed.r2dbc.examples.production.spring.app.SessionsView
import exposed.r2dbc.examples.production.spring.app.SpringProductionRepository
import exposed.r2dbc.examples.production.spring.app.SpringProductionWorkflowService
import exposed.r2dbc.examples.production.spring.app.StructuredError
import exposed.r2dbc.examples.production.spring.app.WorkItemView
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringProductionIntegrationApplicationTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val repository: SpringProductionRepository,
    @Autowired private val outboundDelivery: RecordingOutboundDelivery,
) {

    @BeforeEach
    fun resetDatabase() = runTest {
        repository.reset()
        outboundDelivery.reset()
    }

    @Test
    fun `register account and authorize permission`() = runTest {
        val response = client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterAccountRequest("operator", "spring-key", "work:create"))
            .exchange()
            .expectStatus().isOk
            .expectBody(AccountView::class.java)
            .returnResult()
            .responseBody

        val account = requireNotNull(response)
        account.username shouldBeEqualTo "operator"
        account.roles shouldBeEqualTo setOf("USER")
        repository.hasPermission("spring-key", "work:create") shouldBeEqualTo true
    }

    @Test
    fun `missing and invalid basic credentials are rejected`() {
        client.get()
            .uri("/production/profile")
            .exchange()
            .expectStatus().isUnauthorized

        client.get()
            .uri("/production/profile")
            .basic("alice", "wrong")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `basic credentials expose profile and deny non-admin access`() {
        val profile = client.get()
            .uri("/production/profile")
            .basic("alice", "password")
            .exchange()
            .expectStatus().isOk
            .expectBody(AuthProfileView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(profile).roles shouldBeEqualTo setOf("USER")

        client.get()
            .uri("/production/admin")
            .basic("alice", "password")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `admin credentials access protected admin endpoint`() {
        val profile = client.get()
            .uri("/production/admin")
            .basic("admin", "password")
            .exchange()
            .expectStatus().isOk
            .expectBody(AuthProfileView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(profile).roles shouldBeEqualTo setOf("ADMIN", "USER")
    }

    @Test
    fun `public registration cannot grant admin role`() {
        val account = client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                RegisterAccountRequest(
                    username = "self-admin",
                    apiKey = "self-admin-key",
                    permission = "outbound:create",
                    password = "password",
                    displayName = "Self Admin",
                    roles = setOf("ADMIN", "USER"),
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(AccountView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(account).roles shouldBeEqualTo setOf("USER")
        account.permission shouldBeEqualTo "work:create"

        client.get()
            .uri("/production/admin")
            .basic("self-admin", "password")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `authenticated users persist session metadata without listing raw tokens`() {
        val created = client.post()
            .uri("/production/sessions")
            .basic("alice", "password")
            .exchange()
            .expectStatus().isOk
            .expectBody(SessionView::class.java)
            .returnResult()
            .responseBody

        val session = requireNotNull(created)
        session.username shouldBeEqualTo "alice"
        requireNotNull(session.token)
        (session.expiresAtEpochMs > session.issuedAtEpochMs) shouldBeEqualTo true

        val sessions = client.get()
            .uri("/production/sessions")
            .basic("alice", "password")
            .exchange()
            .expectStatus().isOk
            .expectBody(SessionsView::class.java)
            .returnResult()
            .responseBody

        val listed = requireNotNull(sessions).sessions.single { it.username == "alice" }
        listed.token shouldBeEqualTo null
    }

    @Test
    fun `create work item persists realtime event and supports SSE replay`() = runTest {
        client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterAccountRequest("worker", "spring-key", "work:create"))
            .exchange()
            .expectStatus().isOk

        val workItem = client.post()
            .uri("/production/work-items")
            .header("X-Api-Key", "spring-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CreateWorkItemRequest("alice", "ship order"))
            .exchange()
            .expectStatus().isOk
            .expectBody(WorkItemView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(workItem).status shouldBeEqualTo "ACCEPTED"
        repository.outboxEvents().single().status shouldBeEqualTo OutboxStatus.PENDING

        val published = client.post()
            .uri("/production/outbox/publish")
            .header("X-Api-Key", "spring-key")
            .exchange()
            .expectStatus().isOk
            .expectBody(PublishOutboxView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(published).delivered shouldBeEqualTo 1
        val stored = repository.outboxEvents().single()
        stored.status shouldBeEqualTo OutboxStatus.PUBLISHED
        stored.attempts shouldBeEqualTo 1
        repository.replayEvents(0).size shouldBeGreaterThan 0

        val events = client.get()
            .uri("/production/realtime?after=0")
            .header("X-Api-Key", "spring-key")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(OutboxEventView::class.java)
            .responseBody
            .take(1)
            .collectList()
            .block(Duration.ofSeconds(5))

        requireNotNull(events).first().eventType shouldBeEqualTo "work-item.accepted"
    }

    @Test
    fun `replay boundary returns only later published events`() = runTest {
        repository.createWorkItem(CreateWorkItemRequest("alice", "first"))
        repository.createWorkItem(CreateWorkItemRequest("alice", "second"))
        repository.publishPending(AlwaysDeliveringRealtimeDelivery)

        val firstSequence = repository.outboxEvents().first().sequence
        val replayed = repository.replayEvents(firstSequence)

        replayed.map { it.payload } shouldBeEqualTo listOf("second")
    }

    @Test
    fun `delivery failure is recorded without losing realtime outbox event`() = runTest {
        val failingService = SpringProductionWorkflowService(repository, FailingRealtimeDelivery, outboundDelivery)
        failingService.createWorkItem(CreateWorkItemRequest("alice", "will fail"))

        val result = failingService.publishPendingOutbox()

        result.attempted shouldBeEqualTo 1
        result.delivered shouldBeEqualTo 0
        result.failed shouldBeEqualTo 1
        val failed = repository.outboxEvents().single()
        failed.status shouldBeEqualTo OutboxStatus.FAILED
        failed.lastError shouldBeEqualTo "realtime delivery returned false"
    }

    @Test
    fun `blank work item request returns structured validation error`() {
        client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterAccountRequest("worker", "spring-key", "work:create"))
            .exchange()
            .expectStatus().isOk

        val error = client.post()
            .uri("/production/work-items")
            .header("X-Api-Key", "spring-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CreateWorkItemRequest(" ", "payload"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(StructuredError::class.java)
            .returnResult()
            .responseBody

        requireNotNull(error).code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `malformed JSON returns structured parse error`() {
        val error = client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(StructuredError::class.java)
            .returnResult()
            .responseBody

        requireNotNull(error).code shouldBeEqualTo "INVALID_JSON"
    }

    @Test
    fun `duplicate idempotency key returns conflict`() {
        val request = EnqueueOutboundRequest("payment-1", "https://example.test/payments", "payload")

        client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "admin-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk

        val error = client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "admin-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody(StructuredError::class.java)
            .returnResult()
            .responseBody

        requireNotNull(error).code shouldBeEqualTo "IDEMPOTENCY_CONFLICT"
    }

    @Test
    fun `outbound dispatch marks success after persisted enqueue`() = runTest {
        outboundDelivery.enqueue(OutboundDispatchResult(202))
        client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "admin-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(EnqueueOutboundRequest("payment-success", "https://example.test/payments", "payload"))
            .exchange()
            .expectStatus().isOk

        val result = client.post()
            .uri("/production/outbound/dispatch")
            .header("X-Api-Key", "admin-api-key")
            .exchange()
            .expectStatus().isOk
            .expectBody(DispatchOutboundView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(result).succeeded shouldBeEqualTo 1
        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "SUCCEEDED"
        stored.attempts shouldBeEqualTo 1
        stored.lastStatusCode shouldBeEqualTo 202
    }

    @Test
    fun `outbound retryable failure can be retried to success`() = runTest {
        outboundDelivery.enqueue(OutboundDispatchResult(503, "Authorization:=secret-token temporary outage"))
        outboundDelivery.enqueue(OutboundDispatchResult(200))
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-retry", "https://example.test/payments", "payload"))

        repository.dispatchPendingOutbound(outboundDelivery).retryableFailed shouldBeEqualTo 1
        repository.dispatchPendingOutbound(outboundDelivery).succeeded shouldBeEqualTo 1

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "SUCCEEDED"
        stored.attempts shouldBeEqualTo 2
        (stored.lastError?.contains("secret-token") ?: false) shouldBeEqualTo false
    }

    @Test
    fun `outbound retry exhaustion becomes permanent failure with sanitized error`() = runTest {
        repeat(3) {
            outboundDelivery.enqueue(OutboundDispatchResult(503, "token:=secret-token service unavailable"))
        }
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-exhaust", "https://example.test/payments", "payload"))

        repeat(3) {
            repository.dispatchPendingOutbound(outboundDelivery)
        }

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "PERMANENT_FAILED"
        stored.attempts shouldBeEqualTo 3
        stored.lastStatusCode shouldBeEqualTo 503
        (stored.lastError?.contains("secret-token") ?: false) shouldBeEqualTo false
    }

    @Test
    fun `outbound permanent client failure is not retried`() = runTest {
        outboundDelivery.enqueue(OutboundDispatchResult(422, "validation rejected"))
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-permanent", "https://example.test/payments", "payload"))

        repository.dispatchPendingOutbound(outboundDelivery).permanentFailed shouldBeEqualTo 1
        repository.dispatchPendingOutbound(outboundDelivery).attempted shouldBeEqualTo 0

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "PERMANENT_FAILED"
        stored.attempts shouldBeEqualTo 1
    }

    @Test
    fun `outbound timeout is recorded as retryable transport failure`() = runTest {
        val timeoutDelivery = object: OutboundDelivery {
            override suspend fun dispatch(request: exposed.r2dbc.examples.production.spring.app.OutboundRequestView): OutboundDispatchResult =
                awaitCancellation()
        }
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-timeout", "https://example.test/payments", "payload"))

        repository.dispatchPendingOutbound(timeoutDelivery, Duration.ofMillis(10)).retryableFailed shouldBeEqualTo 1

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "RETRYABLE_FAILED"
        stored.lastStatusCode shouldBeEqualTo 599
        stored.lastError?.contains("timeout") shouldBeEqualTo true
    }

    @Test
    fun `outbound list and dispatch require outbound permission`() {
        client.get()
            .uri("/production/outbound")
            .header("X-Api-Key", "alice-api-key")
            .exchange()
            .expectStatus().isForbidden

        client.post()
            .uri("/production/outbound/dispatch")
            .header("X-Api-Key", "alice-api-key")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `concurrent duplicate outbound submit keeps one row`() = runTest {
        val request = EnqueueOutboundRequest("payment-concurrent", "https://example.test/payments", "payload")

        val results = coroutineScope {
            List(2) {
                async {
                    try {
                        repository.enqueueOutbound(request)
                        "created"
                    } catch (e: DuplicateIdempotencyKeyException) {
                        "duplicate"
                    }
                }
            }.awaitAll()
        }

        results.count { it == "created" } shouldBeEqualTo 1
        results.count { it == "duplicate" } shouldBeEqualTo 1
        repository.outboundRequests().size shouldBeEqualTo 1
    }

    @Test
    fun `concurrent duplicate outbound submit across repositories keeps one row`() = runTest {
        val databaseName = "spring_duplicate_${UUID.randomUUID().toString().replace("-", "")}"
        val sharedDatabase = R2dbcDatabase.connect("r2dbc:h2:mem:///$databaseName;DB_CLOSE_DELAY=-1;USER=sa;")
        val firstRepository = SpringProductionRepository(sharedDatabase, BCryptPasswordEncoder())
        val secondRepository = SpringProductionRepository(sharedDatabase, BCryptPasswordEncoder())
        val request = EnqueueOutboundRequest("payment-cross-duplicate", "https://example.test/payments", "payload")
        firstRepository.reset()
        secondRepository.outboundRequests()

        val results = coroutineScope {
            listOf(firstRepository, secondRepository)
                .map { repository ->
                    async {
                        try {
                            repository.enqueueOutbound(request)
                            "created"
                        } catch (e: DuplicateIdempotencyKeyException) {
                            "duplicate"
                        }
                    }
                }
                .awaitAll()
        }

        results.count { it == "created" } shouldBeEqualTo 1
        results.count { it == "duplicate" } shouldBeEqualTo 1
        firstRepository.outboundRequests().size shouldBeEqualTo 1
    }

    @Test
    fun `concurrent outbound dispatch sends one HTTP request`() = runTest {
        outboundDelivery.enqueue(OutboundDispatchResult(200))
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-single-send", "https://example.test/payments", "payload"))

        val results = coroutineScope {
            List(2) {
                async { repository.dispatchPendingOutbound(outboundDelivery).attempted }
            }.awaitAll()
        }

        results.sum() shouldBeEqualTo 1
        outboundDelivery.calls.get() shouldBeEqualTo 1
        repository.outboundRequests().single().status.name shouldBeEqualTo "SUCCEEDED"
    }

    @Test
    fun `concurrent outbound dispatch across repositories sends one HTTP request`() = runTest {
        val databaseName = "spring_outbound_${UUID.randomUUID().toString().replace("-", "")}"
        val sharedDatabase = R2dbcDatabase.connect("r2dbc:h2:mem:///$databaseName;DB_CLOSE_DELAY=-1;USER=sa;")
        val firstRepository = SpringProductionRepository(sharedDatabase, BCryptPasswordEncoder())
        val secondRepository = SpringProductionRepository(sharedDatabase, BCryptPasswordEncoder())
        val delivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(200))
        }
        firstRepository.reset()
        secondRepository.outboundRequests()
        firstRepository.enqueueOutbound(EnqueueOutboundRequest("payment-cross-repo", "https://example.test/payments", "payload"))

        val results = coroutineScope {
            listOf(firstRepository, secondRepository)
                .map { repository -> async { repository.dispatchPendingOutbound(delivery).attempted } }
                .awaitAll()
        }

        results.sum() shouldBeEqualTo 1
        delivery.calls.get() shouldBeEqualTo 1
        firstRepository.outboundRequests().single().status.name shouldBeEqualTo "SUCCEEDED"
    }

    @Test
    fun `invalid outbound target URL returns structured validation error`() {
        val error = client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "admin-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(EnqueueOutboundRequest("payment-2", "ht!tp://example.test/payments", "payload"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(StructuredError::class.java)
            .returnResult()
            .responseBody

        requireNotNull(error).code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `invalid outbound idempotency key returns structured validation error`() {
        val error = client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "admin-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(EnqueueOutboundRequest("payment\r\nInjected: value", "https://example.test/payments", "payload"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(StructuredError::class.java)
            .returnResult()
            .responseBody

        requireNotNull(error).code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `readiness reports degraded database state`() = runTest {
        repository.markDatabaseDegraded("slow query threshold exceeded")

        val readiness = client.get()
            .uri("/production/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody(ReadinessView::class.java)
            .returnResult()
            .responseBody

        requireNotNull(readiness).status shouldBeEqualTo "DEGRADED"
    }

    private fun WebTestClient.RequestHeadersSpec<*>.basic(username: String, password: String) =
        header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray()))

    private object AlwaysDeliveringRealtimeDelivery: RealtimeDelivery {
        override suspend fun deliver(event: OutboxEventView): Boolean = true
    }

    private object FailingRealtimeDelivery: RealtimeDelivery {
        override suspend fun deliver(event: OutboxEventView): Boolean = false
    }

    @TestConfiguration
    class OutboundTestConfig {
        @Bean
        @Primary
        fun outboundDelivery(): RecordingOutboundDelivery =
            RecordingOutboundDelivery()
    }
}

class RecordingOutboundDelivery: OutboundDelivery {
    val calls = AtomicInteger(0)
    private val results = ConcurrentLinkedQueue<OutboundDispatchResult>()

    fun enqueue(result: OutboundDispatchResult) {
        results.add(result)
    }

    fun reset() {
        calls.set(0)
        results.clear()
    }

    override suspend fun dispatch(request: exposed.r2dbc.examples.production.spring.app.OutboundRequestView): OutboundDispatchResult {
        calls.incrementAndGet()
        return results.poll() ?: OutboundDispatchResult(200)
    }
}
