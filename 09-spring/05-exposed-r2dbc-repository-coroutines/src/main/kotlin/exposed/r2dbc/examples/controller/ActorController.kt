package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.examples.dto.ActorDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/actors")
class ActorController(
    private val actorRepository: ActorR2dbcRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{id}")
    suspend fun getActorById(@PathVariable("id") actorId: Long): ActorDTO? =
        suspendTransaction(readOnly = true) {
            actorRepository.findById(actorId)
        }

    /**
     * `Flow<ActorDTO>` 를 반환할 수는 없다. Transaction Context의 범위를 넘어서기 때문이다.
     */
    @GetMapping
    suspend fun searchActors(request: ServerHttpRequest): List<ActorDTO> {
        val params = request.queryParams.map { it.key to it.value.firstOrNull() }.toMap()

        return when {
            params.isEmpty() -> emptyList()
            else -> suspendTransaction(readOnly = true) {
                actorRepository.searchActors(params).toList()
            }
        }
    }

    @PostMapping
    suspend fun saveActor(@RequestBody actor: ActorDTO): ActorDTO =
        suspendTransaction {
            actorRepository.save(actor)
        }

    @DeleteMapping("/{id}")
    suspend fun deleteActor(@PathVariable("id") actorId: Long): Int =
        suspendTransaction {
            actorRepository.deleteById(actorId)
        }

}
