package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
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

    private fun connectionPool(databaseName: String): ConnectionPool {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PROTOCOL, "mem")
            .option(ConnectionFactoryOptions.DATABASE, databaseName)
            .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
            .option(Option.valueOf("DB_CLOSE_ON_EXIT"), "FALSE")
            .build()
        val config = ConnectionPoolConfiguration
            .builder(ConnectionFactories.get(options))
            .maxSize(1)
            .maxCreateConnectionTime(Duration.ofSeconds(5))
            .maxAcquireTime(Duration.ofSeconds(3))
            .build()
        return ConnectionPool(config)
    }
}
