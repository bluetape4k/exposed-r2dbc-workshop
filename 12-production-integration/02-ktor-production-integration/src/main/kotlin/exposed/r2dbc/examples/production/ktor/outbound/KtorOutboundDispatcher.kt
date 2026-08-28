package exposed.r2dbc.examples.production.ktor.outbound

import exposed.r2dbc.examples.production.ktor.app.OutboundDelivery
import exposed.r2dbc.examples.production.ktor.app.OutboundDispatchResult
import exposed.r2dbc.examples.production.ktor.app.OutboundRequestView
import io.bluetape4k.http.ktor.KtorClientTimeouts
import io.bluetape4k.http.ktor.ktorCioJsonHttpClientOf
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

/** 기본 outbound client가 유지해야 하는 JSON 직렬화 의미입니다. */
internal val defaultOutboundClientJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** helper가 설치하는 client-level timeout을 명시적으로 고정합니다. */
internal val defaultOutboundClientTimeouts: KtorClientTimeouts = KtorClientTimeouts()

/** CIO JSON helper로 기본 outbound client를 생성합니다. */
internal fun defaultOutboundClient(): HttpClient =
    ktorCioJsonHttpClientOf(
        json = defaultOutboundClientJson,
        timeouts = defaultOutboundClientTimeouts,
    )
