package exposed.r2dbc.examples.production.ktor.outbound

import exposed.r2dbc.examples.production.ktor.app.OutboundRequestView
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

/**
 * Dispatches persisted outbound requests through a Ktor HTTP client.
 */
class KtorOutboundDispatcher(
    private val client: HttpClient,
) {
    suspend fun dispatch(request: OutboundRequestView): HttpStatusCode =
        client.post(request.targetUrl) {
            setBody(request.payload)
        }.status
}
