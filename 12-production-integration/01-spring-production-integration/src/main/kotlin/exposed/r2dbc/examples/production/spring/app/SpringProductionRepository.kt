package exposed.r2dbc.examples.production.spring.app

import exposed.r2dbc.examples.production.spring.persistence.SpringProductionTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
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
import org.springframework.stereotype.Repository
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Exposed R2DBC repository for the Spring production slices.
 */
@Repository
class SpringProductionRepository(
    private val database: R2dbcDatabase,
    private val passwordEncoder: PasswordEncoder,
) {
    private companion object {
        val sessionTtl: Duration = Duration.ofHours(1)
        const val defaultRegisteredPermission = "work:create"
        val defaultRegisteredRoles = setOf("USER")
        const val maxOutboxErrorLength = 240
    }

    private val initialized = AtomicBoolean(false)
    private val publishMutex = Mutex()
    private val schemaMutex = Mutex()

    private val tables = arrayOf(
        SpringProductionTables.Accounts,
        SpringProductionTables.Sessions,
        SpringProductionTables.WorkItems,
        SpringProductionTables.OutboxEvents,
        SpringProductionTables.OutboundRequests,
        SpringProductionTables.Diagnostics,
    )

    suspend fun reset() {
        val accounts = seedAccountsWithHashes()
        schemaMutex.withLock {
            suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
                SpringProductionTables.Diagnostics.deleteAll()
                SpringProductionTables.OutboundRequests.deleteAll()
                SpringProductionTables.OutboxEvents.deleteAll()
                SpringProductionTables.WorkItems.deleteAll()
                SpringProductionTables.Sessions.deleteAll()
                SpringProductionTables.Accounts.deleteAll()
            }
            insertSeedAccounts(accounts)
            initialized.set(true)
        }
    }

    suspend fun registerAccount(request: RegisterAccountRequest): AccountView {
        ensureSchema()
        request.username.requireNotBlank("username")
        request.apiKey.requireNotBlank("apiKey")
        request.permission.requireNotBlank("permission")
        request.password.requireNotBlank("password")
        request.displayName.requireNotBlank("displayName")
        val permission = defaultRegisteredPermission
        val roles = defaultRegisteredRoles
        val passwordHash = passwordEncoder.encodeRequired(request.password)
        val id = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            SpringProductionTables.Accounts.insert {
                it[SpringProductionTables.Accounts.id] = id
                it[username] = request.username
                it[apiKey] = request.apiKey
                it[SpringProductionTables.Accounts.passwordHash] = passwordHash
                it[displayName] = request.displayName
                it[SpringProductionTables.Accounts.permission] = permission
                it[SpringProductionTables.Accounts.roles] = roles.toRolesCsv()
            }
            AccountView(id, request.username, request.displayName, permission, roles)
        }
    }

    suspend fun findAccount(username: String): AuthAccount? =
        ensureSchemaAndRead {
            SpringProductionTables.Accounts
                .selectAll()
                .where { SpringProductionTables.Accounts.username eq username }
                .singleOrNull()
                ?.let {
                    AuthAccount(
                        id = it[SpringProductionTables.Accounts.id],
                        username = it[SpringProductionTables.Accounts.username],
                        passwordHash = it[SpringProductionTables.Accounts.passwordHash],
                        displayName = it[SpringProductionTables.Accounts.displayName],
                        permission = it[SpringProductionTables.Accounts.permission],
                        roles = it[SpringProductionTables.Accounts.roles].toRoles(),
                    )
                }
        }

    suspend fun createSession(username: String): SessionView {
        ensureSchema()
        username.requireNotBlank("username")
        val id = UUID.randomUUID().toString()
        val token = "spring_${Base58.randomString(24)}"
        val issuedAtEpochMs = Instant.now().toEpochMilli()
        val expiresAtEpochMs = issuedAtEpochMs + sessionTtl.toMillis()
        return suspendTransaction(db = database) {
            SpringProductionTables.Sessions.insert {
                it[SpringProductionTables.Sessions.id] = id
                it[SpringProductionTables.Sessions.username] = username
                it[tokenHash] = token.sha256()
                it[SpringProductionTables.Sessions.issuedAtEpochMs] = issuedAtEpochMs
                it[SpringProductionTables.Sessions.expiresAtEpochMs] = expiresAtEpochMs
            }
            SessionView(token, username, issuedAtEpochMs, expiresAtEpochMs)
        }
    }

    suspend fun findSessions(username: String): List<SessionView> =
        ensureSchemaAndRead {
            val nowEpochMs = Instant.now().toEpochMilli()
            SpringProductionTables.Sessions
                .selectAll()
                .where {
                    (SpringProductionTables.Sessions.username eq username) and
                            (SpringProductionTables.Sessions.expiresAtEpochMs greater nowEpochMs)
                }
                .map {
                    SessionView(
                        token = null,
                        username = it[SpringProductionTables.Sessions.username],
                        issuedAtEpochMs = it[SpringProductionTables.Sessions.issuedAtEpochMs],
                        expiresAtEpochMs = it[SpringProductionTables.Sessions.expiresAtEpochMs],
                    )
                }
                .toList()
        }

    suspend fun hasPermission(apiKey: String, permission: String): Boolean =
        ensureSchemaAndRead {
            SpringProductionTables.Accounts
                .selectAll()
                .where {
                    (SpringProductionTables.Accounts.apiKey eq apiKey) and
                            (SpringProductionTables.Accounts.permission eq permission)
                }
                .singleOrNull() != null
        }

    suspend fun createWorkItem(request: CreateWorkItemRequest): WorkItemView {
        ensureSchema()
        request.owner.requireNotBlank("owner")
        request.payload.requireNotBlank("payload")
        val id = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            SpringProductionTables.WorkItems.insert {
                it[SpringProductionTables.WorkItems.id] = id
                it[owner] = request.owner
                it[payload] = request.payload
                it[status] = "ACCEPTED"
            }
            SpringProductionTables.OutboxEvents.insert {
                it[aggregateId] = id
                it[eventType] = "work-item.accepted"
                it[payload] = request.payload
                it[status] = OutboxStatus.PENDING.name
                it[attempts] = 0
                it[lastError] = null
                it[delivered] = false
            }
            WorkItemView(id, request.owner, request.payload, "ACCEPTED")
        }
    }

    suspend fun replayEvents(afterSequence: Long): List<OutboxEventView> =
        ensureSchemaAndRead {
            SpringProductionTables.OutboxEvents
                .selectAll()
                .where {
                    (SpringProductionTables.OutboxEvents.sequence greater afterSequence) and
                            (SpringProductionTables.OutboxEvents.status eq OutboxStatus.PUBLISHED.name)
                }
                .orderBy(SpringProductionTables.OutboxEvents.sequence to SortOrder.ASC)
                .map { it.toOutboxEvent() }
                .toList()
        }

    suspend fun outboxEvents(): List<OutboxEventView> =
        ensureSchemaAndRead {
            SpringProductionTables.OutboxEvents
                .selectAll()
                .orderBy(SpringProductionTables.OutboxEvents.sequence to SortOrder.ASC)
                .map { it.toOutboxEvent() }
                .toList()
        }

    suspend fun publishPending(delivery: RealtimeDelivery): PublishOutboxView {
        ensureSchema()
        return publishMutex.withLock {
            val pending = pendingOutboxEvents()
            var delivered = 0
            var failed = 0
            for (event in pending) {
                val publishedEvent = event.copy(
                    status = OutboxStatus.PUBLISHED,
                    attempts = event.attempts + 1,
                    lastError = null,
                )
                val deliveryAccepted = try {
                    delivery.deliver(publishedEvent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markOutboxFailed(event.sequence, e.message ?: "realtime delivery threw an exception")
                    failed += 1
                    continue
                }
                if (deliveryAccepted) {
                    markOutboxPublished(event.sequence)
                    delivered += 1
                } else {
                    markOutboxFailed(event.sequence, "realtime delivery returned false")
                    failed += 1
                }
            }
            PublishOutboxView(
                attempted = pending.size,
                delivered = delivered,
                failed = failed,
            )
        }
    }

    suspend fun enqueueOutbound(request: EnqueueOutboundRequest): OutboundRequestView {
        ensureSchema()
        request.idempotencyKey.requireNotBlank("idempotencyKey")
        request.targetUrl.requireNotBlank("targetUrl")
        request.targetUrl.requireAllowedTargetUrl()
        request.payload.requireNotBlank("payload")
        val id = UUID.randomUUID().toString()
        return suspendTransaction(db = database) {
            val duplicate = SpringProductionTables.OutboundRequests
                .selectAll()
                .where { SpringProductionTables.OutboundRequests.idempotencyKey eq request.idempotencyKey }
                .singleOrNull()
            if (duplicate != null) {
                throw DuplicateIdempotencyKeyException(request.idempotencyKey)
            }
            SpringProductionTables.OutboundRequests.insert {
                it[SpringProductionTables.OutboundRequests.id] = id
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
                val existing = SpringProductionTables.Diagnostics
                    .selectAll()
                    .where { SpringProductionTables.Diagnostics.name eq "database" }
                    .singleOrNull()
                if (existing == null) {
                    SpringProductionTables.Diagnostics.insert {
                        it[name] = "database"
                        it[status] = "DEGRADED"
                        it[SpringProductionTables.Diagnostics.details] = details
                    }
                } else {
                    SpringProductionTables.Diagnostics.update({ SpringProductionTables.Diagnostics.name eq "database" }) {
                        it[status] = "DEGRADED"
                        it[SpringProductionTables.Diagnostics.details] = details
                    }
                }
            }
        }
    }

    suspend fun readiness(): ReadinessView =
        ensureSchemaAndRead {
            val degraded = SpringProductionTables.Diagnostics
                .selectAll()
                .where { SpringProductionTables.Diagnostics.status eq "DEGRADED" }
                .singleOrNull()
            if (degraded == null) {
                ReadinessView("UP", "database reachable")
            } else {
                ReadinessView("DEGRADED", degraded[SpringProductionTables.Diagnostics.details])
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
            val needsSeed = suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
                SpringProductionTables.Accounts.selectAll().empty()
            }
            if (needsSeed) {
                insertSeedAccounts(seedAccountsWithHashes())
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

    private suspend fun pendingOutboxEvents(): List<OutboxEventView> =
        suspendTransaction(db = database) {
            SpringProductionTables.OutboxEvents
                .selectAll()
                .where { SpringProductionTables.OutboxEvents.status eq OutboxStatus.PENDING.name }
                .orderBy(SpringProductionTables.OutboxEvents.sequence to SortOrder.ASC)
                .map { it.toOutboxEvent() }
                .toList()
        }

    private suspend fun markOutboxPublished(sequence: Long) {
        updateOutboxStatus(sequence, OutboxStatus.PUBLISHED, lastError = null)
    }

    private suspend fun markOutboxFailed(sequence: Long, message: String) {
        updateOutboxStatus(sequence, OutboxStatus.FAILED, lastError = message.take(maxOutboxErrorLength))
    }

    private suspend fun updateOutboxStatus(
        sequence: Long,
        nextStatus: OutboxStatus,
        lastError: String?,
    ) {
        suspendTransaction(db = database) {
            val current = SpringProductionTables.OutboxEvents
                .selectAll()
                .where { SpringProductionTables.OutboxEvents.sequence eq sequence }
                .singleOrNull()
                ?: throw NoSuchElementException("Outbox event $sequence was not found")
            SpringProductionTables.OutboxEvents.update({ SpringProductionTables.OutboxEvents.sequence eq sequence }) {
                it[status] = nextStatus.name
                it[delivered] = nextStatus == OutboxStatus.PUBLISHED
                it[attempts] = current[SpringProductionTables.OutboxEvents.attempts] + 1
                it[SpringProductionTables.OutboxEvents.lastError] = lastError
            }
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

    private fun seedAccountsWithHashes(): List<Pair<SeedAccount, String>> =
        listOf(
            SeedAccount("alice", "alice-api-key", "work:create", "password", "Alice Reader", setOf("USER")),
            SeedAccount("admin", "admin-api-key", "outbound:create", "password", "Admin Operator", setOf("USER", "ADMIN")),
        ).map { account ->
            account to passwordEncoder.encodeRequired(account.password)
        }

    private suspend fun insertSeedAccounts(accounts: List<Pair<SeedAccount, String>>) {
        suspendTransaction(db = database) {
            accounts.forEach { (account, passwordHash) ->
                SpringProductionTables.Accounts.insert {
                    it[id] = account.username
                    it[username] = account.username
                    it[apiKey] = account.apiKey
                    it[SpringProductionTables.Accounts.passwordHash] = passwordHash
                    it[displayName] = account.displayName
                    it[permission] = account.permission
                    it[roles] = account.roles.toRolesCsv()
                }
            }
        }
    }

    private data class SeedAccount(
        val username: String,
        val apiKey: String,
        val permission: String,
        val password: String,
        val displayName: String,
        val roles: Set<String>,
    )

    private fun Set<String>.toRolesCsv(): String =
        map { it.requireNotBlank("role") }
            .sorted()
            .joinToString(",")

    private fun String.toRoles(): Set<String> =
        split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun ResultRow.toOutboxEvent(): OutboxEventView =
        OutboxEventView(
            sequence = this[SpringProductionTables.OutboxEvents.sequence],
            aggregateId = this[SpringProductionTables.OutboxEvents.aggregateId],
            eventType = this[SpringProductionTables.OutboxEvents.eventType],
            payload = this[SpringProductionTables.OutboxEvents.payload],
            status = OutboxStatus.valueOf(this[SpringProductionTables.OutboxEvents.status]),
            attempts = this[SpringProductionTables.OutboxEvents.attempts],
            lastError = this[SpringProductionTables.OutboxEvents.lastError],
        )

    private fun PasswordEncoder.encodeRequired(password: String): String =
        checkNotNull(encode(password)) {
            "Password encoder returned null"
        }

}
