package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Validates the tenant header and stores the normalized tenant ID in Reactor context.
 */
@Component
class TenantFilter: WebFilter {

    companion object: KLoggingChannel() {
        /** HTTP header used to select the tenant. */
        const val TENANT_HEADER = "X-TENANT-ID"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = resolveTenantId(exchange)
        log.debug { "Resolved tenantId: $tenantId" }
        return chain
            .filter(exchange)
            .contextWrite { it.put(TenantContextKeys.TENANT_ID, tenantId) }
    }

    private fun resolveTenantId(exchange: ServerWebExchange): String {
        return TenantIdResolver.resolve(exchange.request.headers.getFirst(TENANT_HEADER))
    }
}
