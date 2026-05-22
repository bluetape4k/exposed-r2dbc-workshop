package exposed.r2dbc.examples.cache.ktor.coroutines.config

import exposed.r2dbc.examples.cache.ktor.coroutines.domain.StructuredError
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheResponse
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CacheLoadTimeoutException
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UserNotFoundException
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

internal val KtorCoroutineCacheJson: Json = Json {
    ignoreUnknownKeys = false
}

/**
 * Installs JSON and structured error handling for the cache strategy example.
 */
fun Application.installKtorCoroutineCachePlugins() {
    install(ContentNegotiation) {
        json(KtorCoroutineCacheJson)
    }
    install(StatusPages) {
        exception<UserNotFoundException> { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                CoroutineUserCacheResponse(cacheStatus = CacheStatus.NOT_FOUND, user = null)
            )
        }
        exception<CacheLoadTimeoutException> { call, cause ->
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                StructuredError(code = "CACHE_LOAD_TIMEOUT", message = cause.message ?: "Cache load timed out")
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
