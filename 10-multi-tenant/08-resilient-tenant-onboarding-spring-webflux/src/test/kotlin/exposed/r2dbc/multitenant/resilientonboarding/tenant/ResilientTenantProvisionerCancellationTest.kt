package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResilientTenantProvisionerCancellationTest {

    @Test
    fun `cancellation retains failed metadata and closes created resources`() = runSuspendIO {
        val database = R2dbcDatabase.connect(
            ConnectionFactories.get("r2dbc:h2:mem:///tenant_lifecycle_cancellation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )
        val repository = TenantLifecycleRepository(database, Duration.ofMinutes(2))
        repository.initializeSchema()
        val factory = BlockingResourceFactory()
        val tenantId = TenantId("cancelled")
        val provisioner = ResilientTenantProvisioner(
            repository = repository,
            registry = TenantRuntimeRegistry(),
            resourceFactory = factory,
            properties = TenantLifecycleProperties(),
            clock = Clock.systemUTC(),
        )

        val job = async {
            provisioner.onboard(TenantOnboardingCommand(tenantId, "Cancelled"))
        }
        factory.createEntered.await()
        job.cancelAndJoin()

        assertEquals(TenantLifecycleStatus.FAILED, repository.find(tenantId)?.status)
        assertTrue(factory.closedTenantIds.contains(tenantId))
    }
}

private class BlockingResourceFactory: TenantRuntimeResourceFactory {
    val createEntered = CompletableDeferred<Unit>()
    val closedTenantIds = mutableListOf<TenantId>()

    override suspend fun create(metadata: TenantMetadata): TenantResources {
        return TenantResources(metadata.tenantId, ConnectionFactories.get("r2dbc:h2:mem:///never"))
    }

    override suspend fun probe(resources: TenantResources) {
        createEntered.complete(Unit)
        CompletableDeferred<Unit>().await()
    }

    override suspend fun close(resources: TenantResources) {
        closedTenantIds += resources.tenantId
    }
}
