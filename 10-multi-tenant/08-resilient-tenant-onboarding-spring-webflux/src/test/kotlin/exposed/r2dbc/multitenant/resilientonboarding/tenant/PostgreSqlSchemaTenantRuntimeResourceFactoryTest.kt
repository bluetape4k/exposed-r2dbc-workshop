package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.testcontainers.database.PostgreSQLServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PostgreSqlSchemaTenantRuntimeResourceFactoryTest {

    private val postgres by lazy { PostgreSQLServer.Launcher.postgres }

    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var database: R2dbcDatabase
    private lateinit var factory: PostgreSqlSchemaTenantRuntimeResourceFactory

    @BeforeEach
    fun setUp() {
        connectionFactory = ConnectionFactories.get(connectionOptions())
        database = R2dbcDatabase.connect(
            connectionFactory,
            R2dbcDatabaseConfig { explicitDialect = PostgreSQLDialect() },
        )
        factory = PostgreSqlSchemaTenantRuntimeResourceFactory(connectionFactory, database)
    }

    @Test
    fun `create prepares a tenant schema and readiness marker`() = runSuspendIO {
        val tenantId = uniqueTenant("seoul")
        val resources = factory.create(metadata(tenantId))

        factory.probe(resources)

        assertEquals(TenantSchemaName.from(tenantId), resources.schemaName)
        assertEquals(tenantId.value, queryMarkerTenant(resources))
    }

    @Test
    fun `two tenants use different schemas on the same connection factory`() = runSuspendIO {
        val seoulId = uniqueTenant("seoul")
        val busanId = uniqueTenant("busan")
        val seoul = factory.create(metadata(seoulId))
        val busan = factory.create(metadata(busanId))

        factory.probe(seoul)
        factory.probe(busan)

        assertSame(seoul.connectionFactory, busan.connectionFactory)
        assertEquals(seoulId.value, queryMarkerTenant(seoul))
        assertEquals(busanId.value, queryMarkerTenant(busan))
    }

    @Test
    fun `closing failed resources does not drop the tenant schema`() = runSuspendIO {
        val resources = factory.create(metadata(uniqueTenant("failed")))

        factory.close(resources)

        assertEquals(resources.tenantId.value, queryMarkerTenant(resources))
    }

    @Test
    fun `runtime registry returns schema aware resources`() = runSuspendIO {
        val resources = factory.create(metadata(uniqueTenant("runtime")))
        val registry = TenantRuntimeRegistry()

        registry.publish(resources)

        assertEquals(resources, registry.resourcesOrNull(resources.tenantId))
    }

    @Test
    fun `creating the same tenant twice is idempotent`() = runSuspendIO {
        val tenantId = uniqueTenant("retry")

        val first = factory.create(metadata(tenantId))
        val second = factory.create(metadata(tenantId))

        factory.probe(first)
        factory.probe(second)
        assertEquals(tenantId.value, queryMarkerTenant(second))
    }

    private fun connectionOptions(): ConnectionFactoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, postgres.host)
            .option(ConnectionFactoryOptions.PORT, postgres.port)
            .option(ConnectionFactoryOptions.DATABASE, requireNotNull(postgres.databaseName))
            .option(ConnectionFactoryOptions.USER, requireNotNull(postgres.username))
            .option(ConnectionFactoryOptions.PASSWORD, requireNotNull(postgres.password))
            .option(ConnectionFactoryOptions.SSL, false)
            .build()

    private suspend fun queryMarkerTenant(resources: TenantResources): String =
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(Schema(requireNotNull(resources.schemaName).value))
            TenantReadinessProbeTable
                .selectAll()
                .where { TenantReadinessProbeTable.tenantId eq resources.tenantId.value }
                .first()[TenantReadinessProbeTable.tenantId]
        }

    private fun uniqueTenant(prefix: String): TenantId =
        TenantId("$prefix-${UUID.randomUUID().toString().take(8)}")

    private fun metadata(tenantId: TenantId): TenantMetadata =
        TenantMetadata(
            tenantId = tenantId,
            displayName = tenantId.value,
            status = TenantLifecycleStatus.PROVISIONING,
            attempt = 1,
            reservationToken = UUID.randomUUID(),
            version = 0,
            leaseExpiresAt = Instant.parse("2026-07-26T00:02:00Z"),
            lastFailureCode = null,
            createdAt = Instant.parse("2026-07-26T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-26T00:00:00Z"),
        )
}

private object TenantReadinessProbeTable: Table("tenant_readiness") {
    val tenantId = varchar("tenant_id", 64)
    override val primaryKey = PrimaryKey(tenantId)
}
