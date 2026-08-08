package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.r2dbc.pool.connectionFactoryOf
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Mono

class H2TenantRuntimeResourceFactory: TenantRuntimeResourceFactory {

    override suspend fun create(metadata: TenantMetadata): TenantResources =
        TenantResources(
            tenantId = metadata.tenantId,
            connectionFactory = connectionFactoryOf(
                "r2dbc:h2:mem:///resilient_tenant_${metadata.tenantId.value};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            ),
        )

    override suspend fun probe(resources: TenantResources) {
        val connection = Mono.from(resources.connectionFactory.create()).awaitSingle()
        Mono.from(connection.close()).awaitSingleOrNull()
    }

    override suspend fun close(resources: TenantResources) = Unit
}
