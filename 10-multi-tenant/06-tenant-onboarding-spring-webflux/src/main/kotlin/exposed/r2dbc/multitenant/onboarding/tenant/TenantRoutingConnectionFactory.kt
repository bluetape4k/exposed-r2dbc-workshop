package exposed.r2dbc.multitenant.onboarding.tenant

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import reactor.core.publisher.Mono

/**
 * Dynamic routing connection factory that resolves tenant pools at request time.
 */
class TenantRoutingConnectionFactory(
    private val registry: TenantConnectionFactoryRegistry,
): ConnectionFactory {

    override fun create(): Mono<out Connection> =
        Mono.deferContextual { context ->
            val rawTenantId = if (context.hasKey(TenantContextKeys.TENANT_ID)) {
                context.get<String>(TenantContextKeys.TENANT_ID)
            } else {
                null
            }
            val tenantId = TenantIdResolver.resolve(rawTenantId)
            Mono.from(registry.getConnectionFactory(tenantId).create())
        }

    override fun getMetadata(): ConnectionFactoryMetadata =
        ConnectionFactoryMetadata { "H2" }
}
