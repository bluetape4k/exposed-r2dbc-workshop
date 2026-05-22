package exposed.r2dbc.examples.production.ktor

import exposed.r2dbc.examples.production.ktor.app.AccountView
import exposed.r2dbc.examples.production.ktor.app.AuthProfileView
import exposed.r2dbc.examples.production.ktor.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.ktor.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.OutboxEventsView
import exposed.r2dbc.examples.production.ktor.app.OutboxEventView
import exposed.r2dbc.examples.production.ktor.app.OutboxStatus
import exposed.r2dbc.examples.production.ktor.app.OutboundRequestView
import exposed.r2dbc.examples.production.ktor.app.PublishOutboxView
import exposed.r2dbc.examples.production.ktor.app.ReadinessView
import exposed.r2dbc.examples.production.ktor.app.RealtimeDelivery
import exposed.r2dbc.examples.production.ktor.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.ktor.app.SessionView
import exposed.r2dbc.examples.production.ktor.app.SessionsView
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import exposed.r2dbc.examples.production.ktor.app.WorkItemView
import exposed.r2dbc.examples.production.ktor.outbound.KtorOutboundDispatcher
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Base64
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorProductionIntegrationApplicationTest {

    @Test
    fun `register account and authorize session-bound work item`() = testApplication {
        val repository = newRepository("auth")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val account = client.post("/production/accounts") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("operator", "ktor-key", "work:create"))
        }.body<AccountView>()

        account.username shouldBeEqualTo "operator"
        account.roles shouldBeEqualTo setOf("USER")
        repository.hasPermission("ktor-key", "work:create") shouldBeEqualTo true

        client.post("/production/sessions") {
            basic("operator", "password")
        }.status shouldBeEqualTo HttpStatusCode.OK

        val workItem = client.post("/production/work-items") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkItemRequest("operator", "ship order"))
        }.body<WorkItemView>()

        workItem.status shouldBeEqualTo "ACCEPTED"
    }

    @Test
    fun `missing and invalid basic credentials are rejected`() = testApplication {
        val repository = newRepository("basic-auth")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        client.get("/production/profile").status shouldBeEqualTo HttpStatusCode.Unauthorized
        client.get("/production/profile") {
            basic("alice", "wrong")
        }.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }

    @Test
    fun `basic credentials expose profile and deny non-admin access`() = testApplication {
        val repository = newRepository("roles")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val profile = client.get("/production/profile") {
            basic("alice", "password")
        }.body<AuthProfileView>()

        profile.roles shouldBeEqualTo setOf("USER")

        client.get("/production/admin") {
            basic("alice", "password")
        }.status shouldBeEqualTo HttpStatusCode.Forbidden
    }

    @Test
    fun `admin credentials access protected admin endpoint`() = testApplication {
        val repository = newRepository("admin")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val profile = client.get("/production/admin") {
            basic("admin", "password")
        }.body<AuthProfileView>()

        profile.roles shouldBeEqualTo setOf("ADMIN", "USER")
    }

    @Test
    fun `public registration cannot grant admin role`() = testApplication {
        val repository = newRepository("self-admin")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val account = client.post("/production/accounts") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterAccountRequest(
                    username = "self-admin",
                    apiKey = "self-admin-key",
                    permission = "outbound:create",
                    password = "password",
                    displayName = "Self Admin",
                    roles = setOf("ADMIN", "USER"),
                )
            )
        }.body<AccountView>()

        account.roles shouldBeEqualTo setOf("USER")
        account.permission shouldBeEqualTo "work:create"

        client.get("/production/admin") {
            basic("self-admin", "password")
        }.status shouldBeEqualTo HttpStatusCode.Forbidden
    }

    @Test
    fun `authenticated users persist session metadata without listing raw tokens`() = testApplication {
        val repository = newRepository("sessions")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val created = client.post("/production/sessions") {
            basic("alice", "password")
        }.body<SessionView>()

        created.username shouldBeEqualTo "alice"
        requireNotNull(created.token)
        (created.expiresAtEpochMs > created.issuedAtEpochMs) shouldBeEqualTo true

        val sessions = client.get("/production/sessions") {
            basic("alice", "password")
        }.body<SessionsView>()
        val listed = sessions.sessions.single { it.username == "alice" }
        listed.token shouldBeEqualTo null
    }

    @Test
    fun `websocket realtime endpoint replays persisted outbox event`() = testApplication {
        val repository = newRepository("realtime")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(HttpCookies)
            install(WebSockets)
        }

        client.post("/production/sessions") {
            basic("alice", "password")
        }
        client.post("/production/work-items") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkItemRequest("alice", "ship order"))
        }
        repository.outboxEvents().single().status shouldBeEqualTo OutboxStatus.PENDING
        client.post("/production/outbox/publish").body<PublishOutboxView>().delivered shouldBeEqualTo 1

        client.webSocket("/production/realtime?after=0") {
            val text = (incoming.receive() as Frame.Text).readText()
            text.contains("work-item.accepted") shouldBeEqualTo true
        }

        repository.replayEvents(0).size shouldBeGreaterThan 0
    }

    @Test
    fun `websocket realtime endpoint streams live event after subscription`() = testApplication {
        val repository = newRepository("realtime-live")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(HttpCookies)
            install(WebSockets)
        }

        client.post("/production/sessions") {
            basic("alice", "password")
        }

        client.webSocket("/production/realtime?after=0") {
            client.post("/production/work-items") {
                contentType(ContentType.Application.Json)
                setBody(CreateWorkItemRequest("alice", "live update"))
            }
            client.post("/production/outbox/publish")

            val text = withTimeout(5_000) {
                (incoming.receive() as Frame.Text).readText()
            }
            text.contains("live update") shouldBeEqualTo true
        }
    }

    @Test
    fun `delivery failure is recorded without losing realtime outbox event`() = testApplication {
        val repository = newRepository("realtime-failure")
        repository.reset()
        application {
            productionIntegrationModule(repository, realtimeDelivery = FailingRealtimeDelivery)
        }
        val client = createJsonClient()

        client.post("/production/sessions") {
            basic("alice", "password")
        }
        client.post("/production/work-items") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkItemRequest("alice", "will fail"))
        }

        client.post("/production/outbox/publish").status shouldBeEqualTo HttpStatusCode.OK
        val outbox = client.get("/production/outbox").body<OutboxEventsView>()
        val failed = outbox.events.single()
        failed.status shouldBeEqualTo OutboxStatus.FAILED
        failed.lastError shouldBeEqualTo "realtime delivery returned false"
    }

    @Test
    fun `blank work item request returns structured validation error`() = testApplication {
        val repository = newRepository("validation")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        client.post("/production/sessions") {
            basic("alice", "password")
        }

        val error = client.post("/production/work-items") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkItemRequest(" ", "payload"))
        }

        error.status shouldBeEqualTo HttpStatusCode.BadRequest
        error.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `malformed JSON returns structured parse error`() = testApplication {
        val repository = newRepository("malformed")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val error = client.post("/production/accounts") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        error.status shouldBeEqualTo HttpStatusCode.BadRequest
        error.body<StructuredError>().code shouldBeEqualTo "INVALID_JSON"
    }

    @Test
    fun `work item without session returns structured unauthorized error`() = testApplication {
        val repository = newRepository("unauthorized")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        val error = client.post("/production/work-items") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkItemRequest("alice", "ship order"))
        }

        error.status shouldBeEqualTo HttpStatusCode.Unauthorized
        error.body<StructuredError>().code shouldBeEqualTo "UNAUTHORIZED"
    }

    @Test
    fun `duplicate idempotency key returns conflict`() = testApplication {
        val repository = newRepository("outbound")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()
        val request = EnqueueOutboundRequest("payment-1", "https://example.test/payments", "payload")

        client.post("/production/sessions") {
            basic("admin", "password")
        }

        client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status shouldBeEqualTo HttpStatusCode.OK

        val duplicate = client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        duplicate.status shouldBeEqualTo HttpStatusCode.Conflict
        duplicate.body<StructuredError>().code shouldBeEqualTo "IDEMPOTENCY_CONFLICT"
    }

    @Test
    fun `session without outbound permission is denied`() = testApplication {
        val repository = newRepository("outbound-denied")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        client.post("/production/sessions") {
            basic("alice", "password")
        }.status shouldBeEqualTo HttpStatusCode.OK

        val denied = client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(EnqueueOutboundRequest("payment-denied", "https://example.test/payments", "payload"))
        }

        denied.status shouldBeEqualTo HttpStatusCode.Forbidden
        denied.body<StructuredError>().code shouldBeEqualTo "FORBIDDEN"
    }

    @Test
    fun `invalid outbound target URL returns structured validation error`() = testApplication {
        val repository = newRepository("invalid-target")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        client.post("/production/sessions") {
            basic("admin", "password")
        }

        val error = client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(EnqueueOutboundRequest("payment-2", "ht!tp://example.test/payments", "payload"))
        }

        error.status shouldBeEqualTo HttpStatusCode.BadRequest
        error.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `readiness reports degraded database state`() = testApplication {
        val repository = newRepository("diagnostics")
        repository.reset()
        repository.markDatabaseDegraded("slow query threshold exceeded")
        application {
            productionIntegrationModule(repository)
        }
        val client = createJsonClient()

        client.post("/production/accounts") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("ops", "ops-key", "readiness:read"))
        }

        val readiness = client.get("/production/readiness").body<ReadinessView>()
        readiness.status shouldBeEqualTo "DEGRADED"
    }

    @Test
    fun `outbound dispatcher uses Ktor mock engine`() = kotlinx.coroutines.test.runTest {
        val dispatcherClient = HttpClient(MockEngine { respondOk("accepted") })
        val dispatcher = KtorOutboundDispatcher(dispatcherClient)

        val status = dispatcher.dispatch(
            OutboundRequestView(
                id = "out-1",
                idempotencyKey = "payment-1",
                targetUrl = "https://example.test/payments",
                payload = "payload",
                status = "PENDING",
            )
        )

        status shouldBeEqualTo HttpStatusCode.OK
        dispatcherClient.close()
    }

    private fun newRepository(slug: String): KtorProductionRepository {
        val databaseName = "ktor_${slug}_${UUID.randomUUID().toString().replace("-", "")}"
        return KtorProductionRepository(R2dbcDatabase.connect("r2dbc:h2:mem:///$databaseName;DB_CLOSE_DELAY=-1;USER=sa;"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createJsonClient() =
        createClient {
            install(ContentNegotiation) {
                json()
            }
            install(HttpCookies)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.basic(username: String, password: String) {
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        header(HttpHeaders.Authorization, "Basic $encoded")
    }

    private object FailingRealtimeDelivery: RealtimeDelivery {
        override suspend fun deliver(event: OutboxEventView): Boolean = false
    }
}
