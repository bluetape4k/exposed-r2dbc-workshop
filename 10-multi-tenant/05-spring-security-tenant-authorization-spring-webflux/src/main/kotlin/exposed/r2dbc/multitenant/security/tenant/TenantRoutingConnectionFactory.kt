package exposed.r2dbc.multitenant.security.tenant

import org.springframework.r2dbc.connection.lookup.AbstractRoutingConnectionFactory
import reactor.core.publisher.Mono

/**
 * Spring R2DBC routing factory that reads the tenant ID from Reactor context.
 */
class TenantRoutingConnectionFactory: AbstractRoutingConnectionFactory() {

    override fun determineCurrentLookupKey(): Mono<Any> =
        Mono.deferContextual { contextView ->
            if (contextView.hasKey(TenantContextKeys.TENANT_ID)) {
                Mono.just(contextView.get<String>(TenantContextKeys.TENANT_ID))
            } else {
                Mono.empty()
            }
        }
}
