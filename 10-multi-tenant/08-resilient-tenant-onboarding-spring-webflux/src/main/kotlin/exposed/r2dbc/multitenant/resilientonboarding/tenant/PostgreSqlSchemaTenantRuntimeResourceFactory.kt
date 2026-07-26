package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

class PostgreSqlSchemaTenantRuntimeResourceFactory(
    private val connectionFactory: ConnectionFactory,
    private val database: R2dbcDatabase,
): TenantRuntimeResourceFactory {

    override suspend fun create(metadata: TenantMetadata): TenantResources {
        val schemaName = TenantSchemaName.from(metadata.tenantId)
        val schema = Schema(schemaName.value)

        suspendTransaction(db = database) {
            SchemaUtils.createSchema(schema)
        }
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(schema)
            SchemaUtils.create(TenantReadinessTable)
            TenantReadinessTable.insertIgnore {
                it[tenantId] = metadata.tenantId.value
            }
        }
        return TenantResources(metadata.tenantId, connectionFactory, schemaName)
    }

    override suspend fun probe(resources: TenantResources) {
        val schemaName = requireNotNull(resources.schemaName) {
            "PostgreSQL tenant resources require a schema name"
        }
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(Schema(schemaName.value))
            check(
                TenantReadinessTable
                    .selectAll()
                    .where { TenantReadinessTable.tenantId eq resources.tenantId.value }
                    .count() == 1L
            ) {
                "Tenant readiness marker is missing"
            }
        }
    }

    override suspend fun close(resources: TenantResources) = Unit
}

private object TenantReadinessTable: Table("tenant_readiness") {
    val tenantId = varchar("tenant_id", 64)
    override val primaryKey = PrimaryKey(tenantId)
}
