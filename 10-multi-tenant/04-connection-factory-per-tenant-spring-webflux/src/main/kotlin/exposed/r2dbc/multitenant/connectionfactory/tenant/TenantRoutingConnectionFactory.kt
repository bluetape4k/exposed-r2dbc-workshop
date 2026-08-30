package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.tenant.reactor.ReactorTenantContext
import org.springframework.r2dbc.connection.lookup.AbstractRoutingConnectionFactory
import reactor.core.publisher.Mono

/**
 * Reactor context의 tenant ID를 읽어 Spring R2DBC connection factory를 라우팅합니다.
 */
class TenantRoutingConnectionFactory: AbstractRoutingConnectionFactory() {

    override fun determineCurrentLookupKey(): Mono<Any> =
        Mono.deferContextual { contextView ->
            ReactorTenantContext.currentOrNull(contextView)
                ?.value
                ?.let { Mono.just(it) }
                ?: Mono.empty()
        }
}
