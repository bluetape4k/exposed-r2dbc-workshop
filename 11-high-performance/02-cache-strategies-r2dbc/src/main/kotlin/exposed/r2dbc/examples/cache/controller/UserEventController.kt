package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.domain.model.UserEventDTO
import exposed.r2dbc.examples.cache.domain.repository.UserEventCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user-events")
class UserEventController(
    private val repository: UserEventCacheRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @PostMapping
    suspend fun insert(@RequestBody userEvent: UserEventDTO): Boolean {
        log.debug { "Inserting user event: $userEvent" }
        return suspendTransaction {
            repository.put(userEvent) ?: false
        }
    }

    @PostMapping("/bulk")
    suspend fun insertBulk(@RequestBody userEvents: List<UserEventDTO>): Boolean {
        log.debug { "Inserting user events: $userEvents" }
        suspendTransaction {
            repository.putAll(userEvents)
        }
        return true
    }
}
