package exposed.r2dbc.examples.production.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.session
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.io.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the Ktor production integration example.
 */
fun Application.productionIntegrationModule(
    repository: KtorProductionRepository = KtorProductionRepository(defaultProductionDatabase()),
) {
    install(ContentNegotiation) {
        json()
    }
    install(Sessions) {
        cookie<UserSession>("production_session")
    }
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session -> session }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, StructuredError("UNAUTHORIZED", "Session is required"))
            }
        }
    }
    install(StatusPages) {
        exception<DuplicateIdempotencyKeyException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                StructuredError("IDEMPOTENCY_CONFLICT", cause.message ?: "Duplicate idempotency key")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, StructuredError("INVALID_REQUEST", cause.message ?: "Invalid request"))
        }
    }
    install(WebSockets)

    routing {
        post("/production/accounts") {
            val account = repository.registerAccount(call.receive())
            call.sessions.set(UserSession(account.id))
            call.respond(account)
        }

        authenticate("auth-session") {
            post("/production/work-items") {
                call.respond(repository.createWorkItem(call.receive()))
            }

            webSocket("/production/realtime") {
                val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0L
                repository.replayEvents(after).forEach { event ->
                    send(Frame.Text(Json.encodeToString(OutboxEventView.serializer(), event)))
                }
            }

            post("/production/outbound") {
                call.respond(repository.enqueueOutbound(call.receive()))
            }
        }

        get("/production/readiness") {
            call.respond(repository.readiness())
        }
    }
}

/**
 * Creates the default H2 R2DBC database for the standalone Ktor example.
 */
fun defaultProductionDatabase(): R2dbcDatabase =
    R2dbcDatabase.connect("r2dbc:h2:mem:///ktor-production-integration;DB_CLOSE_DELAY=-1;USER=sa;")

private object KtorProductionTables {
    object Accounts: Table("ktor_prod_accounts") {
        val id = varchar("id", 64)
        val username = varchar("username", 80)
        val apiKey = varchar("api_key", 120).uniqueIndex()
        val permission = varchar("permission", 80)
        val sessionToken = varchar("session_token", 120)
        override val primaryKey = PrimaryKey(id)
    }

    object WorkItems: Table("ktor_prod_work_items") {
        val id = varchar("id", 64)
        val owner = varchar("owner", 80)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object OutboxEvents: Table("ktor_prod_outbox_events") {
        val sequence = long("sequence").autoIncrement()
        val aggregateId = varchar("aggregate_id", 64)
        val eventType = varchar("event_type", 80)
        val payload = varchar("payload", 500)
        val delivered = bool("delivered").default(false)
        override val primaryKey = PrimaryKey(sequence)
    }

