package exposed.r2dbc.examples.production.spring

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
            .bodyValue(RegisterAccountRequest("alice", "spring-key", "work:create"))
            .exchange()
            .expectStatus().isOk
            .expectBody(AccountView::class.java)
            .returnResult()
            .responseBody

        val account = requireNotNull(response)
        account.username shouldBeEqualTo "alice"
        repository.hasPermission("spring-key", "work:create") shouldBeEqualTo true
    }

    @Test
    fun `create work item persists realtime event and supports SSE replay`() = runTest {
        client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterAccountRequest("alice", "spring-key", "work:create"))
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
    fun `duplicate idempotency key returns conflict`() {
        val request = EnqueueOutboundRequest("payment-1", "https://example.test/payments", "payload")

        client.post()
            .uri("/production/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterAccountRequest("outbound", "outbound-key", "outbound:create"))
            .exchange()
            .expectStatus().isOk

        client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "outbound-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk

        val error = client.post()
            .uri("/production/outbound")
            .header("X-Api-Key", "outbound-key")
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
}
