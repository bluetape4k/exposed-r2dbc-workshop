package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.r2dbc.spi.ConnectionFactory
import java.util.concurrent.ConcurrentHashMap

data class TenantResources(
    val tenantId: TenantId,
    val connectionFactory: ConnectionFactory,
    val schemaName: TenantSchemaName? = null,
)

class TenantRuntimeRegistry {

    private val resources = ConcurrentHashMap<TenantId, TenantResources>()

    fun publish(resources: TenantResources) {
        this.resources[resources.tenantId] = resources
    }

    fun unregister(tenantId: TenantId): TenantResources? = resources.remove(tenantId)

    fun resourcesOrNull(tenantId: TenantId): TenantResources? = resources[tenantId]

    fun connectionFactoryOrNull(tenantId: TenantId): ConnectionFactory? =
        resources[tenantId]?.connectionFactory
}
