package exposed.r2dbc.examples.production.ktor.outbound

import exposed.r2dbc.examples.production.ktor.app.OutboundDelivery
import exposed.r2dbc.examples.production.ktor.app.OutboundDispatchResult
import exposed.r2dbc.examples.production.ktor.app.OutboundRequestView
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 저장된 outbound 요청을 Ktor HTTP client로 dispatch합니다.
 */
class KtorOutboundDispatcher(
    private val client: HttpClient = defaultOutboundClient(),
) : OutboundDelivery, AutoCloseable {
    override suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult {
        val response = client.post(request.targetUrl) {
            contentType(ContentType.Application.Json)
            header(IDEMPOTENCY_KEY_HEADER, request.idempotencyKey)
            setBody(request.payload)
        }
        return OutboundDispatchResult(statusCode = response.status.value)
    }

    override fun close() {
        client.close()
    }

    private companion object {
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}

private fun defaultOutboundClient(): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }
    }
