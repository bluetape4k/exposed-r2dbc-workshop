package exposed.r2dbc.examples.routing.ktor.config

import exposed.r2dbc.examples.routing.ktor.domain.StructuredError
import exposed.r2dbc.examples.routing.ktor.routing.InvalidRoutingRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

internal val KtorRoutingDatasourceJson: Json = Json {
    ignoreUnknownKeys = false
}

/**
 * Installs JSON serialization and stable routing error responses.
 */
fun Application.installKtorRoutingDatasourcePlugins() {
    install(ContentNegotiation) {
        json(KtorRoutingDatasourceJson)
    }
    install(StatusPages) {
        exception<InvalidRoutingRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_ROUTING_REQUEST", message = cause.message ?: "Invalid routing request")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_ROUTING_REQUEST", message = cause.message ?: "Invalid routing request")
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_JSON", message = "Request body is not valid JSON")
            )
        }
    }
}
