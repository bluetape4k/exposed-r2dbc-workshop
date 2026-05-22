package exposed.r2dbc.multitenant.connectionfactory.controller

import exposed.r2dbc.multitenant.connectionfactory.domain.model.ActorRecord
import exposed.r2dbc.multitenant.connectionfactory.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.connectionfactory.tenant.TenantTransactionExecutor
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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
    suspend fun getAllActors(): List<ActorRecord> =
        transactionExecutor.execute {
            actorRepository.findAll().toList()
        }

    /**
     * Returns one actor for the tenant selected by `X-TENANT-ID`.
     */
    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ActorRecord? =
        transactionExecutor.execute {
            actorRepository.findByIdOrNull(id)
        }
}
