package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.domain.model.UserCredentialsDTO
import exposed.r2dbc.examples.cache.domain.repository.UserCredentialsCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.transaction.TransactionManager
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user-credentials")
class UserCredentialsController(
    private val repository: UserCredentialsCacheRepository,
    private val transactionManager: TransactionManager,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping
    suspend fun findAll(@RequestParam(name = "limit") limit: Int? = null): List<UserCredentialsDTO> {
        log.debug { "Finding all user credentials with limit: $limit" }
        return suspendTransaction(readOnly = true) {
            repository.findAll(limit = limit).toList()
        }
    }

    @GetMapping("/{id}")
    suspend fun get(@PathVariable(name = "id") id: String): UserCredentialsDTO? {
        log.debug { "Getting user credentials with id: $id" }
        return suspendTransaction(readOnly = true) {
            repository.get(id)
        }
    }

    @GetMapping("/all")
    suspend fun getAll(@RequestParam(name = "ids") ids: List<String>): List<UserCredentialsDTO> {
        log.debug { "Getting all user credentials with ids: $ids" }
        return suspendTransaction(readOnly = true) {
            repository.getAll(ids)
        }
    }

    @DeleteMapping("/invalidate")
    suspend fun invalidate(@RequestParam(name = "ids") ids: List<String>): Long {
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
            log.debug { "Invalidating all user credentials cache" }
            repository.invalidateAll()
        }

    }

    @DeleteMapping("/invalidate/pattern")
    suspend fun invalidatePattern(@RequestParam(name = "pattern") pattern: String): Long {
        if (pattern.isEmpty()) {
            return 0
        }
        log.debug { "Invalidating cache for pattern: $pattern" }
        return suspendTransaction {
            repository.invalidateByPattern(pattern)
        }
    }

}
