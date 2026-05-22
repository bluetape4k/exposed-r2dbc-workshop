package exposed.r2dbc.examples.production.spring.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

internal const val REQUEST_ID_HEADER = "X-Request-ID"
internal const val REQUEST_ID_ATTRIBUTE = "requestId"

private val RequestIdPattern = Regex("[A-Za-z0-9._-]{1,64}")

internal fun sanitizeRequestId(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { RequestIdPattern.matches(it) }

internal fun ServerWebExchange.requestId(): String =
    attributes[REQUEST_ID_ATTRIBUTE]?.toString().orEmpty()

/**
 * Adds a safe request id to every Spring WebFlux response before security and routes run.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SpringRequestCorrelationFilter: WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = sanitizeRequestId(exchange.request.requestIdHeader())
            ?: UUID.randomUUID().toString()

        exchange.attributes[REQUEST_ID_ATTRIBUTE] = requestId
        exchange.response.headers.set(REQUEST_ID_HEADER, requestId)
        return chain.filter(exchange)
            .contextWrite { context -> context.put(REQUEST_ID_ATTRIBUTE, requestId) }
    }

    private fun ServerHttpRequest.requestIdHeader(): String? =
        headers.getFirst(REQUEST_ID_HEADER)
}
