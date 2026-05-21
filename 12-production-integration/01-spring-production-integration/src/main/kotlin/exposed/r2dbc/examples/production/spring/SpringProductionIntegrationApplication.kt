package exposed.r2dbc.examples.production.spring

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.asFlux
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
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.io.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.flow

/**
 * Starts the Spring Boot 4 production integration example.
 */
@SpringBootApplication
class SpringProductionIntegrationApplication

fun main(args: Array<String>) {
    runApplication<SpringProductionIntegrationApplication>(*args)
}

/**
 * Provides the H2 R2DBC database used by the Spring app-boundary example.
 */
@Configuration
class SpringProductionDatabaseConfig {
    @Bean
    fun productionDatabase(): R2dbcDatabase =
        R2dbcDatabase.connect("r2dbc:h2:mem:///spring-production-integration;DB_CLOSE_DELAY=-1;USER=sa;")
}

private object SpringProductionTables {
    object Accounts: Table("spring_prod_accounts") {
        val id = varchar("id", 64)
        val username = varchar("username", 80)
        val apiKey = varchar("api_key", 120).uniqueIndex()
        val permission = varchar("permission", 80)
        val sessionToken = varchar("session_token", 120)
        override val primaryKey = PrimaryKey(id)
    }

    object WorkItems: Table("spring_prod_work_items") {
        val id = varchar("id", 64)
        val owner = varchar("owner", 80)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object OutboxEvents: Table("spring_prod_outbox_events") {
        val sequence = long("sequence").autoIncrement()
        val aggregateId = varchar("aggregate_id", 64)
        val eventType = varchar("event_type", 80)
        val payload = varchar("payload", 500)
        val delivered = bool("delivered").default(false)
        override val primaryKey = PrimaryKey(sequence)
    }

