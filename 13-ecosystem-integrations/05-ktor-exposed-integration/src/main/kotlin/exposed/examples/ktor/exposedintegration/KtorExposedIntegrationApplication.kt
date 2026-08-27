package exposed.examples.ktor.exposedintegration

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.Duration
import java.io.Serializable as JavaSerializable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** R2DBC로 저장하는 Ktor 통합 예제의 노트 테이블입니다. */
internal object WorkshopNotes: LongIdTable("ktor_exposed_notes") {
    val title = varchar("title", 120)
    val body = text("body")
}

@Serializable
internal data class NoteRequest(
    val title: String,
    val body: String,
): JavaSerializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Serializable
internal data class NoteResponse(
    val id: Long,
    val title: String,
    val body: String,
): JavaSerializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Serializable
internal data class HealthResponse(
    val status: String,
    val components: Map<String, String> = emptyMap(),
): JavaSerializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        fun up(components: Map<String, String> = emptyMap()): HealthResponse =
            HealthResponse(status = "UP", components = components)

        fun down(components: Map<String, String> = emptyMap()): HealthResponse =
            HealthResponse(status = "DOWN", components = components)
    }
}

@Serializable
internal data class ErrorResponse(
    val code: String,
    val message: String,
): JavaSerializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** R2DBC 계층의 오류를 Ktor StatusPages로 안전하게 변환하는 예외입니다. */
internal class R2dbcDatabaseFailure(message: String): RuntimeException(message)

/** 노트 CRUD를 담당하며 모든 DB 호출을 Exposed R2DBC suspend transaction으로 감쌉니다. */
internal class KtorExposedIntegrationRepository(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    suspend fun initialize() {
        ensureSchema()
    }

    suspend fun create(request: NoteRequest): NoteResponse {
        ensureSchema()
        val title = request.title.trim().requireNotBlank("title")
        val body = request.body.trim().requireNotBlank("body")
        return suspendTransaction(db = database) {
            val id = WorkshopNotes.insertAndGetId {
                it[WorkshopNotes.title] = title
                it[WorkshopNotes.body] = body
            }
            WorkshopNotes
                .selectAll()
                .where { WorkshopNotes.id eq id }
                .single()
                .toNoteResponse()
        }
    }

    suspend fun findAll(): List<NoteResponse> {
        ensureSchema()
        return suspendTransaction(db = database) {
            WorkshopNotes
                .selectAll()
                .orderBy(WorkshopNotes.id to SortOrder.ASC)
                .toList()
                .map { it.toNoteResponse() }
        }
    }

    suspend fun readiness(): HealthResponse {
        return try {
            ensureSchema()
            suspendTransaction(db = database) {
                WorkshopNotes.selectAll().limit(1).toList()
            }
            HealthResponse.up(mapOf("r2dbc" to "UP"))
        } catch (ex: R2dbcDatabaseFailure) {
            throw ex
        } catch (ex: Throwable) {
            throw R2dbcDatabaseFailure("R2DBC readiness probe failed")
        }
    }

    private suspend fun ensureSchema() {
        if (schemaReady.get()) return
        schemaMutex.withLock {
            if (schemaReady.get()) return
            suspendTransaction(db = database) {
                SchemaUtils.create(WorkshopNotes)
            }
            schemaReady.set(true)
        }
    }

    private fun String.requireNotBlank(field: String): String {
        require(isNotBlank()) { "$field must not be blank" }
        return this
    }
}

/** Ktor 애플리케이션이 소유하고 종료하는 R2DBC 풀과 Exposed Database입니다. */
internal class KtorExposedIntegrationResources private constructor(
    internal val r2dbcPool: ConnectionPool,
    internal val r2dbcDatabase: R2dbcDatabase,
) : AutoCloseable {

    val repository: KtorExposedIntegrationRepository =
        KtorExposedIntegrationRepository(r2dbcDatabase)

    override fun close() {
        r2dbcPool.dispose()
    }

    companion object {
        fun create(name: String = "default"): KtorExposedIntegrationResources {
            val options = h2ConnectionFactoryOptions(
                database = "ktor-exposed-integration-$name",
                closeOnExit = false,
            )
            val pool = connectionPoolOf(options) {
                maxSize = 2
                initialSize = 1
                minIdle = 0
                maxCreateConnectionTime = Duration.ofSeconds(5)
                maxAcquireTime = Duration.ofSeconds(3)
            }
            val config = R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions = options
            }
            return KtorExposedIntegrationResources(
                r2dbcPool = pool,
                r2dbcDatabase = R2dbcDatabase.connect(pool, config),
            )
        }
    }
}

/** R2DBC 전용 Ktor Exposed 통합 예제를 설치합니다. */
internal fun Application.installKtorExposedIntegrationWorkshop(
    resources: KtorExposedIntegrationResources,
) {
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }

    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    install(StatusPages) {
        exception<R2dbcDatabaseFailure> { call, _ ->
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("R2DBC_DATABASE_UNAVAILABLE", "Database is unavailable"),
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_REQUEST", cause.message ?: "Invalid request"),
            )
        }
        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Unexpected error"),
            )
        }
    }

    routing {
        route("/api/notes") {
            post {
                val note = resources.repository.create(call.receive())
                call.respond(HttpStatusCode.Created, note)
            }
            get {
                call.respond(resources.repository.findAll())
            }
        }
        get("/healthz/exposed") {
            call.respond(HealthResponse.up(mapOf("exposed" to "UP")))
        }
        get("/readyz/exposed") {
            call.respond(resources.repository.readiness())
        }
        get("/api/failures/sql") {
            throw R2dbcDatabaseFailure(
                "r2dbc:h2:mem:secret;password=top-secret;select * from notes",
            )
        }
    }
}

fun main() = runBlocking {
    val resources = KtorExposedIntegrationResources.create()
    resources.repository.initialize()
    embeddedServer(Netty, port = 8080) {
        installKtorExposedIntegrationWorkshop(resources)
    }.start(wait = true)
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toNoteResponse(): NoteResponse =
    NoteResponse(
        id = this[WorkshopNotes.id].value,
        title = this[WorkshopNotes.title],
        body = this[WorkshopNotes.body],
    )
