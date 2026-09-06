package exposed.r2dbc.multitenant.security.config

import exposed.r2dbc.multitenant.security.AbstractMultitenantTest
import exposed.r2dbc.multitenant.security.controller.ActorController
import exposed.r2dbc.multitenant.security.tenant.DataInitializer
import exposed.r2dbc.multitenant.security.tenant.TenantInitializer
import exposed.r2dbc.multitenant.security.tenant.TenantTransactionExecutor
import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.r2dbc.pool.R2dbcConnectionFactoryRegistry
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import kotlin.test.assertNotSame

class SecurityTenantR2dbcConfigTest(
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
}
