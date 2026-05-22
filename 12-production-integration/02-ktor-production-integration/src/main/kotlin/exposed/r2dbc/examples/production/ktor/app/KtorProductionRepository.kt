package exposed.r2dbc.examples.production.ktor.app

import exposed.r2dbc.examples.production.ktor.persistence.KtorProductionTables
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exposed R2DBC repository for the Ktor production slices.
 */
class KtorProductionRepository(
    private val database: R2dbcDatabase,
) {
    private val initialized = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    private val tables = arrayOf(
        KtorProductionTables.Accounts,
        KtorProductionTables.WorkItems,
        KtorProductionTables.OutboxEvents,
        KtorProductionTables.OutboundRequests,
        KtorProductionTables.Diagnostics,
    )

    suspend fun reset() {
        schemaMutex.withLock {
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
    }

    suspend fun registerAccount(request: RegisterAccountRequest): AccountView {
        ensureSchema()
        request.username.requireNotBlank("username")
        request.apiKey.requireNotBlank("apiKey")
        request.permission.requireNotBlank("permission")
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
        request.owner.requireNotBlank("owner")
        request.payload.requireNotBlank("payload")
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
        request.idempotencyKey.requireNotBlank("idempotencyKey")
        request.targetUrl.requireNotBlank("targetUrl")
        request.targetUrl.requireAllowedTargetUrl()
        request.payload.requireNotBlank("payload")
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
        schemaMutex.withLock {
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
        schemaMutex.withLock {
            if (initialized.get()) {
                return
            }
            suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
            }
            initialized.set(true)
        }
    }

    private suspend fun <T> ensureSchemaAndRead(block: suspend () -> T): T {
        ensureSchema()
        return suspendTransaction(db = database) {
            block()
        }
    }

    private fun String.requireAllowedTargetUrl() {
        val uri = try {
            URI.create(this)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("targetUrl must be a valid URI", e)
        }
        require(uri.scheme == "https" && uri.host == "example.test") {
            "targetUrl must use the example.test HTTPS host"
        }
    }
}
