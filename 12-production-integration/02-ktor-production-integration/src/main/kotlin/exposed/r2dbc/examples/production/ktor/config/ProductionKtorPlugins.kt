package exposed.r2dbc.examples.production.ktor.config

import exposed.r2dbc.examples.production.ktor.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.ktor.app.PermissionDeniedException
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

internal val ProductionJson: Json = Json {
    ignoreUnknownKeys = false
}

internal fun Application.installProductionKtorPlugins() {
    install(ContentNegotiation) {
        json(ProductionJson)
    }
    install(StatusPages) {
        exception<DuplicateIdempotencyKeyException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                StructuredError("IDEMPOTENCY_CONFLICT", cause.message ?: "Duplicate idempotency key")
            )
        }
        exception<PermissionDeniedException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, StructuredError("FORBIDDEN", cause.message ?: "Permission denied"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, StructuredError("INVALID_REQUEST", cause.message ?: "Invalid request"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, StructuredError("INVALID_JSON", "Request body is not valid JSON"))
        }
    }
}
