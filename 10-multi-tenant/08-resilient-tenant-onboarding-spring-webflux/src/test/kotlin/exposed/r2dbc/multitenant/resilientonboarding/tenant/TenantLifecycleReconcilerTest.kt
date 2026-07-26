package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.r2dbc.spi.ConnectionFactories
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TenantLifecycleReconcilerTest {

    private lateinit var repository: TenantLifecycleRepository
    private lateinit var registry: TenantRuntimeRegistry
    private lateinit var factory: RecordingResourceFactory
    private lateinit var reconciler: TenantLifecycleReconciler

    private val now: Instant = Instant.parse("2026-07-26T00:10:00Z")

    @BeforeEach
    fun setUp() = runSuspendIO {
        val database = R2dbcDatabase.connect(
            ConnectionFactories.get("r2dbc:h2:mem:///tenant_lifecycle_reconciler;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )
        repository = TenantLifecycleRepository(database, Duration.ofMinutes(2))
        repository.initializeSchema()
        repository.deleteAll()
        registry = TenantRuntimeRegistry()
        factory = RecordingResourceFactory()
        reconciler = TenantLifecycleReconciler(repository, registry, factory)
    }

    @Test
    fun `expired provisioning is retained as recovery failure`() = runSuspendIO {
        repository.claim(TenantId("late"), "Late", now.minusSeconds(180))

        reconciler.reconcile(now)

        assertEquals(TenantLifecycleStatus.FAILED, repository.find(TenantId("late"))?.status)
        assertEquals(TenantFailureCode.RECOVERY, repository.find(TenantId("late"))?.lastFailureCode)
    }

    @Test
    fun `active tenant is published only after probe`() = runSuspendIO {
        val owner = repository.claim(TenantId("ready"), "Ready", now) as TenantClaim.Owner
        repository.markActive(owner, now)

        reconciler.reconcile(now)

        assertEquals(listOf(TenantId("ready")), factory.probedTenantIds)
        assertNotNull(registry.connectionFactoryOrNull(TenantId("ready")))
    }

    @Test
    fun `active tenant probe failure is recorded as recovery failure`() = runSuspendIO {
        val tenantId = TenantId("broken")
        val owner = repository.claim(tenantId, "Broken", now) as TenantClaim.Owner
        repository.markActive(owner, now)
        factory.failingTenantId = tenantId

        reconciler.reconcile(now.plusSeconds(1))

        repository.find(tenantId).let { metadata ->
            assertEquals(TenantLifecycleStatus.FAILED, metadata?.status)
            assertEquals(TenantFailureCode.RECOVERY, metadata?.lastFailureCode)
        }
        assertNull(registry.connectionFactoryOrNull(tenantId))
    }
}

private class RecordingResourceFactory: TenantRuntimeResourceFactory {
    val probedTenantIds = mutableListOf<TenantId>()
    var failingTenantId: TenantId? = null

    override suspend fun create(metadata: TenantMetadata): TenantResources =
        TenantResources(
            tenantId = metadata.tenantId,
            connectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///tenant_resource_${metadata.tenantId.value};DB_CLOSE_DELAY=-1"),
        )

    override suspend fun probe(resources: TenantResources) {
        check(resources.tenantId != failingTenantId) { "simulated recovery probe failure" }
        probedTenantIds += resources.tenantId
    }

    override suspend fun close(resources: TenantResources) = Unit
}
