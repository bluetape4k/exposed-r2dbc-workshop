package exposed.r2dbc.examples.cache.controller


import exposed.r2dbc.examples.cache.domain.model.UserDTO
import exposed.r2dbc.examples.cache.domain.repository.UserCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val repository: UserCacheRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping
    suspend fun findAll(@RequestParam(name = "limit") limit: Int? = null): List<UserDTO> {
        log.debug { "Finding all users with limit: $limit" }
        return suspendTransaction(readOnly = true) {
            repository.findAll(limit = limit, where = { Op.TRUE }).toList()
        }
    }

    @GetMapping("/{id}")
    suspend fun get(@PathVariable(name = "id") id: Long): UserDTO? {
        log.debug { "Getting user with id: $id" }
        return suspendTransaction(readOnly = true) {
            repository.get(id)
        }
    }

    @GetMapping("/all")
    suspend fun getAll(@RequestParam(name = "ids") ids: List<Long>): List<UserDTO> {
        log.debug { "Getting all users with ids: $ids" }
        return suspendTransaction(readOnly = true) {
            repository.getAll(ids)
        }
    }

    @DeleteMapping("/invalidate")
    suspend fun invalidate(@RequestParam(name = "ids") ids: List<Long>): Long {
        if (ids.isEmpty()) {
            return 0
        }
        log.debug { "Invalidating cache for ids: $ids" }
        return suspendTransaction {
            repository.invalidate(*ids.toTypedArray())
        }
    }

    @DeleteMapping("/invalidate/all")
    suspend fun invalidateAll() {
        suspendTransaction {
            repository.invalidateAll()
        }
    }

    @DeleteMapping("/invalidate/pattern")
    suspend fun invalidateByPattern(@RequestParam(name = "patterns") pattern: String): Long {
        return suspendTransaction {
            repository.invalidateByPattern(pattern)
        }
    }

    @PostMapping
    suspend fun put(@RequestBody userDTO: UserDTO): UserDTO {
        log.debug { "Updating user with id: ${userDTO.id}" }
        suspendTransaction { repository.put(userDTO) }
        return userDTO
    }
}
