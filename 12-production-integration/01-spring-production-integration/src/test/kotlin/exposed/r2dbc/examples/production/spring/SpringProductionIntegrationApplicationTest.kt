package exposed.r2dbc.examples.production.spring

import exposed.r2dbc.examples.production.spring.app.AccountView
import exposed.r2dbc.examples.production.spring.app.AuthProfileView
import exposed.r2dbc.examples.production.spring.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.spring.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.spring.app.OutboxEventView
import exposed.r2dbc.examples.production.spring.app.OutboxStatus
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.Base64
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringProductionIntegrationApplicationTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val repository: SpringProductionRepository,
) {

    @BeforeEach
    fun resetDatabase() = runTest {
        repository.reset()
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
        val failingService = SpringProductionWorkflowService(repository, FailingRealtimeDelivery)
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
}