    object OutboundRequests: Table("spring_prod_outbound_requests") {
        val id = varchar("id", 64)
        val idempotencyKey = varchar("idempotency_key", 120).uniqueIndex()
        val targetUrl = varchar("target_url", 300)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object Diagnostics: Table("spring_prod_diagnostics") {
        val name = varchar("name", 80)
        val status = varchar("status", 40)
        val details = varchar("details", 500)
        override val primaryKey = PrimaryKey(name)
    }
}

/**
 * Registers an account and its workshop permission.
 */
data class RegisterAccountRequest(
    val username: String,
    val apiKey: String,
    val permission: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Creates a validated production work item.
 */
data class CreateWorkItemRequest(
    val owner: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Describes an outbound call that must be persisted before dispatch.
 */
data class EnqueueOutboundRequest(
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Account response returned by the authentication slice.
 */
data class AccountView(
    val id: String,
    val username: String,
    val permission: String,
    val sessionToken: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Work item response returned by the application slice.
 */
data class WorkItemView(
    val id: String,
    val owner: String,
    val payload: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Persisted realtime event returned by the realtime outbox slice.
 */
data class OutboxEventView(
    val sequence: Long,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Outbound request response returned by the HTTP client outbox slice.
 */
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
data class ReadinessView(
    val status: String,
    val details: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Structured API error used by Spring and mirrored by the Ktor module.
 */
data class StructuredError(
    val code: String,
    val message: String,
): Serializable {
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
 * Exposed R2DBC repository for the Spring production slices.
 */
@org.springframework.stereotype.Repository
class SpringProductionRepository(
    private val database: R2dbcDatabase,
) {
    private val initialized = AtomicBoolean(false)

    private val tables = arrayOf(
        SpringProductionTables.Accounts,
        SpringProductionTables.WorkItems,
        SpringProductionTables.OutboxEvents,
        SpringProductionTables.OutboundRequests,
        SpringProductionTables.Diagnostics,
    )

    suspend fun reset() {
        suspendTransaction(db = database) {
            SchemaUtils.create(*tables)
            SpringProductionTables.Diagnostics.deleteAll()
            SpringProductionTables.OutboundRequests.deleteAll()
            SpringProductionTables.OutboxEvents.deleteAll()
            SpringProductionTables.WorkItems.deleteAll()
            SpringProductionTables.Accounts.deleteAll()
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
            SpringProductionTables.Accounts.insert {
                it[SpringProductionTables.Accounts.id] = id
                it[username] = request.username
                it[apiKey] = request.apiKey
                it[permission] = request.permission
                it[SpringProductionTables.Accounts.sessionToken] = sessionToken
            }
            AccountView(id, request.username, request.permission, sessionToken)
        }
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
        request.owner.requireNotBlankField("owner")
        request.payload.requireNotBlankField("payload")
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
            }
            WorkItemView(id, request.owner, request.payload, "ACCEPTED")
        }
    }

    suspend fun replayEvents(afterSequence: Long): List<OutboxEventView> =
        ensureSchemaAndRead {
            SpringProductionTables.OutboxEvents
                .selectAll()
                .where { SpringProductionTables.OutboxEvents.sequence greater afterSequence }
                .map {
                    OutboxEventView(
                        sequence = it[SpringProductionTables.OutboxEvents.sequence],
                        aggregateId = it[SpringProductionTables.OutboxEvents.aggregateId],
                        eventType = it[SpringProductionTables.OutboxEvents.eventType],
                        payload = it[SpringProductionTables.OutboxEvents.payload],
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
 * Application service that keeps coroutine and reactive boundaries explicit.
 */
@org.springframework.stereotype.Service
class SpringProductionWorkflowService(
    private val repository: SpringProductionRepository,
) {
    suspend fun registerAccount(request: RegisterAccountRequest): AccountView =
        repository.registerAccount(request)

    suspend fun createWorkItem(request: CreateWorkItemRequest): WorkItemView =
        repository.createWorkItem(request)

    suspend fun replayEvents(afterSequence: Long): List<OutboxEventView> =
        repository.replayEvents(afterSequence)

    suspend fun enqueueOutbound(request: EnqueueOutboundRequest): OutboundRequestView =
        repository.enqueueOutbound(request)

    suspend fun readiness(): ReadinessView =
        repository.readiness()

    suspend fun requirePermission(apiKey: String, permission: String) {
        require(repository.hasPermission(apiKey, permission)) { "Missing permission: $permission" }
    }
}

/**
 * WebFlux controller exposing the five chapter 12 production slices.
 */
@RestController
@RequestMapping("/production")
class SpringProductionController(
    private val service: SpringProductionWorkflowService,
) {
    @PostMapping("/accounts")
    suspend fun registerAccount(@RequestBody request: RegisterAccountRequest): AccountView =
        service.registerAccount(request)

    @PostMapping("/work-items")
    suspend fun createWorkItem(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestBody request: CreateWorkItemRequest,
    ): WorkItemView {
        service.requirePermission(apiKey, "work:create")
        return service.createWorkItem(request)
    }

    @GetMapping(path = ["/realtime"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun realtime(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestParam(defaultValue = "0") after: Long,
    ): reactor.core.publisher.Flux<ServerSentEvent<OutboxEventView>> {
        service.requirePermission(apiKey, "work:create")
        return realtimeEvents(after)
    }

    private fun realtimeEvents(after: Long) =
        flow {
            service.replayEvents(after).forEach { event ->
                emit(
                    ServerSentEvent.builder(event)
                        .id(event.sequence.toString())
                        .event(event.eventType)
                        .build()
                )
            }
        }.asFlux()

    @PostMapping("/outbound")
    suspend fun enqueueOutbound(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestBody request: EnqueueOutboundRequest,
    ): OutboundRequestView {
        service.requirePermission(apiKey, "outbound:create")
        return service.enqueueOutbound(request)
    }

    @GetMapping("/readiness")
    suspend fun readiness(): ReadinessView =
        service.readiness()
}

/**
 * Converts workshop exceptions into stable structured HTTP responses.
 */
@RestControllerAdvice
class SpringProductionErrorHandler {
    @ExceptionHandler(DuplicateIdempotencyKeyException::class)
    fun duplicateIdempotencyKey(exception: DuplicateIdempotencyKeyException): ResponseEntity<StructuredError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(StructuredError("IDEMPOTENCY_CONFLICT", exception.message ?: "Duplicate idempotency key"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException): ResponseEntity<StructuredError> =
        ResponseEntity.badRequest()
            .body(StructuredError("INVALID_REQUEST", exception.message ?: "Invalid request"))
}

private fun String.requireNotBlankField(fieldName: String) {
    require(isNotBlank()) { "$fieldName must not be blank" }
}

private fun String.requireAllowedTargetUrl() {
    require(startsWith("https://example.test/")) { "targetUrl must use the example.test HTTPS host" }
}
