package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.domain.model.UserEventRecord
import exposed.r2dbc.examples.cache.domain.repository.UserEventCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user-events")
class UserEventController(
    private val repository: UserEventCacheRepository,
) {

    companion object: KLoggingChannel()

    @PostMapping
    suspend fun insert(@RequestBody userEvent: UserEventRecord): Boolean {
        log.debug { "Inserting user event: $userEvent" }
        return repository.put(userEvent) ?: false
    }

    @PostMapping("/bulk")
    suspend fun insertBulk(@RequestBody userEvents: List<UserEventRecord>): Boolean {
        log.debug { "Inserting user events. count: ${userEvents.size}" }
        repository.putAll(userEvents)
        return true
    }
}
