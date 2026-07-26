package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.r2dbc.spi.ConnectionFactories
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class TenantLifecycleRestartIntegrationTest {

    private val now: Instant = Instant.parse("2026-07-26T01:00:00Z")

    @Test
    fun `active tenant is recovered from durable state after application restart`(
        @TempDir tempDir: Path,
    ) = runSuspendIO {
        val tenantId = TenantId("restart-ready")
        val databaseUrl = fileDatabaseUrl(tempDir.resolve("tenant-lifecycle"))

        val repositoryBeforeRestart = TenantLifecycleRepository(
            database = connect(databaseUrl),
            leaseDuration = Duration.ofMinutes(2),
        )
        repositoryBeforeRestart.initializeSchema()
        val owner = repositoryBeforeRestart.claim(tenantId, "Restart Ready", now) as TenantClaim.Owner
        repositoryBeforeRestart.markActive(owner, now.plusSeconds(1)).shouldNotBeNull()

        val registryAfterRestart = TenantRuntimeRegistry()
        registryAfterRestart.connectionFactoryOrNull(tenantId).shouldBeNull()
        val resourceFactory = RestartRecordingResourceFactory(
            delegate = H2TenantRuntimeResourceFactory(),
            registry = registryAfterRestart,
        )
        val repositoryAfterRestart = TenantLifecycleRepository(
            database = connect(databaseUrl),
            leaseDuration = Duration.ofMinutes(2),
        )
        val persistedMetadata = repositoryAfterRestart.find(tenantId).shouldNotBeNull()
        val reconciler = TenantLifecycleReconciler(
            repository = repositoryAfterRestart,
            registry = registryAfterRestart,
            resourceFactory = resourceFactory,
        )

        reconciler.reconcile(now.plusSeconds(2))

        persistedMetadata.status shouldBeEqualTo TenantLifecycleStatus.ACTIVE
        resourceFactory.interactions shouldBeEqualTo listOf(
            "create:${tenantId.value}",
            "probe:${tenantId.value}",
        )
        registryAfterRestart.connectionFactoryOrNull(tenantId).shouldNotBeNull()
        repositoryAfterRestart.find(tenantId) shouldBeEqualTo persistedMetadata
    }

    private fun connect(databaseUrl: String): R2dbcDatabase =
        R2dbcDatabase.connect(
            ConnectionFactories.get(databaseUrl),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )

    private fun fileDatabaseUrl(path: Path): String =
        "r2dbc:h2:file:///${path.toAbsolutePath()}?mode=PostgreSQL"
}

private class RestartRecordingResourceFactory(
    private val delegate: TenantRuntimeResourceFactory,
    private val registry: TenantRuntimeRegistry,
): TenantRuntimeResourceFactory {

    val interactions = mutableListOf<String>()

    override suspend fun create(metadata: TenantMetadata): TenantResources =
        delegate.create(metadata).also {
            interactions += "create:${metadata.tenantId.value}"
        }

    override suspend fun probe(resources: TenantResources) {
        registry.connectionFactoryOrNull(resources.tenantId).shouldBeNull()
        delegate.probe(resources)
        interactions += "probe:${resources.tenantId.value}"
    }

    override suspend fun close(resources: TenantResources) {
        delegate.close(resources)
    }
}
