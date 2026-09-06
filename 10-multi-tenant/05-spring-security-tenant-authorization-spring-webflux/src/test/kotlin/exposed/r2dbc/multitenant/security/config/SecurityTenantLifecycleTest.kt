package exposed.r2dbc.multitenant.security.config

import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.r2dbc.pool.R2dbcConnectionFactoryRegistry
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import org.awaitility.kotlin.await
import org.springframework.boot.test.context.TestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import java.time.Duration

class SecurityTenantLifecycleTest {

    @Test
    fun `spring destroy method disposes registry-owned pools`() {
        val korean = testPool("spring_lifecycle_korean")
        val english = testPool("spring_lifecycle_english")
        val registry = R2dbcConnectionFactoryRegistry.owned(
            mapOf(
                Tenant.KOREAN to korean,
                Tenant.ENGLISH to english,
            ),
        )
        RegistryLifecycleHolder.registry = registry

        AnnotationConfigApplicationContext(RegistryLifecycleConfiguration::class.java).use { context ->
            context.getBean(R2dbcConnectionFactoryRegistry::class.java)
        }

        await.atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .until { registry.isDisposed() && korean.isDisposed && english.isDisposed }
    }

    private fun testPool(databaseName: String): ConnectionPool =
        connectionPoolOf(h2ConnectionFactoryOptions(databaseName)) {
            maxSize = 1
            initialSize = 0
            minIdle = 0
            maxCreateConnectionTime = Duration.ofSeconds(5)
            maxAcquireTime = Duration.ofSeconds(3)
        }
}

private object RegistryLifecycleHolder {
    lateinit var registry: R2dbcConnectionFactoryRegistry<Tenant>
}

@TestConfiguration(proxyBeanMethods = false)
private class RegistryLifecycleConfiguration {

    @Bean(destroyMethod = "dispose")
    fun isolatedRegistry(): R2dbcConnectionFactoryRegistry<Tenant> =
        RegistryLifecycleHolder.registry
}
