package exposed.r2dbc.multitenant.ktor.config

import exposed.r2dbc.multitenant.ktor.domain.model.StructuredError
import exposed.r2dbc.multitenant.ktor.tenant.InvalidTenantException
import exposed.r2dbc.multitenant.ktor.tenant.TenantPlugin
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

internal val KtorMultitenantJson: Json = Json {
    ignoreUnknownKeys = false
}

/**
 * Installs JSON, error handling, and tenant resolution for this Ktor example.
 */
fun Application.installKtorMultitenantPlugins() {
    install(ContentNegotiation) {
        json(KtorMultitenantJson)
    }
    install(StatusPages) {
        exception<InvalidTenantException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_TENANT", message = cause.message ?: "Invalid tenant")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_REQUEST", message = cause.message ?: "Invalid request")
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_JSON", message = "Request body is not valid JSON")
            )
        }
    }
    install(TenantPlugin)
}
