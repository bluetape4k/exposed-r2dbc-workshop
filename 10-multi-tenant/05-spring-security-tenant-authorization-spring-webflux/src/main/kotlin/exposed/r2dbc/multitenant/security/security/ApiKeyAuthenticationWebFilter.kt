package exposed.r2dbc.multitenant.security.security

import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Authenticates fixed demo API keys for the workshop example.
 */
@Component
class ApiKeyAuthenticationWebFilter: WebFilter {

    companion object {
        /** Demo API-key header. */
        const val API_KEY_HEADER = "X-API-KEY"

        private val ApiKeys = mapOf(
            "demo-korean-key" to Tenant.KOREAN,
            "demo-english-key" to Tenant.ENGLISH,
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val apiKey = exchange.request.headers.getFirst(API_KEY_HEADER) ?: return chain.filter(exchange)
        val tenant = ApiKeys[apiKey] ?: return unauthorized(exchange)
        return chain
            .filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(TenantAuthenticationToken("api-key", tenant)))
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
