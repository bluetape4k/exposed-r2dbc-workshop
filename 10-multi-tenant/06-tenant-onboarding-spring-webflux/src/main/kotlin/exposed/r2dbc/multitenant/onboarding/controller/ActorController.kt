package exposed.r2dbc.multitenant.onboarding.controller

import exposed.r2dbc.multitenant.onboarding.domain.model.ActorRecord
import exposed.r2dbc.multitenant.onboarding.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.onboarding.tenant.TenantHeaders
import exposed.r2dbc.multitenant.onboarding.tenant.TenantIdResolver
import exposed.r2dbc.multitenant.onboarding.tenant.TenantTransactionExecutor
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * Actor API backed by tenant-specific R2DBC connection factories.
 */
@RestController
@RequestMapping("/actors")
class ActorController(
    private val actorRepository: ActorR2dbcRepository,
    private val transactionExecutor: TenantTransactionExecutor,
) {

    companion object: KLoggingChannel()

    /**
     * Returns actors for the tenant selected by `X-TENANT-ID`.
     */
    @GetMapping
    suspend fun getAllActors(
        @RequestHeader(TenantHeaders.TENANT_HEADER) rawTenantId: String?,
    ): List<ActorRecord> =
        transactionExecutor.execute(TenantIdResolver.resolve(rawTenantId)) {
            actorRepository.findAll().toList()
        }

    /**
     * Returns one actor for the tenant selected by `X-TENANT-ID`.
     */
    @GetMapping("/{id}")
    suspend fun findById(
        @RequestHeader(TenantHeaders.TENANT_HEADER) rawTenantId: String?,
        @PathVariable id: Long,
    ): ActorRecord? =
        transactionExecutor.execute(TenantIdResolver.resolve(rawTenantId)) {
            actorRepository.findByIdOrNull(id)
        }
}
