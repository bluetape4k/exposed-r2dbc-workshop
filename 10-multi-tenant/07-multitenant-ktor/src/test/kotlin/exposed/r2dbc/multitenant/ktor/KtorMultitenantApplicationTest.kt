package exposed.r2dbc.multitenant.ktor

import exposed.r2dbc.multitenant.ktor.config.KtorMultitenantDatabase
import exposed.r2dbc.multitenant.ktor.config.KtorMultitenantJson
import exposed.r2dbc.multitenant.ktor.config.installKtorMultitenantPlugins
import exposed.r2dbc.multitenant.ktor.domain.model.ActorRecord
import exposed.r2dbc.multitenant.ktor.domain.model.CreateActorRequest
import exposed.r2dbc.multitenant.ktor.domain.model.StructuredError
import exposed.r2dbc.multitenant.ktor.tenant.TenantHeader
import exposed.r2dbc.multitenant.ktor.tenant.currentTenant
import exposed.r2dbc.multitenant.ktor.tenant.Tenants.Tenant
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.ktor.tenant.KtorTenantContext
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.bluetape4k.ktor.tenant.TenantAlreadyBoundException
import io.bluetape4k.tenant.TenantId
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorMultitenantApplicationTest {

    companion object: KLoggingChannel() {
        const val TRACE_REQUEST_COUNT = 2
        const val TRACE_DISPATCHER = "ktor-tenant-fixture-reused"
    }

    @Test
    fun `deterministic ktor tenant fixture preserves provider tenant across dispatcher hops`() = testApplication {
        val traces = ConcurrentHashMap<String, List<KtorTenantTrace>>()
        val ready = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val arrivals = AtomicInteger()
        val traceDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, TRACE_DISPATCHER)
        }.asCoroutineDispatcher()

        try {
            application {
                installKtorMultitenantPlugins()
                routing {
                    get("/tenant-trace/{traceId}") {
                        val traceId = call.parameters["traceId"]
                            ?: error("fixture trace id is missing")
                        val expectedTenant = call.currentTenant()
                        val recorder = KtorTenantTraceRecorder { trace -> log.info { trace.toLogLine() } }
                        recorder.record(
                            expectedTenant = expectedTenant.id,
                            observedTenant = call.currentTenant().id,
                            subscriptionId = "ktor-$traceId",
                            dispatcher = "ktor-call",
                            phase = TracePhase.START,
                        )

                        if (arrivals.incrementAndGet() == TRACE_REQUEST_COUNT) {
                            ready.complete(Unit)
                        }
                        ready.await()
                        release.await()

                        withContext(traceDispatcher) {
                            recorder.record(
                                expectedTenant = expectedTenant.id,
                                observedTenant = call.currentTenant().id,
                                subscriptionId = "ktor-$traceId",
                                dispatcher = TRACE_DISPATCHER,
                                phase = TracePhase.RESUME,
                            )

                            recorder.record(
                                expectedTenant = expectedTenant.id,
                                observedTenant = call.currentTenant().id,
                                subscriptionId = "ktor-$traceId",
                                dispatcher = TRACE_DISPATCHER,
                                phase = TracePhase.NESTED_ENTER,
                            )
                            try {
                                if (traceId.endsWith("-failure")) {
                                    KtorTenantContext.bindTenant(call, TenantId(expectedTenant.other().id))
                                } else {
                                    recorder.record(
                                        expectedTenant = expectedTenant.id,
                                        observedTenant = call.currentTenant().id,
                                        subscriptionId = "ktor-$traceId",
                                        dispatcher = TRACE_DISPATCHER,
                                        phase = TracePhase.NESTED_SUCCESS,
                                    )
                                }
                            } catch (_: TenantAlreadyBoundException) {
                                recorder.record(
                                    expectedTenant = expectedTenant.id,
                                    observedTenant = call.currentTenant().id,
                                    subscriptionId = "ktor-$traceId",
                                    dispatcher = TRACE_DISPATCHER,
                                    phase = TracePhase.NESTED_FAILURE,
                                )
                            }

                            recorder.record(
                                expectedTenant = expectedTenant.id,
                                observedTenant = call.currentTenant().id,
                                subscriptionId = "ktor-$traceId",
                                dispatcher = TRACE_DISPATCHER,
                                phase = TracePhase.COMPLETE,
                            )
                        }
                        traces[traceId] = recorder.events.toList()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val client = createClient {}
            val expectedByTrace = mapOf(
                "korean-success" to Tenant.KOREAN,
                "english-failure" to Tenant.ENGLISH,
            )
            coroutineScope {
                val requests = expectedByTrace.map { (traceId, tenant) ->
                    async {
                        client.get("/tenant-trace/$traceId") {
                            header(TenantHeader, tenant.id)
                        }.status shouldBeEqualTo HttpStatusCode.OK
                    }
                }
                ready.await()
                release.complete(Unit)
                requests.awaitAll()
            }

            expectedByTrace.forEach { (traceId, expectedTenant) ->
                val events = traces[traceId] ?: error("missing fixture trace for $traceId")
                events shouldHaveSize 5
                events
                    .filter { it.phase !in setOf(TracePhase.NESTED_ENTER, TracePhase.NESTED_SUCCESS) }
                    .forEach { it.observedTenant shouldBeEqualTo expectedTenant.id }
                events
                    .filter { it.phase in setOf(TracePhase.NESTED_ENTER, TracePhase.NESTED_SUCCESS) }
                    .forEach { it.observedTenant shouldBeEqualTo expectedTenant.id }
                events.map { it.phase }.toSet() shouldBeEqualTo setOf(
                    TracePhase.START,
                    TracePhase.RESUME,
                    TracePhase.NESTED_ENTER,
                    if (traceId.endsWith("-failure")) TracePhase.NESTED_FAILURE else TracePhase.NESTED_SUCCESS,
                    TracePhase.COMPLETE,
                )
            }

            expectedByTrace
                .map { (traceId, _) -> traces[traceId].orEmpty().single { it.phase == TracePhase.RESUME }.threadId }
                .toSet() shouldHaveSize 1

            client.get("/tenant-trace/after-request").status shouldBeEqualTo HttpStatusCode.BadRequest
        } finally {
            traceDispatcher.close()
        }
    }

    @Test
    fun `get all actors by tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("read-all"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        Tenant.entries.forEach { tenant ->
            val actors = client.get("/actors") {
                header(TenantHeader, tenant.id)
            }.body<List<ActorRecord>>()

            actors shouldHaveSize 9
            val expectedFirstName = when (tenant) {
                Tenant.KOREAN -> "조니"
                Tenant.ENGLISH -> "Johnny"
            }
            actors.any { it.firstName == expectedFirstName }.shouldBeTrue()
        }
    }

    @Test
    fun `get actor by id keeps tenant data isolated`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("read-one"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val koreanActor = client.get("/actors/2") {
            header(TenantHeader, Tenant.KOREAN.id)
        }.body<ActorRecord>()
        val englishActor = client.get("/actors/2") {
            header(TenantHeader, Tenant.ENGLISH.id)
        }.body<ActorRecord>()

        koreanActor.id shouldBeEqualTo englishActor.id
        koreanActor.firstName shouldBeEqualTo "브래드"
        koreanActor.lastName shouldBeEqualTo "피트"
        englishActor.firstName shouldBeEqualTo "Brad"
        englishActor.lastName shouldBeEqualTo "Pitt"
    }

    @Test
    fun `lowercase tenant header resolves tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("lowercase"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val actors = client.get("/actors") {
            header("x-tenant-id", Tenant.KOREAN.id)
        }.body<List<ActorRecord>>()

        actors shouldHaveSize 9
    }

    @Test
    fun `invalid tenant headers return structured 400`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("invalid"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val missing = client.get("/actors")
        missing.status shouldBeEqualTo HttpStatusCode.BadRequest
        missing.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val empty = client.get("/actors") {
            header(TenantHeader, "")
        }
        empty.status shouldBeEqualTo HttpStatusCode.BadRequest
        empty.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val whitespace = client.get("/actors") {
            header(TenantHeader, "   ")
        }
        whitespace.status shouldBeEqualTo HttpStatusCode.BadRequest
        whitespace.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val unknown = client.get("/actors") {
            header(TenantHeader, "unknown-tenant")
        }
        unknown.status shouldBeEqualTo HttpStatusCode.BadRequest
        val error = unknown.body<StructuredError>()
        error.code shouldBeEqualTo "INVALID_TENANT"
        error.message shouldContain "Unknown tenant id"
    }

    @Test
    fun `conflicting duplicate tenant headers are rejected`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("duplicate"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val same = client.get("/actors") {
            headers {
                append(TenantHeader, "korean")
                append(TenantHeader, " korean ")
            }
        }
        same.status shouldBeEqualTo HttpStatusCode.OK

        val conflicting = client.get("/actors") {
            headers {
                append(TenantHeader, "korean")
                append(TenantHeader, "english")
            }
        }
        conflicting.status shouldBeEqualTo HttpStatusCode.BadRequest
        conflicting.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"
    }

    @Test
    fun `write in one tenant is not visible from another tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("write-isolation"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val created = client.post("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
            contentType(ContentType.Application.Json)
            setBody(CreateActorRequest("테스트", "배우", "2000-01-01"))
        }.body<ActorRecord>()

        created.id shouldBeGreaterThan 9L
        client.get("/actors/${created.id}") {
            header(TenantHeader, Tenant.ENGLISH.id)
        }.status shouldBeEqualTo HttpStatusCode.NotFound

        val koreanActors = client.get("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
        }.body<List<ActorRecord>>()
        koreanActors.map { it.firstName } shouldContain "테스트"
    }

    @Test
    fun `rapid tenant alternation reuses one pool without schema leakage`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("pool-one", maxPoolSize = 1))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val observed = (1..20).map { index ->
            val tenant = if (index % 2 == 0) Tenant.ENGLISH else Tenant.KOREAN
            client.get("/actors/2") {
                header(TenantHeader, tenant.id)
            }.body<ActorRecord>().firstName
        }

        observed.filterIndexed { index, _ -> index % 2 == 0 }.forEach {
            it shouldBeEqualTo "브래드"
        }
        observed.filterIndexed { index, _ -> index % 2 == 1 }.forEach {
            it shouldBeEqualTo "Brad"
        }
    }

    @Test
    fun `overlapping tenant requests do not leak tenant context`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("concurrent", maxPoolSize = 1))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val firstNames = coroutineScope {
            (1..16).map { index ->
                async {
                    val tenant = if (index % 2 == 0) Tenant.ENGLISH else Tenant.KOREAN
                    client.get("/actors/2") {
                        header(TenantHeader, tenant.id)
                    }.body<ActorRecord>().firstName
                }
            }.awaitAll()
        }

        firstNames shouldContain "브래드"
        firstNames shouldContain "Brad"
    }

    @Test
    fun `invalid actor id returns structured request error`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("invalid-id"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val nonNumeric = client.get("/actors/not-a-number") {
            header(TenantHeader, Tenant.KOREAN.id)
        }
        nonNumeric.status shouldBeEqualTo HttpStatusCode.BadRequest
        nonNumeric.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val negative = client.get("/actors/-1") {
            header(TenantHeader, Tenant.KOREAN.id)
        }
        negative.status shouldBeEqualTo HttpStatusCode.BadRequest
        negative.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `malformed create actor json returns structured json error`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("malformed-json"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorMultitenantJson)

        val response = client.post("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
            contentType(ContentType.Application.Json)
            setBody("""{"firstName":""")
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        response.body<StructuredError>().code shouldBeEqualTo "INVALID_JSON"
    }

    private fun newDatabase(slug: String, maxPoolSize: Int = 8): KtorMultitenantDatabase =
        KtorMultitenantDatabase.create(
            databaseName = "ktor_multitenant_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            maxPoolSize = maxPoolSize,
        )

    private fun Tenant.other(): Tenant =
        when (this) {
            Tenant.KOREAN -> Tenant.ENGLISH
            Tenant.ENGLISH -> Tenant.KOREAN
        }

    private enum class TracePhase(private val logValue: String) {
        START("start"),
        RESUME("resume"),
        NESTED_ENTER("nested-enter"),
        NESTED_SUCCESS("nested-success"),
        NESTED_FAILURE("nested-failure"),
        COMPLETE("complete"),

        ;

        override fun toString(): String = logValue
    }

    private data class KtorTenantTrace(
        val expectedTenant: String,
        val observedTenant: String?,
        val carrier: String,
        val subscriptionId: String,
        val dispatcher: String,
        val phase: TracePhase,
        val threadId: Long,
    ) {
        fun toLogLine(): String =
            "tenant-trace expectedTenant=$expectedTenant observedTenant=${observedTenant ?: "<none>"} " +
                "carrier=$carrier threadId=$threadId subscriptionId=$subscriptionId " +
                "dispatcher=$dispatcher phase=$phase"
    }

    private class KtorTenantTraceRecorder(
        private val emit: (KtorTenantTrace) -> Unit,
    ) {
        val events = mutableListOf<KtorTenantTrace>()

        fun record(
            expectedTenant: String,
            observedTenant: String?,
            subscriptionId: String,
            dispatcher: String,
            phase: TracePhase,
        ) {
            KtorTenantTrace(
                expectedTenant = expectedTenant,
                observedTenant = observedTenant,
                carrier = "KtorTenantContext",
                subscriptionId = subscriptionId,
                dispatcher = dispatcher,
                phase = phase,
                threadId = Thread.currentThread().threadId(),
            ).also {
                events += it
                emit(it)
            }
        }
    }

}
