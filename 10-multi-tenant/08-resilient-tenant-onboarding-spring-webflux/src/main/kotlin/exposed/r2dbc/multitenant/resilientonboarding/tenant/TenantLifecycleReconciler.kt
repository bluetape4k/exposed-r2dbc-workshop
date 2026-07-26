package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.warn
import java.time.Instant

class TenantLifecycleReconciler(
    private val repository: TenantLifecycleRepository,
    private val registry: TenantRuntimeRegistry,
    private val resourceFactory: TenantRuntimeResourceFactory,
) {

    companion object: KLoggingChannel()

    suspend fun reconcile(now: Instant) {
        repository.findExpiredProvisioning(now).forEach { metadata ->
            repository.markFailed(metadata.asOwner(), TenantFailureCode.RECOVERY, now)
        }
        repository.findActive().forEach { metadata ->
            publishIfHealthy(metadata, now)
        }
    }

    private suspend fun publishIfHealthy(
        metadata: TenantMetadata,
        now: Instant,
    ) {
        var resources: TenantResources? = null
        try {
            resources = resourceFactory.create(metadata)
            resourceFactory.probe(resources)
            registry.publish(resources)
        } catch (cause: Exception) {
            resources?.let { resourceFactory.close(it) }
            repository.markFailed(metadata.asOwner(), TenantFailureCode.PROBE, now)
            log.warn(cause) { "Tenant recovery probe failed. tenantId=${metadata.tenantId.value}" }
        }
    }
}
