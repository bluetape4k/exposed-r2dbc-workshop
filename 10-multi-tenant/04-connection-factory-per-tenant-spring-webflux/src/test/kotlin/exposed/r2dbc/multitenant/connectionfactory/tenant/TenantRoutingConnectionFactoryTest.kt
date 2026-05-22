package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class TenantRoutingConnectionFactoryTest {

    @Test
    fun `no reactor tenant uses default tenant factory`() {
        val factory = routingFactory()

        StepVerifier
            .create(Mono.from(factory.create()))
            .expectErrorSatisfies {
                (it as SelectedTenantException).tenantId shouldBeEqualTo Tenants.Tenant.KOREAN.id
            }
            .verify()
    }

    @Test
    fun `known reactor tenant routes to matching factory`() {
        val factory = routingFactory()

        StepVerifier
            .create(
                Mono
                    .from(factory.create())
                    .contextWrite { it.put(TenantContextKeys.TENANT_ID, Tenants.Tenant.ENGLISH.id) },
            )
            .expectErrorSatisfies {
                (it as SelectedTenantException).tenantId shouldBeEqualTo Tenants.Tenant.ENGLISH.id
            }
            .verify()
    }

    @Test
    fun `unknown emitted tenant fails with lenient fallback disabled`() {
        val factory = routingFactory()

        StepVerifier
            .create(
                Mono
                    .from(factory.create())
                    .contextWrite { it.put(TenantContextKeys.TENANT_ID, "unknown") },
            )
            .expectError(IllegalStateException::class.java)
            .verify()
    }

    private fun routingFactory(): TenantRoutingConnectionFactory =
        TenantRoutingConnectionFactory().apply {
            val korean = FailingConnectionFactory(Tenants.Tenant.KOREAN.id)
            val english = FailingConnectionFactory(Tenants.Tenant.ENGLISH.id)
            setTargetConnectionFactories(
                mapOf(
                    Tenants.Tenant.KOREAN.id to korean,
                    Tenants.Tenant.ENGLISH.id to english,
                ),
            )
            setDefaultTargetConnectionFactory(korean)
            setLenientFallback(false)
            afterPropertiesSet()
        }

    private class FailingConnectionFactory(private val tenantId: String): ConnectionFactory {

        override fun create(): Publisher<out Connection> =
            Mono.error(SelectedTenantException(tenantId))

        override fun getMetadata(): ConnectionFactoryMetadata =
            ConnectionFactoryMetadata { "tenant-$tenantId" }
    }

    private class SelectedTenantException(val tenantId: String): RuntimeException(tenantId)
}
