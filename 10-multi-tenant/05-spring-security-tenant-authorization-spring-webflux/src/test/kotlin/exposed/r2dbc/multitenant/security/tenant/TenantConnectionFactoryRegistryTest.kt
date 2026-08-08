package exposed.r2dbc.multitenant.security.tenant

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import org.junit.jupiter.api.Test
import java.time.Duration

class TenantConnectionFactoryRegistryTest {

    @Test
    fun `destroy disposes tenant pools idempotently`() {
        val registry = TenantConnectionFactoryRegistry(
            mapOf(
                Tenants.Tenant.KOREAN to connectionPool("registry_lifecycle_korean"),
                Tenants.Tenant.ENGLISH to connectionPool("registry_lifecycle_english"),
            ),
        )

        registry.destroy()
        registry.destroy()

        (registry.get(Tenants.Tenant.KOREAN) as ConnectionPool).isDisposed shouldBeEqualTo true
        (registry.get(Tenants.Tenant.ENGLISH) as ConnectionPool).isDisposed shouldBeEqualTo true
    }

    private fun connectionPool(databaseName: String): ConnectionPool =
        connectionPoolOf(h2ConnectionFactoryOptions(databaseName)) {
            maxSize = 1
            initialSize = 0
            minIdle = 0
            maxCreateConnectionTime = Duration.ofSeconds(5)
            maxAcquireTime = Duration.ofSeconds(3)
        }
}
