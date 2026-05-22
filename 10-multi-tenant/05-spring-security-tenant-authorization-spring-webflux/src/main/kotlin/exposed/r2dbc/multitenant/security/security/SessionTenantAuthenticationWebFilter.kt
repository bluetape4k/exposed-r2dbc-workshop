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
 * Authenticates fixed demo session IDs without adding a login flow.
 */
@Component
class SessionTenantAuthenticationWebFilter: WebFilter {

    companion object {
        /** Demo session header. This is not a production session cookie. */
        const val SESSION_HEADER = "X-DEMO-SESSION"

        private val Sessions = mapOf(
            "korean-session" to Tenant.KOREAN,
            "english-session" to Tenant.ENGLISH,
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val sessionId = exchange.request.headers.getFirst(SESSION_HEADER) ?: return chain.filter(exchange)
        val tenant = Sessions[sessionId] ?: return unauthorized(exchange)
        return chain
            .filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(TenantAuthenticationToken("demo-session", tenant)))
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
