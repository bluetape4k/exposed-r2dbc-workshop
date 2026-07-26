package exposed.r2dbc.multitenant.resilientonboarding.tenant

import exposed.r2dbc.multitenant.resilientonboarding.ResilientTenantOnboardingApp
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.testcontainers.database.PostgreSQLServer
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PostgreSqlTenantLifecycleRestartIntegrationTest {

    private val postgres by lazy { PostgreSQLServer.Launcher.postgres }

    @Test
    fun `active tenant is republished after PostgreSQL application restart`() = runSuspendIO {
        val tenantId = uniqueTenant("restart")

        postgresContext().use { beforeRestart ->
            val provisioner = beforeRestart.getBean(ResilientTenantProvisioner::class.java)
            assertIs<TenantOnboardingResult.Created>(
                provisioner.onboard(TenantOnboardingCommand(tenantId, "Restart PostgreSQL")),
            )
        }

        postgresContext().use { afterRestart ->
            val repository = afterRestart.getBean(TenantLifecycleRepository::class.java)
            val registry = afterRestart.getBean(TenantRuntimeRegistry::class.java)

            assertEquals(TenantLifecycleStatus.ACTIVE, repository.find(tenantId)?.status)
            assertEquals(
                TenantSchemaName.from(tenantId),
                assertNotNull(registry.resourcesOrNull(tenantId)).schemaName,
            )
        }
    }

    @Test
    fun `active lifecycle row without a ready schema becomes recovery failure`() = runSuspendIO {
        val tenantId = uniqueTenant("missing")
        val now = Instant.now()

        postgresContext().use { context ->
            val repository = context.getBean(TenantLifecycleRepository::class.java)
            val owner = repository.claim(tenantId, "Missing Schema", now) as TenantClaim.Owner
            assertNotNull(repository.markActive(owner, now.plusSeconds(1)))
        }

        postgresContext().use { restarted ->
            val repository = restarted.getBean(TenantLifecycleRepository::class.java)
            val registry = restarted.getBean(TenantRuntimeRegistry::class.java)
            val metadata = assertNotNull(repository.find(tenantId))

            assertEquals(TenantLifecycleStatus.FAILED, metadata.status)
            assertEquals(TenantFailureCode.RECOVERY, metadata.lastFailureCode)
            assertNull(registry.resourcesOrNull(tenantId))
        }
    }

    @Test
    fun `probe failure keeps the tenant schema and retry succeeds`() = runSuspendIO {
        val tenantId = uniqueTenant("failed")

        postgresContext().use { context ->
            val repository = context.getBean(TenantLifecycleRepository::class.java)
            val registry = context.getBean(TenantRuntimeRegistry::class.java)
            val delegate = context.getBean(TenantRuntimeResourceFactory::class.java)
            val properties = context.getBean(TenantLifecycleProperties::class.java)
            val clock = context.getBean(Clock::class.java)
            val database = context.getBean(R2dbcDatabase::class.java)
            val failingFactory = object: TenantRuntimeResourceFactory by delegate {
                override suspend fun probe(resources: TenantResources) {
                    error("simulated probe failure")
                }
            }
            val provisioner = ResilientTenantProvisioner(
                repository,
                registry,
                failingFactory,
                properties,
                clock,
            )

            assertIs<TenantOnboardingResult.Failed>(
                provisioner.onboard(TenantOnboardingCommand(tenantId, "Failed PostgreSQL")),
            )

            assertEquals(tenantId.value, readinessMarker(database, tenantId))
            assertEquals(1L, publicLifecycleRowCount(database, tenantId))
            assertNull(registry.resourcesOrNull(tenantId))

            assertIs<TenantOnboardingResult.Created>(
                context.getBean(ResilientTenantProvisioner::class.java)
                    .onboard(TenantOnboardingCommand(tenantId, "Failed PostgreSQL")),
            )
            assertEquals(TenantLifecycleStatus.ACTIVE, repository.find(tenantId)?.status)
            assertEquals(
                TenantSchemaName.from(tenantId),
                assertNotNull(registry.resourcesOrNull(tenantId)).schemaName,
            )
        }
    }

    private fun postgresContext() =
        SpringApplicationBuilder(ResilientTenantOnboardingApp::class.java)
            .profiles("postgres")
            .web(WebApplicationType.NONE)
            .run(
                "--app.resilient-onboarding.postgres.host=${postgres.host}",
                "--app.resilient-onboarding.postgres.port=${postgres.port}",
                "--app.resilient-onboarding.postgres.database=${requireNotNull(postgres.databaseName)}",
                "--app.resilient-onboarding.postgres.username=${requireNotNull(postgres.username)}",
                "--app.resilient-onboarding.postgres.password=${requireNotNull(postgres.password)}",
                "--spring.main.banner-mode=off",
            )

    private fun uniqueTenant(prefix: String): TenantId =
        TenantId("$prefix-${UUID.randomUUID().toString().take(8)}")

    private suspend fun readinessMarker(
        database: R2dbcDatabase,
        tenantId: TenantId,
    ): String =
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(Schema(TenantSchemaName.from(tenantId).value))
            TenantReadinessIntegrationTable
                .selectAll()
                .where { TenantReadinessIntegrationTable.tenantId eq tenantId.value }
                .single()[TenantReadinessIntegrationTable.tenantId]
        }

    private suspend fun publicLifecycleRowCount(
        database: R2dbcDatabase,
        tenantId: TenantId,
    ): Long =
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(Schema("public"))
            TenantLifecycleIntegrationTable
                .selectAll()
                .where { TenantLifecycleIntegrationTable.tenantId eq tenantId.value }
                .count()
        }
}

private object TenantReadinessIntegrationTable: Table("tenant_readiness") {
    val tenantId = varchar("tenant_id", 64)
}

private object TenantLifecycleIntegrationTable: Table("tenant_lifecycle") {
    val tenantId = varchar("tenant_id", 64)
}
