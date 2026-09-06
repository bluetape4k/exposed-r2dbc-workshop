package exposed.r2dbc.multitenant.connectionfactory.config

import exposed.r2dbc.multitenant.connectionfactory.AbstractMultitenantTest
import exposed.r2dbc.multitenant.connectionfactory.controller.ActorController
import exposed.r2dbc.multitenant.connectionfactory.tenant.DataInitializer
import exposed.r2dbc.multitenant.connectionfactory.tenant.TenantInitializer
import exposed.r2dbc.multitenant.connectionfactory.tenant.TenantTransactionExecutor
import exposed.r2dbc.multitenant.connectionfactory.tenant.Tenants.Tenant
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.bluetape4k.r2dbc.pool.R2dbcConnectionFactoryRegistry
import io.r2dbc.pool.ConnectionPool
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class ConnectionFactoryTenantR2dbcConfigTest(
    @param:Autowired private val actorController: ActorController,
    @param:Autowired private val registry: R2dbcConnectionFactoryRegistry<Tenant>,
    @param:Autowired private val dataInitializer: DataInitializer,
    @param:Autowired private val tenantInitializer: TenantInitializer,
    @param:Autowired private val transactionExecutor: TenantTransactionExecutor,
    @param:Autowired private val properties: TenantConnectionFactoryProperties,
    @param:Qualifier("tenantRoutingDatabase")
    @param:Autowired private val tenantRoutingDatabase: R2dbcDatabase,
): AbstractMultitenantTest() {

    @Test
    fun `context loading`() {
        actorController.shouldNotBeNull()
        registry.shouldNotBeNull()
        dataInitializer.shouldNotBeNull()
        tenantInitializer.shouldNotBeNull()
        transactionExecutor.shouldNotBeNull()
        tenantRoutingDatabase.shouldNotBeNull()
    }

    @Test
    fun `registry contains distinct tenant factories`() {
        registry.keys.map(Tenant::id).toSet() shouldBeEqualTo setOf(Tenant.KOREAN.id, Tenant.ENGLISH.id)
        assertNotSame(registry[Tenant.KOREAN], registry[Tenant.ENGLISH])
    }

    @Test
    fun `tenant h2 urls use distinct database names`() {
        properties.definitionFor(Tenant.KOREAN).url.contains("tenant_cf_korean") shouldBeEqualTo true
        properties.definitionFor(Tenant.ENGLISH).url.contains("tenant_cf_english") shouldBeEqualTo true
    }

    @Test
    fun `partial startup disposes pools when later tenant warmup fails`() {
        val first = testPool("partial_startup_first")
        val second = testPool("partial_startup_second")
        val expected = IllegalStateException("warmup failed")

        val failure = assertFailsWith<IllegalStateException> {
            createTenantPools(
                tenants = listOf("first", "second"),
                poolFactory = { tenant -> if (tenant == "first") first else second },
                warmup = { pool -> if (pool === second) throw expected else Unit },
            )
        }

        failure shouldBeEqualTo expected
        first.isDisposed shouldBeEqualTo true
        second.isDisposed shouldBeEqualTo true
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
