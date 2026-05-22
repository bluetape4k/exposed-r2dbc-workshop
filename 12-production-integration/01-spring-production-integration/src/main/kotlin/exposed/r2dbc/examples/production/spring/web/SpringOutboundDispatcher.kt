package exposed.r2dbc.examples.production.spring.web

import exposed.r2dbc.examples.production.spring.app.OutboundDelivery
import exposed.r2dbc.examples.production.spring.app.OutboundDispatchResult
import exposed.r2dbc.examples.production.spring.app.OutboundRequestView
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitExchange

/**
 * Dispatches persisted outbound requests through Spring WebFlux WebClient.
 */
@Component
class SpringOutboundDispatcher(
    private val webClient: WebClient,
) : OutboundDelivery {
    override suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult =
        webClient.post()
            .uri(request.targetUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .header(IDEMPOTENCY_KEY_HEADER, request.idempotencyKey)
            .bodyValue(request.payload)
            .awaitExchange { response ->
                OutboundDispatchResult(statusCode = response.statusCode().value())
            }

    private companion object {
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
