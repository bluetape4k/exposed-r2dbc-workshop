package exposed.r2dbc.multitenant.security.tenant

import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registry that owns tenant-specific R2DBC connection pools.
 */
class TenantConnectionFactoryRegistry(
    private val pools: Map<Tenant, ConnectionPool>,
): DisposableBean {

    private val destroyed = AtomicBoolean(false)

    /** Returns the tenant-owned connection factory. */
    fun get(tenant: Tenant): ConnectionFactory =
        pools[tenant] ?: error("No ConnectionFactory for tenant '${tenant.id}'")

    /** Returns the configured tenant IDs. */
    fun keys(): Set<String> =
        pools.keys.mapTo(linkedSetOf()) { it.id }

    /** Returns the target map shape expected by Spring's routing connection factory. */
    fun targetConnectionFactories(): Map<String, ConnectionFactory> =
        pools.entries.associate { (tenant, factory) -> tenant.id to factory }

    override fun destroy() {
        if (destroyed.compareAndSet(false, true)) {
            pools.values.forEach(ConnectionPool::dispose)
        }
    }
}
