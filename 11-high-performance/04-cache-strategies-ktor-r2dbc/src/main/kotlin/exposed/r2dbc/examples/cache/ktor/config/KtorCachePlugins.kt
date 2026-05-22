package exposed.r2dbc.examples.cache.ktor.config

import exposed.r2dbc.examples.cache.ktor.domain.StructuredError
import exposed.r2dbc.examples.cache.ktor.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.domain.UserCacheResponse
import exposed.r2dbc.examples.cache.ktor.domain.UserNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import java.time.format.DateTimeParseException

internal val KtorCacheJson: Json = Json {
    ignoreUnknownKeys = false
}

/**
 * Installs JSON and structured error handling for the cache strategy example.
 */
fun Application.installKtorCachePlugins() {
    install(ContentNegotiation) {
        json(KtorCacheJson)
    }
    install(StatusPages) {
        exception<UserNotFoundException> { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                UserCacheResponse(cacheStatus = CacheStatus.NOT_FOUND, user = null)
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
        exception<DateTimeParseException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_REQUEST", message = "Invalid date: ${cause.parsedString}")
            )
        }
    }
}
