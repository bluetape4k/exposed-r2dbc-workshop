package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.tenant.TenantId
import io.bluetape4k.tenant.reactor.ReactorTenantContext
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * tenant header를 검증하고 정규화한 tenant ID를 Reactor context에 저장합니다.
 */
@Component
class TenantFilter: WebFilter {

    companion object: KLoggingChannel() {
        /** HTTP header used to select the tenant. */
        const val TENANT_HEADER = "X-TENANT-ID"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val tenantId = TenantId(resolveTenantId(exchange))
        log.debug { "Resolved tenantId: ${tenantId.value}" }
        return chain
            .filter(exchange)
            .contextWrite { context ->
                ReactorTenantContext.withTenant(context, tenantId)
            }
    }

    private fun resolveTenantId(exchange: ServerWebExchange): String {
        return TenantIdResolver.resolve(exchange.request.headers.getFirst(TENANT_HEADER))
    }
}
