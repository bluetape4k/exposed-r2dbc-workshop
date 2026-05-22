package exposed.r2dbc.examples.production.ktor.app

import exposed.r2dbc.examples.production.ktor.persistence.KtorProductionTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    }

    private val initialized = AtomicBoolean(false)
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

    private fun BCryptPasswordEncoder.encodeRequired(password: String): String =
        checkNotNull(encode(password)) {
            "BCrypt encoder returned null"
        }
}
