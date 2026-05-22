package exposed.r2dbc.examples.production.ktor.config

import exposed.r2dbc.examples.production.ktor.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.ktor.app.PermissionDeniedException
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import java.util.UUID

internal val ProductionJson: Json = Json {
    ignoreUnknownKeys = false
}

internal const val REQUEST_ID_HEADER = "X-Request-ID"

private val RequestIdPattern = Regex("[A-Za-z0-9._-]{1,64}")

internal fun sanitizeRequestId(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { RequestIdPattern.matches(it) }

internal fun Application.installProductionKtorPlugins() {
    install(CallId) {
        retrieve { call -> sanitizeRequestId(call.request.header(REQUEST_ID_HEADER)) }
        generate { UUID.randomUUID().toString() }
        verify { callId -> RequestIdPattern.matches(callId) }
        replyToHeader(REQUEST_ID_HEADER)
    }
    install(CallLogging) {
        callIdMdc("requestId")
    }
    install(ContentNegotiation) {
        json(ProductionJson)
    }
    install(StatusPages) {
        exception<DuplicateIdempotencyKeyException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                StructuredError(
                    code = "IDEMPOTENCY_CONFLICT",
                    message = cause.message ?: "Duplicate idempotency key",
                    requestId = call.callId.orEmpty(),
                )
            )
        }
        exception<PermissionDeniedException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                StructuredError(
                    code = "FORBIDDEN",
                    message = cause.message ?: "Permission denied",
                    requestId = call.callId.orEmpty(),
                )
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(
                    code = "INVALID_REQUEST",
                    message = cause.message ?: "Invalid request",
                    requestId = call.callId.orEmpty(),
                )
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(
                    code = "INVALID_JSON",
                    message = "Request body is not valid JSON",
                    requestId = call.callId.orEmpty(),
                )
            )
        }
    }
}
