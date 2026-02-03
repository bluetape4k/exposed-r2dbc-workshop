package exposed.r2dbc.multitenant.webflux.controller

import exposed.r2dbc.multitenant.webflux.domain.dto.ActorDTO
import exposed.r2dbc.multitenant.webflux.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.webflux.tenant.suspendTransactionWithCurrentTenant
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/actors")
class ActorController(
    private val actorRepository: ActorR2dbcRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping
    suspend fun getAllActors(): List<ActorDTO> =
        suspendTransactionWithCurrentTenant {
            actorRepository.findAll().toFastList()
        }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ActorDTO? =
        suspendTransactionWithCurrentTenant {
            actorRepository.findByIdOrNull(id)
        }
}
