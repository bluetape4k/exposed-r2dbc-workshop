package exposed.r2dbc.examples.cache.controller


import exposed.r2dbc.examples.cache.domain.model.UserRecord
import exposed.r2dbc.examples.cache.domain.repository.UserCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.jetbrains.exposed.v1.core.Op
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 캐시(읽기/쓰기/무효화) 관리 API를 제공합니다.
 */
@RestController
@RequestMapping("/users")
class UserController(
    private val repository: UserCacheRepository,
) {

    companion object: KLoggingChannel()

    /**
     * 사용자 목록을 조회합니다.
     */
    @GetMapping
    suspend fun findAll(@RequestParam(name = "limit") limit: Int? = null): List<UserRecord> {
        log.debug { "Finding all users with limit: $limit" }
        return repository.findAll(limit = limit, where = { Op.TRUE }).toList()
    }

    /**
     * ID로 사용자 한 건을 조회합니다.
     */
    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: Long): UserRecord? {
        log.debug { "Getting user with id: $id" }
        return repository.get(id)
    }

    /**
     * 복수 ID로 사용자 목록을 조회합니다.
     */
    @GetMapping("/all")
    suspend fun getAll(@RequestParam(name = "ids") ids: List<Long>): List<UserRecord> {
        log.debug { "Getting all users with ids: $ids" }
        return repository.getAll(ids)
    }

    /**
     * 지정한 사용자 키의 캐시를 무효화합니다.
     */
    @DeleteMapping("/invalidate")
    suspend fun invalidate(@RequestParam(name = "ids") ids: List<Long>): Long {
        if (ids.isEmpty()) {
            return 0
        }
        log.debug { "Invalidating cache for ids: $ids" }
        return repository.invalidate(*ids.toTypedArray())
    }

    /**
     * 사용자 캐시 전체를 무효화합니다.
     */
    @DeleteMapping("/invalidate/all")
    suspend fun invalidateAll() {
        repository.invalidateAll()
    }

    /**
     * 패턴 기반으로 사용자 캐시를 무효화합니다.
     */
    @DeleteMapping("/invalidate/pattern")
    suspend fun invalidateByPattern(@RequestParam(name = "patterns") pattern: String): Long {
        return repository.invalidateByPattern(pattern)
    }

    /**
     * 사용자 정보를 저장(업서트)합니다.
     */
    @PostMapping
    suspend fun put(@RequestBody userRecord: UserRecord): UserRecord {
        log.debug { "Updating user with id: ${userRecord.id}" }
        repository.put(userRecord)
        return userRecord
    }
}
