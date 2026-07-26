package exposed.r2dbc.multitenant.resilientonboarding.tenant

interface TenantRuntimeResourceFactory {
    suspend fun create(metadata: TenantMetadata): TenantResources

    suspend fun restore(metadata: TenantMetadata): TenantResources = create(metadata)

    suspend fun probe(resources: TenantResources)

    suspend fun close(resources: TenantResources)
}
