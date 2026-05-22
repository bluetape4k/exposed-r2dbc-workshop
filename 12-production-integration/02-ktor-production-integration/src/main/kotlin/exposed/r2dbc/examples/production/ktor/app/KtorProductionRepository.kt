package exposed.r2dbc.examples.production.ktor.app

import exposed.r2dbc.examples.production.ktor.persistence.KtorProductionTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
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
import kotlin.time.toKotlinDuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Exposed R2DBC repository for the Ktor production slices.
 */
class KtorProductionRepository(
    private val database: R2dbcDatabase,
) {
    private companion object: KLogging() {
        val sessionTtl: Duration = Duration.ofHours(1)
        const val defaultRegisteredPermission = "work:create"
        val defaultRegisteredRoles = setOf("USER")
        val passwordEncoder = BCryptPasswordEncoder()
        const val maxOutboxErrorLength = 240
        const val maxOutboundErrorLength = 240
        const val maxOutboundAttempts = 3
        // Longest prefix preserved before a sanitized error body, e.g. "HTTP 599 ".
        private const val maxHttpStatusPrefixLength = 9
        val outboundDispatchTimeout: Duration = Duration.ofSeconds(5)
        val idempotencyKeyPattern = Regex("[A-Za-z0-9._-]{1,120}")
        val credentialLikeErrorPattern =
            Regex("(?i)\\b(authorization|cookie|token|secret|api[-_ ]?key)[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+")
        const val diagnosticOperationLimit = 100
        const val slowDiagnosticThresholdMs = 250L
        val diagnosticPingTimeout: Duration = Duration.ofSeconds(1)
        val diagnosticOperationNamePattern = Regex("[a-z][a-z0-9-]{0,40}")
        val diagnosticRequestIdPattern = Regex("[A-Za-z0-9._-]{1,64}")
    }

    private val initialized = AtomicBoolean(false)
    private val publishMutex = Mutex()
    private val dispatchMutex = Mutex()
    private val outboundMutationMutex = Mutex()
    private val schemaMutex = Mutex()

    private val tables = arrayOf(
        KtorProductionTables.Accounts,
        KtorProductionTables.Sessions,
        KtorProductionTables.WorkItems,
        KtorProductionTables.OutboxEvents,
        KtorProductionTables.OutboundRequests,
        KtorProductionTables.Diagnostics,
        KtorProductionTables.DiagnosticOperations,
    )

    suspend fun reset() {
        val accounts = seedAccountsWithHashes()
        schemaMutex.withLock {
            suspendTransaction(db = database) {
                SchemaUtils.create(*tables)
                KtorProductionTables.DiagnosticOperations.deleteAll()
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
        request.idempotencyKey.requireValidOutboundIdempotencyKey()
        request.targetUrl.requireNotBlank("targetUrl")
        request.targetUrl.requireAllowedTargetUrl()
        request.payload.requireNotBlank("payload")
        val id = UUID.randomUUID().toString()
        return outboundMutationMutex.withLock {
            suspendTransaction(db = database) {
                val duplicate = findOutboundByIdempotencyKeyInTransaction(request.idempotencyKey)
                if (duplicate != null) {
                    throw DuplicateIdempotencyKeyException(request.idempotencyKey)
                }
                try {
                    KtorProductionTables.OutboundRequests.insert {
                        it[KtorProductionTables.OutboundRequests.id] = id
                        it[idempotencyKey] = request.idempotencyKey
                        it[targetUrl] = request.targetUrl
                        it[payload] = request.payload
                        it[status] = OutboundStatus.PENDING.name
                        it[attempts] = 0
                        it[lastStatusCode] = null
                        it[lastError] = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (findOutboundByIdempotencyKeyInTransaction(request.idempotencyKey) != null) {
                        throw DuplicateIdempotencyKeyException(request.idempotencyKey)
                    }
                    throw e
                }
                checkNotNull(findOutboundByIdInTransaction(id)) {
                    "Inserted outbound request $id was not found"
                }
            }
        }
    }

    suspend fun outboundRequests(): List<OutboundRequestView> =
        ensureSchemaAndRead {
            KtorProductionTables.OutboundRequests
                .selectAll()
                .orderBy(KtorProductionTables.OutboundRequests.id to SortOrder.ASC)
                .map { it.toOutboundRequest() }
                .toList()
        }

    suspend fun dispatchPendingOutbound(
        delivery: OutboundDelivery,
        timeout: Duration = outboundDispatchTimeout,
    ): DispatchOutboundView {
        ensureSchema()
        return dispatchMutex.withLock {
            val claimed = claimDispatchableOutbound()
            var succeeded = 0
            var retryableFailed = 0
            var permanentFailed = 0
            for (request in claimed) {
                val result = try {
                    withTimeout(timeout.toKotlinDuration()) {
                        delivery.dispatch(request)
                    }
                } catch (e: TimeoutCancellationException) {
                    OutboundDispatchResult(
                        statusCode = 599,
                        error = "Transport timeout after ${timeout.toMillis()}ms",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn(e) { "Outbound dispatch failed for requestId=${request.id}" }
                    OutboundDispatchResult(
                        statusCode = 599,
                        error = "Transport failure: ${e::class.simpleName ?: "Exception"}",
                    )
                }
                when (markOutboundDispatchResult(request.id, result).status) {
                    OutboundStatus.SUCCEEDED -> succeeded += 1
                    OutboundStatus.RETRYABLE_FAILED -> retryableFailed += 1
                    OutboundStatus.PERMANENT_FAILED -> permanentFailed += 1
                    OutboundStatus.PENDING,
                    OutboundStatus.IN_FLIGHT -> Unit
                }
            }
            DispatchOutboundView(
                attempted = claimed.size,
                succeeded = succeeded,
                retryableFailed = retryableFailed,
                permanentFailed = permanentFailed,
            )
        }
    }

    suspend fun recordDiagnosticOperation(command: RecordDiagnosticOperationCommand): DiagnosticOperationView {
        ensureSchema()
        command.name.requireValidDiagnosticOperationName()
        command.requestId.requireValidDiagnosticRequestId()
        require(command.durationMs >= 0) {
            "durationMs must be greater than or equal to zero"
        }
        val id = UUID.randomUUID().toString()
        val createdAtEpochMs = Instant.now().toEpochMilli()
        return suspendTransaction(db = database) {
            KtorProductionTables.DiagnosticOperations.insert {
                it[KtorProductionTables.DiagnosticOperations.id] = id
                it[name] = command.name
                it[requestId] = command.requestId
                it[durationMs] = command.durationMs
                it[slow] = command.slow
                it[KtorProductionTables.DiagnosticOperations.createdAtEpochMs] = createdAtEpochMs
            }
            DiagnosticOperationView(
                id = id,
                name = command.name,
                requestId = command.requestId,
                durationMs = command.durationMs,
                slow = command.slow,
                createdAtEpochMs = createdAtEpochMs,
            )
        }
    }

    suspend fun diagnosticOperations(limit: Int = diagnosticOperationLimit): List<DiagnosticOperationView> =
        ensureSchemaAndRead {
            KtorProductionTables.DiagnosticOperations
                .selectAll()
                .orderBy(KtorProductionTables.DiagnosticOperations.createdAtEpochMs to SortOrder.DESC)
                .limit(limit)
                .map { it.toDiagnosticOperation() }
                .toList()
        }

    suspend fun markDatabaseDegraded(details: String) {
        ensureSchema()
        details.requireNotBlank("details")
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

    suspend fun clearDatabaseDegraded() {
        ensureSchema()
        schemaMutex.withLock {
            suspendTransaction(db = database) {
                KtorProductionTables.Diagnostics.deleteWhere {
                    KtorProductionTables.Diagnostics.name eq "database"
                }
            }
        }
    }

    suspend fun pingDatabase(): Boolean =
        try {
            withTimeout(diagnosticPingTimeout.toKotlinDuration()) {
                suspendTransaction(db = database) {
                    KtorProductionTables.Diagnostics.selectAll().limit(1).toList()
                    true
                }
            }
        } catch (e: TimeoutCancellationException) {
            log.warn(e) { "Database readiness ping timed out" }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Database readiness ping failed" }
            false
        }

    suspend fun readiness(requestId: String? = null): ReadinessView {
        try {
            ensureSchema()
            val degraded = degradedDatabaseDetails()
            if (degraded != null) {
                return ReadinessView("DEGRADED", degraded, requestId)
            }
            return if (pingDatabase()) {
                ReadinessView("UP", "database reachable", requestId)
            } else {
                ReadinessView("DEGRADED", "database unreachable", requestId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Database readiness check failed for requestId=${requestId.orEmpty()}" }
            return ReadinessView("DEGRADED", "database unreachable", requestId)
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

    private fun String.requireValidOutboundIdempotencyKey() {
        require(idempotencyKeyPattern.matches(this)) {
            "idempotencyKey must be 1-120 characters and contain only letters, digits, dot, underscore, or hyphen"
        }
    }

    private fun String.requireValidDiagnosticOperationName() {
        require(diagnosticOperationNamePattern.matches(this)) {
            "operation name must start with a lowercase letter and contain only lowercase letters, digits, or hyphen"
        }
    }

    private fun String.requireValidDiagnosticRequestId() {
        require(diagnosticRequestIdPattern.matches(this)) {
            "requestId must be 1-64 characters and contain only letters, digits, dot, underscore, or hyphen"
        }
    }

    private suspend fun degradedDatabaseDetails(): String? =
        suspendTransaction(db = database) {
            KtorProductionTables.Diagnostics
                .selectAll()
                .where {
                    (KtorProductionTables.Diagnostics.name eq "database") and
                            (KtorProductionTables.Diagnostics.status eq "DEGRADED")
                }
                .singleOrNull()
                ?.get(KtorProductionTables.Diagnostics.details)
        }

    private suspend fun claimDispatchableOutbound(): List<OutboundRequestView> =
        suspendTransaction(db = database) {
            val dispatchable = KtorProductionTables.OutboundRequests
                .selectAll()
                .where {
                    ((KtorProductionTables.OutboundRequests.status eq OutboundStatus.PENDING.name) or
                            (KtorProductionTables.OutboundRequests.status eq OutboundStatus.RETRYABLE_FAILED.name)) and
                            (KtorProductionTables.OutboundRequests.attempts less maxOutboundAttempts)
                }
                .orderBy(KtorProductionTables.OutboundRequests.id to SortOrder.ASC)
                .map { it.toOutboundRequest() }
                .toList()
            dispatchable.mapNotNull { request ->
                val updated = KtorProductionTables.OutboundRequests.update({
                    (KtorProductionTables.OutboundRequests.id eq request.id) and
                        (((KtorProductionTables.OutboundRequests.status eq OutboundStatus.PENDING.name) or
                            (KtorProductionTables.OutboundRequests.status eq OutboundStatus.RETRYABLE_FAILED.name)) and
                            (KtorProductionTables.OutboundRequests.attempts less maxOutboundAttempts))
                }) {
                    it[status] = OutboundStatus.IN_FLIGHT.name
                    it[lastError] = null
                }
                if (updated == 1) {
                    request.copy(status = OutboundStatus.IN_FLIGHT)
                } else {
                    null
                }
            }
        }

    private suspend fun markOutboundDispatchResult(
        id: String,
        result: OutboundDispatchResult,
    ): OutboundRequestView =
        suspendTransaction(db = database) {
            val current = findOutboundByIdInTransaction(id)
                ?: throw NoSuchElementException("Outbound request $id was not found")
            val nextAttempts = current.attempts + 1
            val nextStatus = result.statusCode.toOutboundStatus(nextAttempts)
            val nextError = if (nextStatus == OutboundStatus.SUCCEEDED) {
                null
            } else {
                sanitizeOutboundError(result.statusCode, result.error)
            }
            KtorProductionTables.OutboundRequests.update({ KtorProductionTables.OutboundRequests.id eq id }) {
                it[status] = nextStatus.name
                it[attempts] = nextAttempts
                it[lastStatusCode] = result.statusCode
                it[lastError] = nextError
            }
            checkNotNull(findOutboundByIdInTransaction(id)) {
                "Updated outbound request $id was not found"
            }
        }

    private fun Int.toOutboundStatus(nextAttempts: Int): OutboundStatus =
        when {
            this in 200..299 -> OutboundStatus.SUCCEEDED
            this in setOf(408, 425, 429) || this >= 500 ->
                if (nextAttempts >= maxOutboundAttempts) {
                    OutboundStatus.PERMANENT_FAILED
                } else {
                    OutboundStatus.RETRYABLE_FAILED
                }
            this in 400..499 -> OutboundStatus.PERMANENT_FAILED
            else -> OutboundStatus.RETRYABLE_FAILED
        }

    private fun sanitizeOutboundError(statusCode: Int, rawMessage: String?): String {
        val safeMessage = rawMessage
            ?.replace(credentialLikeErrorPattern, "\$1:[redacted]")
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(maxOutboundErrorLength - maxHttpStatusPrefixLength)
            ?.takeIf { it.isNotBlank() }
        return listOfNotNull("HTTP $statusCode", safeMessage)
            .joinToString(" ")
            .take(maxOutboundErrorLength)
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

    private suspend fun findOutboundByIdInTransaction(id: String): OutboundRequestView? =
        KtorProductionTables.OutboundRequests
            .selectAll()
            .where { KtorProductionTables.OutboundRequests.id eq id }
            .singleOrNull()
            ?.toOutboundRequest()

    private suspend fun findOutboundByIdempotencyKeyInTransaction(idempotencyKey: String): OutboundRequestView? =
        KtorProductionTables.OutboundRequests
            .selectAll()
            .where { KtorProductionTables.OutboundRequests.idempotencyKey eq idempotencyKey }
            .singleOrNull()
            ?.toOutboundRequest()

    private fun ResultRow.toOutboundRequest(): OutboundRequestView =
        OutboundRequestView(
            id = this[KtorProductionTables.OutboundRequests.id],
            idempotencyKey = this[KtorProductionTables.OutboundRequests.idempotencyKey],
            targetUrl = this[KtorProductionTables.OutboundRequests.targetUrl],
            payload = this[KtorProductionTables.OutboundRequests.payload],
            status = OutboundStatus.valueOf(this[KtorProductionTables.OutboundRequests.status]),
            attempts = this[KtorProductionTables.OutboundRequests.attempts],
            lastStatusCode = this[KtorProductionTables.OutboundRequests.lastStatusCode],
            lastError = this[KtorProductionTables.OutboundRequests.lastError],
        )

    private fun ResultRow.toDiagnosticOperation(): DiagnosticOperationView =
        DiagnosticOperationView(
            id = this[KtorProductionTables.DiagnosticOperations.id],
            name = this[KtorProductionTables.DiagnosticOperations.name],
            requestId = this[KtorProductionTables.DiagnosticOperations.requestId],
            durationMs = this[KtorProductionTables.DiagnosticOperations.durationMs],
            slow = this[KtorProductionTables.DiagnosticOperations.slow],
            createdAtEpochMs = this[KtorProductionTables.DiagnosticOperations.createdAtEpochMs],
        )

    private fun BCryptPasswordEncoder.encodeRequired(password: String): String =
        checkNotNull(encode(password)) {
            "BCrypt encoder returned null"
        }
}
