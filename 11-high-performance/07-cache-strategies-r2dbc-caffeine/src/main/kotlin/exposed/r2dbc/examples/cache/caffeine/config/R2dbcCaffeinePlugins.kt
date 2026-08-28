package exposed.r2dbc.examples.cache.caffeine.config

import exposed.r2dbc.examples.cache.caffeine.domain.StructuredError
import exposed.r2dbc.examples.cache.caffeine.service.InvalidProductRequestException
import exposed.r2dbc.examples.cache.caffeine.service.ProductNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** 테스트와 애플리케이션이 공유하는 결정적 JSON 설정입니다. */
val R2dbcCaffeineJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

/** 입력/도메인 오류를 구조화하고 예외 세부 정보를 HTTP에 노출하지 않습니다. */
fun Application.installR2dbcCaffeinePlugins() {
    install(ContentNegotiation) {
        json(R2dbcCaffeineJson)
    }
    install(StatusPages) {
        exception<InvalidProductRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                StructuredError(code = "INVALID_REQUEST", message = cause.message ?: "Invalid request"),
            )
        }
        exception<ProductNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                StructuredError(code = "NOT_FOUND", message = cause.message ?: "Product not found"),
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, StructuredError("INVALID_JSON", "Request body is not valid JSON"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, StructuredError("INVALID_JSON", "Request body is not valid JSON"))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.respond(HttpStatusCode.InternalServerError, StructuredError("INTERNAL_ERROR", "Request failed"))
        }
    }
}
