package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

class PostgreSqlSchemaTenantRuntimeResourceFactory(
    private val connectionFactory: ConnectionFactory,
    private val database: R2dbcDatabase,
): TenantRuntimeResourceFactory {

    override suspend fun create(metadata: TenantMetadata): TenantResources =
        error("PostgreSQL tenant schema provisioning is not implemented")

    override suspend fun probe(resources: TenantResources): Unit =
        error("PostgreSQL tenant schema probe is not implemented")

    override suspend fun close(resources: TenantResources) = Unit
}
