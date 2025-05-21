package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.domain.ActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.repository.ActorRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val actorRepository: ActorRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{id}")
    suspend fun getActorById(@PathVariable("id") actorId: Long): ActorDTO? {
        return suspendTransaction(readOnly = true) {
            log.debug { "current transaction=$this" }
            actorRepository.findById(actorId)
        }
    }

    @GetMapping
    suspend fun searchActors(request: ServerHttpRequest): List<ActorDTO> {
        val params = request.queryParams.map { it.key to it.value.first() }.toMap()
        return when {
            params.isEmpty() -> suspendTransaction(readOnly = true) {
                actorRepository.findAll()
            }
            else -> suspendTransaction(readOnly = true) {
                actorRepository.searchActor(params)
            }
        }
    }

    @PostMapping
    suspend fun createActor(@RequestBody actor: ActorDTO): ActorDTO =
        suspendTransaction {
            actorRepository.create(actor)
        }

    @DeleteMapping("/{id}")
    suspend fun deleteActor(@PathVariable("id") actorId: Long): Int =
        suspendTransaction {
            actorRepository.deleteById(actorId)
        }
}
