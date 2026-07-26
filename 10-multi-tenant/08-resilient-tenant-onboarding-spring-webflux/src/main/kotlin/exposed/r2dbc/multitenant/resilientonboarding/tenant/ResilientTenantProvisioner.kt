package exposed.r2dbc.multitenant.resilientonboarding.tenant

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

data class TenantOnboardingCommand(
    val tenantId: TenantId,
    val displayName: String,
)

sealed interface TenantOnboardingResult {
    data class Created(val metadata: TenantMetadata): TenantOnboardingResult
    data class Active(val metadata: TenantMetadata): TenantOnboardingResult
    data class Pending(val metadata: TenantMetadata): TenantOnboardingResult
    data class Conflict(val metadata: TenantMetadata): TenantOnboardingResult
    data class Failed(val metadata: TenantMetadata): TenantOnboardingResult
}

class ResilientTenantProvisioner(
    private val repository: TenantLifecycleRepository,
    private val registry: TenantRuntimeRegistry,
    private val resourceFactory: TenantRuntimeResourceFactory,
    private val properties: TenantLifecycleProperties,
    private val clock: Clock,
) {

    suspend fun onboard(command: TenantOnboardingCommand): TenantOnboardingResult =
        withTimeout(properties.overallProvisionTimeout.toMillis()) {
            when (val claim = repository.claim(command.tenantId, command.displayName, clock.instant())) {
                is TenantClaim.Active -> TenantOnboardingResult.Active(claim.metadata)
                is TenantClaim.Pending -> TenantOnboardingResult.Pending(claim.metadata)
                is TenantClaim.Conflict -> TenantOnboardingResult.Conflict(claim.metadata)
                is TenantClaim.Owner -> provision(claim)
            }
        }

    private suspend fun provision(owner: TenantClaim.Owner): TenantOnboardingResult {
        var currentOwner = owner
        var resources: TenantResources? = null
        try {
            currentOwner = repository.renewLease(currentOwner, clock.instant())
                ?: return TenantOnboardingResult.Pending(owner.metadata)
            resources = resourceFactory.create(currentOwner.metadata)
            currentOwner = repository.renewLease(currentOwner, clock.instant())
                ?: return TenantOnboardingResult.Pending(currentOwner.metadata)
            resourceFactory.probe(resources)
            val active = repository.markActive(currentOwner, clock.instant())
                ?: return TenantOnboardingResult.Pending(currentOwner.metadata)
            registry.publish(resources)
            return TenantOnboardingResult.Created(active)
        } catch (cause: CancellationException) {
            cleanupFailedAttempt(currentOwner, resources, TenantFailureCode.PUBLISH)
            throw cause
        } catch (cause: Exception) {
            cleanupFailedAttempt(currentOwner, resources, TenantFailureCode.SCHEMA)
            val failedMetadata = repository.find(currentOwner.metadata.tenantId) ?: currentOwner.metadata
            return TenantOnboardingResult.Failed(failedMetadata)
        }
    }

    private suspend fun cleanupFailedAttempt(
        owner: TenantClaim.Owner,
        resources: TenantResources?,
        code: TenantFailureCode,
    ) {
        withContext(NonCancellable) {
            repository.markFailed(owner, code, clock.instant())
            registry.unregister(owner.metadata.tenantId)
            resources?.let { resourceFactory.close(it) }
        }
    }
}
