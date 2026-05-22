package exposed.r2dbc.examples.production.ktor.app

import exposed.r2dbc.examples.production.ktor.persistence.KtorProductionTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Exposed R2DBC repository for the Ktor production slices.
 */
class KtorProductionRepository(
    private val database: R2dbcDatabase,
) {
    private companion object {
        val sessionTtl: Duration = Duration.ofHours(1)
        const val defaultRegisteredPermission = "work:create"
        val defaultRegisteredRoles = setOf("USER")
        val passwordEncoder = BCryptPasswordEncoder()
        const val maxOutboxErrorLength = 240
    }

    private val initialized = AtomicBoolean(false)
    private val publishMutex = Mutex()
    private val schemaMutex = Mutex()

    private val tables = arrayOf(
        KtorProductionTables.Accounts,
        KtorProductionTables.Sessions,
        KtorProductionTables.WorkItems,
        KtorProductionTables.OutboxEvents,
        KtorProductionTables.OutboundRequests,
        KtorProductionTables.Diagnostics,
    )

    suspend fun reset() {
        val accounts = seedAccountsWithHashes()
        schemaMutex.withLock {
            suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
                KtorProductionTables.Diagnostics.deleteAll()
                KtorProductionTables.OutboundRequests.deleteAll()
                KtorProductionTables.OutboxEvents.deleteAll()
                KtorProductionTables.WorkItems.deleteAll()
                KtorProductionTables.Sessions.deleteAll()
                KtorProductionTables.Accounts.deleteAll()
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
            KtorProductionTables.Accounts.insert {
                it[KtorProductionTables.Accounts.id] = id
                it[username] = request.username
                it[apiKey] = request.apiKey
                it[KtorProductionTables.Accounts.passwordHash] = passwordHash
                it[displayName] = request.displayName
                it[KtorProductionTables.Accounts.permission] = permission
                it[KtorProductionTables.Accounts.roles] = roles.toRolesCsv()
            }
            AccountView(id, request.username, request.displayName, permission, roles)
        }
    }

    suspend fun findAccount(username: String): AuthAccount? =
        ensureSchemaAndRead {
            KtorProductionTables.Accounts
                .selectAll()
                .where { KtorProductionTables.Accounts.username eq username }
                .singleOrNull()
                ?.let {
                    AuthAccount(
                        id = it[KtorProductionTables.Accounts.id],
                        username = it[KtorProductionTables.Accounts.username],
                        passwordHash = it[KtorProductionTables.Accounts.passwordHash],
                        displayName = it[KtorProductionTables.Accounts.displayName],
                        permission = it[KtorProductionTables.Accounts.permission],
                        roles = it[KtorProductionTables.Accounts.roles].toRoles(),
                    )
                }
        }

    suspend fun authenticate(username: String, password: String): AuthAccount? {
        username.requireNotBlank("username")
        password.requireNotBlank("password")
        val account = findAccount(username) ?: return null
        val matches = withContext(Dispatchers.Default) {
            passwordEncoder.matches(password, account.passwordHash)
        }
        return account.takeIf { matches }
    }

    suspend fun createSession(username: String): SessionView {
        ensureSchema()
        username.requireNotBlank("username")
        val id = UUID.randomUUID().toString()
        val token = "ktor_${Base58.randomString(24)}"
        val issuedAtEpochMs = Instant.now().toEpochMilli()
        val expiresAtEpochMs = issuedAtEpochMs + sessionTtl.toMillis()
        return suspendTransaction(db = database) {
            KtorProductionTables.Sessions.insert {
                it[KtorProductionTables.Sessions.id] = id
                it[KtorProductionTables.Sessions.username] = username
                it[tokenHash] = token.sha256()
                it[KtorProductionTables.Sessions.issuedAtEpochMs] = issuedAtEpochMs
                it[KtorProductionTables.Sessions.expiresAtEpochMs] = expiresAtEpochMs
            }
            SessionView(token, username, issuedAtEpochMs, expiresAtEpochMs)
        }
    }

    suspend fun findSessionByToken(token: String): SessionView? {
        ensureSchema()
        token.requireNotBlank("token")
        val tokenHash = token.sha256()
        val nowEpochMs = Instant.now().toEpochMilli()
        return suspendTransaction(db = database) {
            KtorProductionTables.Sessions
                .selectAll()
                .where {
                    (KtorProductionTables.Sessions.tokenHash eq tokenHash) and
                            (KtorProductionTables.Sessions.expiresAtEpochMs greater nowEpochMs)
                }
                .singleOrNull()
                ?.let {
                    SessionView(
                        token = null,
                        username = it[KtorProductionTables.Sessions.username],
                        issuedAtEpochMs = it[KtorProductionTables.Sessions.issuedAtEpochMs],
                        expiresAtEpochMs = it[KtorProductionTables.Sessions.expiresAtEpochMs],
                    )
                }
        }
    }

    suspend fun findSessions(username: String): List<SessionView> =
        ensureSchemaAndRead {
            val nowEpochMs = Instant.now().toEpochMilli()
            KtorProductionTables.Sessions
                .selectAll()
                .where {
                    (KtorProductionTables.Sessions.username eq username) and
                            (KtorProductionTables.Sessions.expiresAtEpochMs greater nowEpochMs)
                }
                .map {
                    SessionView(
                        token = null,
                        username = it[KtorProductionTables.Sessions.username],
                        issuedAtEpochMs = it[KtorProductionTables.Sessions.issuedAtEpochMs],
                        expiresAtEpochMs = it[KtorProductionTables.Sessions.expiresAtEpochMs],
                    )
                }
                .toList()
        }

    suspend fun hasSessionPermission(token: String, permission: String): Boolean {
        token.requireNotBlank("token")
        permission.requireNotBlank("permission")
        val session = findSessionByToken(token) ?: return false
        val account = findAccount(session.username) ?: return false
        return account.permission == permission
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
            KtorProductionTables.OutboxEvents
                .selectAll()
                .where {
                    (KtorProductionTables.OutboxEvents.sequence greater afterSequence) and
                            (KtorProductionTables.OutboxEvents.status eq OutboxStatus.PUBLISHED.name)
                }
                .orderBy(KtorProductionTables.OutboxEvents.sequence to SortOrder.ASC)
                .map { it.toOutboxEvent() }
                .toList()
        }

    suspend fun outboxEvents(): List<OutboxEventView> =
        ensureSchemaAndRead {
            KtorProductionTables.OutboxEvents
                .selectAll()
                .orderBy(KtorProductionTables.OutboxEvents.sequence to SortOrder.ASC)
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
            val needsSeed = suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
                KtorProductionTables.Accounts.selectAll().empty()
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
            KtorProductionTables.OutboxEvents
                .selectAll()
                .where { KtorProductionTables.OutboxEvents.status eq OutboxStatus.PENDING.name }
                .orderBy(KtorProductionTables.OutboxEvents.sequence to SortOrder.ASC)
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
            val current = KtorProductionTables.OutboxEvents
                .selectAll()
                .where { KtorProductionTables.OutboxEvents.sequence eq sequence }
                .singleOrNull()
                ?: throw NoSuchElementException("Outbox event $sequence was not found")
            KtorProductionTables.OutboxEvents.update({ KtorProductionTables.OutboxEvents.sequence eq sequence }) {
                it[status] = nextStatus.name
                it[delivered] = nextStatus == OutboxStatus.PUBLISHED
                it[attempts] = current[KtorProductionTables.OutboxEvents.attempts] + 1
                it[KtorProductionTables.OutboxEvents.lastError] = lastError
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
                KtorProductionTables.Accounts.insert {
                    it[id] = account.username
                    it[username] = account.username
                    it[apiKey] = account.apiKey
                    it[KtorProductionTables.Accounts.passwordHash] = passwordHash
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
            sequence = this[KtorProductionTables.OutboxEvents.sequence],
            aggregateId = this[KtorProductionTables.OutboxEvents.aggregateId],
            eventType = this[KtorProductionTables.OutboxEvents.eventType],
            payload = this[KtorProductionTables.OutboxEvents.payload],
            status = OutboxStatus.valueOf(this[KtorProductionTables.OutboxEvents.status]),
            attempts = this[KtorProductionTables.OutboxEvents.attempts],
            lastError = this[KtorProductionTables.OutboxEvents.lastError],
        )

    private fun BCryptPasswordEncoder.encodeRequired(password: String): String =
        checkNotNull(encode(password)) {
            "BCrypt encoder returned null"
        }
}
