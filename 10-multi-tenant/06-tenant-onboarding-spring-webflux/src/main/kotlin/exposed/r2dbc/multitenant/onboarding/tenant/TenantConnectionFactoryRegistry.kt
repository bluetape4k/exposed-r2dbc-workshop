package exposed.r2dbc.multitenant.onboarding.tenant

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime registry for onboarded tenant pools and Exposed database wrappers.
 */
@Component
class TenantConnectionFactoryRegistry: DisposableBean {

    companion object: KLogging()

    private data class TenantResources(
        val pool: ConnectionPool,
        val database: R2dbcDatabase,
    )

    private val resources = ConcurrentHashMap<TenantId, TenantResources>()
    private val tenantMutexes = ConcurrentHashMap<TenantId, Mutex>()

    val onboardingMutex = Mutex()

    fun mutexFor(tenantId: TenantId): Mutex =
        tenantMutexes.computeIfAbsent(tenantId) { Mutex() }

    fun register(
        tenantId: TenantId,
        pool: ConnectionPool,
        database: R2dbcDatabase,
    ) {
        resources[tenantId] = TenantResources(pool, database)
    }

    fun unregister(tenantId: TenantId): ConnectionPool? =
        resources.remove(tenantId)?.pool

    fun getConnectionFactory(tenantId: TenantId): ConnectionFactory =
        resources[tenantId]?.pool ?: throw UnknownTenantException(tenantId)

    fun getDatabase(tenantId: TenantId): R2dbcDatabase =
        resources[tenantId]?.database ?: throw UnknownTenantException(tenantId)

    fun contains(tenantId: TenantId): Boolean =
        resources.containsKey(tenantId)

    fun activeTenantCount(): Int =
        resources.size

    override fun destroy() {
        resources.entries.forEach { (tenantId, resource) ->
            closePool(tenantId, resource.pool)
        }
        resources.clear()
    }

    private fun closePool(tenantId: TenantId, pool: ConnectionPool) {
        try {
            pool.dispose()
        } catch (e: Exception) {
            log.warn(e) { "Failed to close tenant pool. tenantId=$tenantId" }
        }
    }
}
