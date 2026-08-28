package exposed.r2dbc.examples.production.ktor

import exposed.r2dbc.examples.production.ktor.app.AccountView
import exposed.r2dbc.examples.production.ktor.app.AuthProfileView
import exposed.r2dbc.examples.production.ktor.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.ktor.app.DiagnosticOperationView
import exposed.r2dbc.examples.production.ktor.app.DiagnosticOperationsView
import exposed.r2dbc.examples.production.ktor.app.DispatchOutboundView
import exposed.r2dbc.examples.production.ktor.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.ktor.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.OutboxEventsView
import exposed.r2dbc.examples.production.ktor.app.OutboxEventView
import exposed.r2dbc.examples.production.ktor.app.OutboxStatus
import exposed.r2dbc.examples.production.ktor.app.OutboundDelivery
import exposed.r2dbc.examples.production.ktor.app.OutboundDispatchResult
import exposed.r2dbc.examples.production.ktor.app.OutboundRequestView
import exposed.r2dbc.examples.production.ktor.app.PublishOutboxView
import exposed.r2dbc.examples.production.ktor.app.ReadinessView
import exposed.r2dbc.examples.production.ktor.app.RealtimeDelivery
import exposed.r2dbc.examples.production.ktor.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.ktor.app.SessionView
import exposed.r2dbc.examples.production.ktor.app.SessionsView
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import exposed.r2dbc.examples.production.ktor.app.WorkItemView
import exposed.r2dbc.examples.production.ktor.config.REQUEST_ID_HEADER
import exposed.r2dbc.examples.production.ktor.outbound.KtorOutboundDispatcher
import exposed.r2dbc.examples.production.ktor.outbound.defaultOutboundClient
import exposed.r2dbc.examples.production.ktor.outbound.defaultOutboundClientJson
import exposed.r2dbc.examples.production.ktor.outbound.defaultOutboundClientTimeouts
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorProductionIntegrationApplicationTest {

    @Test
    fun `register account and authorize session-bound work item`() = testApplication {
        val repository = newRepository("auth")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })
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
    fun `outbound dispatch marks success after persisted enqueue`() = testApplication {
        val repository = newRepository("outbound-success")
        val outboundDelivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(202))
        }
        repository.reset()
        application {
            productionIntegrationModule(repository, outboundDelivery = outboundDelivery)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        client.post("/production/sessions") {
            basic("admin", "password")
        }
        client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(EnqueueOutboundRequest("payment-success", "https://example.test/payments", "payload"))
        }.status shouldBeEqualTo HttpStatusCode.OK

        val result = client.post("/production/outbound/dispatch").body<DispatchOutboundView>()

        result.succeeded shouldBeEqualTo 1
        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "SUCCEEDED"
        stored.attempts shouldBeEqualTo 1
        stored.lastStatusCode shouldBeEqualTo 202
    }

    @Test
    fun `outbound retryable failure can be retried to success`() = testApplication {
        val repository = newRepository("outbound-retry")
        val outboundDelivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(503, "Authorization:=secret-token temporary outage"))
            it.enqueue(OutboundDispatchResult(200))
        }
        repository.reset()
        application {
            productionIntegrationModule(repository, outboundDelivery = outboundDelivery)
        }

        repository.enqueueOutbound(EnqueueOutboundRequest("payment-retry", "https://example.test/payments", "payload"))
        repository.dispatchPendingOutbound(outboundDelivery).retryableFailed shouldBeEqualTo 1
        repository.dispatchPendingOutbound(outboundDelivery).succeeded shouldBeEqualTo 1

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "SUCCEEDED"
        stored.attempts shouldBeEqualTo 2
        (stored.lastError?.contains("secret-token") ?: false) shouldBeEqualTo false
    }

    @Test
    fun `outbound retry exhaustion becomes permanent failure with sanitized error`() = testApplication {
        val repository = newRepository("outbound-exhaust")
        val outboundDelivery = RecordingOutboundDelivery().also { delivery ->
            repeat(3) {
                delivery.enqueue(OutboundDispatchResult(503, "token:=secret-token service unavailable"))
            }
        }
        repository.reset()
        application {
            productionIntegrationModule(repository, outboundDelivery = outboundDelivery)
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
    fun `outbound permanent client failure is not retried`() = testApplication {
        val repository = newRepository("outbound-permanent")
        val outboundDelivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(422, "validation rejected"))
        }
        repository.reset()
        application {
            productionIntegrationModule(repository, outboundDelivery = outboundDelivery)
        }

        repository.enqueueOutbound(EnqueueOutboundRequest("payment-permanent", "https://example.test/payments", "payload"))

        repository.dispatchPendingOutbound(outboundDelivery).permanentFailed shouldBeEqualTo 1
        repository.dispatchPendingOutbound(outboundDelivery).attempted shouldBeEqualTo 0
        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "PERMANENT_FAILED"
        stored.attempts shouldBeEqualTo 1
    }

    @Test
    fun `outbound timeout is recorded as retryable transport failure`() = kotlinx.coroutines.test.runTest {
        val repository = newRepository("outbound-timeout")
        val timeoutDelivery = object: OutboundDelivery {
            override suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult =
                awaitCancellation()
        }
        repository.reset()
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-timeout", "https://example.test/payments", "payload"))

        repository.dispatchPendingOutbound(timeoutDelivery, Duration.ofMillis(10)).retryableFailed shouldBeEqualTo 1

        val stored = repository.outboundRequests().single()
        stored.status.name shouldBeEqualTo "RETRYABLE_FAILED"
        stored.lastStatusCode shouldBeEqualTo 599
        stored.lastError?.contains("timeout") shouldBeEqualTo true
    }

    @Test
    fun `session without outbound permission is denied`() = testApplication {
        val repository = newRepository("outbound-denied")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        client.post("/production/sessions") {
            basic("alice", "password")
        }.status shouldBeEqualTo HttpStatusCode.OK

        val denied = client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(EnqueueOutboundRequest("payment-denied", "https://example.test/payments", "payload"))
        }

        denied.status shouldBeEqualTo HttpStatusCode.Forbidden
        denied.body<StructuredError>().code shouldBeEqualTo "FORBIDDEN"

        client.get("/production/outbound").status shouldBeEqualTo HttpStatusCode.Forbidden
        client.post("/production/outbound/dispatch").status shouldBeEqualTo HttpStatusCode.Forbidden
    }

    @Test
    fun `invalid outbound target URL returns structured validation error`() = testApplication {
        val repository = newRepository("invalid-target")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

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
    fun `invalid outbound idempotency key returns structured validation error`() = testApplication {
        val repository = newRepository("invalid-idempotency-key")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        client.post("/production/sessions") {
            basic("admin", "password")
        }

        val error = client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(EnqueueOutboundRequest("payment\r\nInjected: value", "https://example.test/payments", "payload"))
        }

        error.status shouldBeEqualTo HttpStatusCode.BadRequest
        error.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `readiness reports up degraded and recovered database state with request id`() = testApplication {
        val repository = newRepository("diagnostics")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        val up = client.get("/production/readiness") {
            header(REQUEST_ID_HEADER, "ktor-ready-up")
        }.body<ReadinessView>()
        up.status shouldBeEqualTo "UP"
        up.requestId shouldBeEqualTo "ktor-ready-up"

        repository.markDatabaseDegraded("slow query threshold exceeded")

        val degraded = client.get("/production/readiness") {
            header(REQUEST_ID_HEADER, "ktor-ready-degraded")
        }.body<ReadinessView>()
        degraded.status shouldBeEqualTo "DEGRADED"
        degraded.requestId shouldBeEqualTo "ktor-ready-degraded"

        repository.clearDatabaseDegraded()

        client.get("/production/readiness").body<ReadinessView>().status shouldBeEqualTo "UP"
    }

    @Test
    fun `diagnostic operation records slow timing and request id`() = testApplication {
        val repository = newRepository("diagnostic-operation")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        val operation = client.get("/production/diagnostics/operations/import-orders?delayMs=260") {
            header(REQUEST_ID_HEADER, "ktor-diagnostic-1")
        }.body<DiagnosticOperationView>()

        operation.name shouldBeEqualTo "import-orders"
        operation.requestId shouldBeEqualTo "ktor-diagnostic-1"
        operation.slow shouldBeEqualTo true

        val operations = client.get("/production/diagnostics/operations").body<DiagnosticOperationsView>()
        operations.operations.any { it.id == operation.id } shouldBeEqualTo true
    }

    @Test
    fun `invalid request id is replaced before diagnostic persistence`() = testApplication {
        val repository = newRepository("diagnostic-request-id")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        val response = client.get("/production/diagnostics/operations/import") {
            header(REQUEST_ID_HEADER, "ktor trace with spaces")
        }

        val sanitized = response.headers[REQUEST_ID_HEADER].shouldNotBeNull()
        sanitized shouldNotBeEqualTo "ktor trace with spaces"
        response.body<DiagnosticOperationView>().requestId shouldBeEqualTo sanitized
    }

    @Test
    fun `diagnostic validation errors include request id`() = testApplication {
        val repository = newRepository("diagnostic-validation")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })

        val delayError = client.get("/production/diagnostics/operations/import?delayMs=2001") {
            header(REQUEST_ID_HEADER, "ktor-diagnostic-2")
        }
        delayError.status shouldBeEqualTo HttpStatusCode.BadRequest
        delayError.body<StructuredError>().requestId shouldBeEqualTo "ktor-diagnostic-2"

        val nameError = client.get("/production/diagnostics/operations/Import") {
            header(REQUEST_ID_HEADER, "ktor-diagnostic-3")
        }
        nameError.status shouldBeEqualTo HttpStatusCode.BadRequest
        nameError.body<StructuredError>().requestId shouldBeEqualTo "ktor-diagnostic-3"
    }

    @Test
    fun `structured permission and conflict errors include request id`() = testApplication {
        val repository = newRepository("diagnostic-errors")
        repository.reset()
        application {
            productionIntegrationModule(repository)
        }
        val client = bluetape4kJsonClient(configure = { install(HttpCookies) })
        client.post("/production/sessions") {
            basic("alice", "password")
        }

        val forbidden = client.get("/production/outbound") {
            header(REQUEST_ID_HEADER, "ktor-error-1")
        }
        forbidden.status shouldBeEqualTo HttpStatusCode.Forbidden
        forbidden.body<StructuredError>().requestId shouldBeEqualTo "ktor-error-1"

        client.post("/production/sessions") {
            basic("admin", "password")
        }
        val request = EnqueueOutboundRequest("payment-request-id", "https://example.test/payments", "payload")
        client.post("/production/outbound") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status shouldBeEqualTo HttpStatusCode.OK

        val conflict = client.post("/production/outbound") {
            header(REQUEST_ID_HEADER, "ktor-error-2")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        conflict.status shouldBeEqualTo HttpStatusCode.Conflict
        conflict.body<StructuredError>().requestId shouldBeEqualTo "ktor-error-2"
    }

    @Test
    fun `outbound dispatcher uses Ktor mock engine`() = kotlinx.coroutines.test.runTest {
        val dispatcherClient = HttpClient(MockEngine { request ->
            request.headers["Idempotency-Key"] shouldBeEqualTo "payment-1"
            respondOk("accepted")
        })
        val dispatcher = KtorOutboundDispatcher(dispatcherClient)

        val result = dispatcher.dispatch(
            OutboundRequestView(
                id = "out-1",
                idempotencyKey = "payment-1",
                targetUrl = "https://example.test/payments",
                payload = "payload",
                status = exposed.r2dbc.examples.production.ktor.app.OutboundStatus.PENDING,
                attempts = 0,
                lastStatusCode = null,
                lastError = null,
            )
        )

        result.statusCode shouldBeEqualTo HttpStatusCode.OK.value
        dispatcher.close()
    }

    @Test
    fun `default outbound client keeps explicit JSON and timeout contract`() {
        val client = defaultOutboundClient()
        try {
            client.plugin(ContentNegotiation).shouldNotBeNull()
            client.plugin(HttpTimeout).shouldNotBeNull()
            defaultOutboundClientJson.configuration.ignoreUnknownKeys shouldBeEqualTo true
            defaultOutboundClientJson.configuration.encodeDefaults shouldBeEqualTo true
            defaultOutboundClientJson.configuration.explicitNulls shouldBeEqualTo true
            defaultOutboundClientTimeouts.requestTimeout shouldBeEqualTo Duration.ofSeconds(30)
            defaultOutboundClientTimeouts.connectTimeout shouldBeEqualTo Duration.ofSeconds(10)
            defaultOutboundClientTimeouts.socketTimeout shouldBeEqualTo Duration.ofSeconds(30)
        } finally {
            client.close()
        }
    }

    @Test
    fun `dispatcher closes its default client`() {
        KtorOutboundDispatcher().close()
    }

    @Test
    fun `concurrent duplicate outbound submit keeps one row`() = kotlinx.coroutines.test.runTest {
        val repository = newRepository("outbound-concurrent")
        repository.reset()
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
    fun `concurrent duplicate outbound submit across repositories keeps one row`() = kotlinx.coroutines.test.runTest {
        val databaseName = "ktor_duplicate_${UUID.randomUUID().toString().replace("-", "")}"
        val firstRepository = repositoryForDatabase(databaseName)
        val secondRepository = repositoryForDatabase(databaseName)
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
    fun `concurrent outbound dispatch sends one HTTP request`() = kotlinx.coroutines.test.runTest {
        val repository = newRepository("outbound-single-send")
        val outboundDelivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(200))
        }
        repository.reset()
        repository.enqueueOutbound(EnqueueOutboundRequest("payment-single-send", "https://example.test/payments", "payload"))

        val results = coroutineScope {
            List(2) {
                async { repository.dispatchPendingOutbound(outboundDelivery).attempted }
            }.awaitAll()
        }

        results.sum() shouldBeEqualTo 1
        outboundDelivery.calls shouldBeEqualTo 1
        repository.outboundRequests().single().status.name shouldBeEqualTo "SUCCEEDED"
    }

    @Test
    fun `concurrent outbound dispatch across repositories sends one HTTP request`() = kotlinx.coroutines.test.runTest {
        val databaseName = "ktor_outbound_${UUID.randomUUID().toString().replace("-", "")}"
        val firstRepository = repositoryForDatabase(databaseName)
        val secondRepository = repositoryForDatabase(databaseName)
        val outboundDelivery = RecordingOutboundDelivery().also {
            it.enqueue(OutboundDispatchResult(200))
        }
        firstRepository.reset()
        secondRepository.outboundRequests()
        firstRepository.enqueueOutbound(EnqueueOutboundRequest("payment-cross-repo", "https://example.test/payments", "payload"))

        val results = coroutineScope {
            listOf(firstRepository, secondRepository)
                .map { repository -> async { repository.dispatchPendingOutbound(outboundDelivery).attempted } }
                .awaitAll()
        }

        results.sum() shouldBeEqualTo 1
        outboundDelivery.calls shouldBeEqualTo 1
        firstRepository.outboundRequests().single().status.name shouldBeEqualTo "SUCCEEDED"
    }

    private fun newRepository(slug: String): KtorProductionRepository {
        val databaseName = "ktor_${slug}_${UUID.randomUUID().toString().replace("-", "")}"
        return repositoryForDatabase(databaseName)
    }

    private fun repositoryForDatabase(databaseName: String): KtorProductionRepository {
        return KtorProductionRepository(R2dbcDatabase.connect("r2dbc:h2:mem:///$databaseName;DB_CLOSE_DELAY=-1;USER=sa;"))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.basic(username: String, password: String) {
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        header(HttpHeaders.Authorization, "Basic $encoded")
    }

    private object FailingRealtimeDelivery: RealtimeDelivery {
        override suspend fun deliver(event: OutboxEventView): Boolean = false
    }
}

private class RecordingOutboundDelivery: OutboundDelivery {
    private val callCounter = AtomicInteger(0)
    val calls: Int
        get() = callCounter.get()

    private val results = ConcurrentLinkedQueue<OutboundDispatchResult>()

    fun enqueue(result: OutboundDispatchResult) {
        results.add(result)
    }

    override suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult {
        callCounter.incrementAndGet()
        return results.poll() ?: OutboundDispatchResult(200)
    }
}
