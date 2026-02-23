package exposed.r2dbc.examples.routing.web

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 요청 헤더/경로 정보를 Reactor Context에 적재해 라우팅 힌트를 전달합니다.
 */
@Component
class TenantRoutingWebFilter(
    @Value("\${routing.r2dbc.default-tenant:default}")
    private val defaultTenant: String,
): WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> = mono {
        val tenant = exchange.request.headers.getFirst(TENANT_HEADER)
            ?.takeIf { it.isNotBlank() }
            ?: defaultTenant

        val readOnly = exchange.request.headers.getFirst(READ_ONLY_HEADER)
            ?.toBooleanStrictOrNull()
            ?: exchange.request.path.value().endsWith("/readonly")

        chain.filter(exchange)
            .contextWrite {
                it.put(RoutingContextKeys.TENANT, tenant)
                    .put(RoutingContextKeys.READ_ONLY, readOnly)
            }
            .awaitSingleOrNull()// awaitSingle() 을 사용하면, 전송 후에도 뭔가 처리하느라 예외가 발생함.
    }

    companion object {
        const val TENANT_HEADER = "X-Tenant-Id"
        const val READ_ONLY_HEADER = "X-Read-Only"
    }
}
