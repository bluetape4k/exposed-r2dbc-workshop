package exposed.r2dbc.multitenant.connectionfactory.tenant

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.bluetape4k.r2dbc.pool.R2dbcConnectionFactoryRegistry
import io.r2dbc.pool.ConnectionPool
import org.junit.jupiter.api.Test
import java.time.Duration

class TenantConnectionFactoryRegistryTest {

    @Test
    fun `owned provider registry disposes tenant pools idempotently`() {
        val korean = connectionPool("registry_lifecycle_korean")
        val english = connectionPool("registry_lifecycle_english")
        val registry = R2dbcConnectionFactoryRegistry.owned(
            mapOf(
                Tenants.Tenant.KOREAN to korean,
                Tenants.Tenant.ENGLISH to english,
            ),
        )

        registry.dispose()
        registry.dispose()

        registry.isDisposed() shouldBeEqualTo true
        korean.isDisposed shouldBeEqualTo true
        english.isDisposed shouldBeEqualTo true
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