    object OutboundRequests: Table("ktor_prod_outbound_requests") {
        val id = varchar("id", 64)
        val idempotencyKey = varchar("idempotency_key", 120).uniqueIndex()
        val targetUrl = varchar("target_url", 300)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object Diagnostics: Table("ktor_prod_diagnostics") {
        val name = varchar("name", 80)
        val status = varchar("status", 40)
        val details = varchar("details", 500)
        override val primaryKey = PrimaryKey(name)
    }
}

/**
 * Session identity stored in the Ktor session cookie.
 */
@kotlinx.serialization.Serializable
data class UserSession(val accountId: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Request for registering an account and permission.
 */
@kotlinx.serialization.Serializable
data class RegisterAccountRequest(val username: String, val apiKey: String, val permission: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Request for creating a validated work item.
 */
@kotlinx.serialization.Serializable
data class CreateWorkItemRequest(val owner: String, val payload: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Request for persisted outbound work with an idempotency key.
 */
@kotlinx.serialization.Serializable
data class EnqueueOutboundRequest(val idempotencyKey: String, val targetUrl: String, val payload: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the Ktor authentication slice.
 */
@kotlinx.serialization.Serializable
data class AccountView(val id: String, val username: String, val permission: String, val sessionToken: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the Ktor application slice.
 */
@kotlinx.serialization.Serializable
data class WorkItemView(val id: String, val owner: String, val payload: String, val status: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Persisted outbox event returned by realtime replay.
 */
@kotlinx.serialization.Serializable
data class OutboxEventView(val sequence: Long, val aggregateId: String, val eventType: String, val payload: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the outbound HTTP outbox slice.
 */
@kotlinx.serialization.Serializable
data class OutboundRequestView(
    val id: String,
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Readiness response returned by the diagnostics slice.
 */
@kotlinx.serialization.Serializable
data class ReadinessView(val status: String, val details: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Structured API error shared by Ktor handlers.
 */
@kotlinx.serialization.Serializable
data class StructuredError(val code: String, val message: String): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Signals a duplicate outbound idempotency key.
 */
class DuplicateIdempotencyKeyException(
    key: String,
): RuntimeException("Duplicate idempotency key: $key")

/**
 * Exposed R2DBC repository for the Ktor production slices.
 */
class KtorProductionRepository(
    private val database: R2dbcDatabase,
) {
    private val initialized = AtomicBoolean(false)

    private val tables = arrayOf(
        KtorProductionTables.Accounts,
        KtorProductionTables.WorkItems,
        KtorProductionTables.OutboxEvents,
        KtorProductionTables.OutboundRequests,
        KtorProductionTables.Diagnostics,
    )

    suspend fun reset() {
        suspendTransaction(db = database) {
            SchemaUtils.create(*tables)
            KtorProductionTables.Diagnostics.deleteAll()
            KtorProductionTables.OutboundRequests.deleteAll()
            KtorProductionTables.OutboxEvents.deleteAll()
            KtorProductionTables.WorkItems.deleteAll()
            KtorProductionTables.Accounts.deleteAll()
        }
        initialized.set(true)
    }

    suspend fun registerAccount(request: RegisterAccountRequest): AccountView {
        ensureSchema()
        request.username.requireNotBlankField("username")
        request.apiKey.requireNotBlankField("apiKey")
        request.permission.requireNotBlankField("permission")
        val id = UUID.randomUUID().toString()
        val sessionToken = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            KtorProductionTables.Accounts.insert {
                it[KtorProductionTables.Accounts.id] = id
                it[username] = request.username
                it[apiKey] = request.apiKey
                it[permission] = request.permission
                it[KtorProductionTables.Accounts.sessionToken] = sessionToken
            }
            AccountView(id, request.username, request.permission, sessionToken)
        }
    }

    suspend fun hasPermission(apiKey: String, permission: String): Boolean =
        ensureSchemaAndRead {
            KtorProductionTables.Accounts
                .selectAll()
                .where {
                    (KtorProductionTables.Accounts.apiKey eq apiKey) and
                            (KtorProductionTables.Accounts.permission eq permission)
                }
                .singleOrNull() != null
        }

    suspend fun createWorkItem(request: CreateWorkItemRequest): WorkItemView {
        ensureSchema()
        request.owner.requireNotBlankField("owner")
        request.payload.requireNotBlankField("payload")
        val id = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            KtorProductionTables.WorkItems.insert {
                it[KtorProductionTables.WorkItems.id] = id
                it[owner] = request.owner
                it[payload] = request.payload
                it[status] = "ACCEPTED"
            }
            KtorProductionTables.OutboxEvents.insert {
                it[aggregateId] = id
                it[eventType] = "work-item.accepted"
                it[payload] = request.payload
            }
            WorkItemView(id, request.owner, request.payload, "ACCEPTED")
        }
    }

    suspend fun replayEvents(afterSequence: Long): List<OutboxEventView> =
        ensureSchemaAndRead {
            KtorProductionTables.OutboxEvents
                .selectAll()
                .where { KtorProductionTables.OutboxEvents.sequence greater afterSequence }
                .map {
                    OutboxEventView(
                        sequence = it[KtorProductionTables.OutboxEvents.sequence],
                        aggregateId = it[KtorProductionTables.OutboxEvents.aggregateId],
                        eventType = it[KtorProductionTables.OutboxEvents.eventType],
                        payload = it[KtorProductionTables.OutboxEvents.payload],
                    )
                }
                .toList()
        }

    suspend fun enqueueOutbound(request: EnqueueOutboundRequest): OutboundRequestView {
        ensureSchema()
        request.idempotencyKey.requireNotBlankField("idempotencyKey")
        request.targetUrl.requireNotBlankField("targetUrl")
        request.targetUrl.requireAllowedTargetUrl()
        request.payload.requireNotBlankField("payload")
        val id = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            val duplicate = KtorProductionTables.OutboundRequests
                .selectAll()
                .where { KtorProductionTables.OutboundRequests.idempotencyKey eq request.idempotencyKey }
                .singleOrNull()
            if (duplicate != null) {
                throw DuplicateIdempotencyKeyException(request.idempotencyKey)
            }
            KtorProductionTables.OutboundRequests.insert {
                it[KtorProductionTables.OutboundRequests.id] = id
                it[idempotencyKey] = request.idempotencyKey
                it[targetUrl] = request.targetUrl
                it[payload] = request.payload
                it[status] = "PENDING"
            }
            OutboundRequestView(id, request.idempotencyKey, request.targetUrl, request.payload, "PENDING")
        }
    }

    suspend fun markDatabaseDegraded(details: String) {
        ensureSchema()
        suspendTransaction(db = database) {
            val existing = KtorProductionTables.Diagnostics
                .selectAll()
                .where { KtorProductionTables.Diagnostics.name eq "database" }
                .singleOrNull()
            if (existing == null) {
                KtorProductionTables.Diagnostics.insert {
                    it[name] = "database"
                    it[status] = "DEGRADED"
                    it[KtorProductionTables.Diagnostics.details] = details
                }
            } else {
                KtorProductionTables.Diagnostics.update({ KtorProductionTables.Diagnostics.name eq "database" }) {
                    it[status] = "DEGRADED"
                    it[KtorProductionTables.Diagnostics.details] = details
                }
            }
        }
    }

    suspend fun readiness(): ReadinessView =
        ensureSchemaAndRead {
            val degraded = KtorProductionTables.Diagnostics
                .selectAll()
                .where { KtorProductionTables.Diagnostics.status eq "DEGRADED" }
                .singleOrNull()
            if (degraded == null) {
                ReadinessView("UP", "database reachable")
            } else {
                ReadinessView("DEGRADED", degraded[KtorProductionTables.Diagnostics.details])
            }
        }

    private suspend fun ensureSchema() {
        if (initialized.get()) {
            return
        }
        suspendTransaction(db = database) {
            SchemaUtils.create(*tables)
        }
        initialized.set(true)
    }

    private suspend fun <T> ensureSchemaAndRead(block: suspend () -> T): T {
        ensureSchema()
        return suspendTransaction(db = database) {
            block()
        }
    }
}

/**
 * Dispatches persisted outbound requests through a Ktor HTTP client.
 */
class KtorOutboundDispatcher(
    private val client: HttpClient,
) {
    suspend fun dispatch(request: OutboundRequestView): HttpStatusCode =
        client.post(request.targetUrl) {
            setBody(request.payload)
        }.status
}

private fun String.requireNotBlankField(fieldName: String) {
    require(isNotBlank()) { "$fieldName must not be blank" }
}

private fun String.requireAllowedTargetUrl() {
    require(startsWith("https://example.test/")) { "targetUrl must use the example.test HTTPS host" }
}
