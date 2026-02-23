package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.domain.model.UserEventRecord
import exposed.r2dbc.examples.cache.domain.repository.UserEventCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 이벤트 캐시 적재 API를 제공합니다.
 */
@RestController
@RequestMapping("/user-events")
class UserEventController(
    private val repository: UserEventCacheRepository,
) {

    companion object: KLoggingChannel()

    /**
     * 단일 사용자 이벤트를 저장합니다.
     */
    @PostMapping
    suspend fun insert(@RequestBody userEvent: UserEventRecord): Boolean {
        log.debug { "Inserting user event: $userEvent" }
        return repository.put(userEvent) ?: false
    }

    /**
     * 복수 사용자 이벤트를 일괄 저장합니다.
     */
    @PostMapping("/bulk")
    suspend fun insertBulk(@RequestBody userEvents: List<UserEventRecord>): Boolean {
        log.debug { "Inserting user events. count: ${userEvents.size}" }
        repository.putAll(userEvents)
        return true
    }
}
