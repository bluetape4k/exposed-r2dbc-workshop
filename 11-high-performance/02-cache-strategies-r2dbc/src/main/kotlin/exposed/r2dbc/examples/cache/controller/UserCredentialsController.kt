package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.domain.model.UserCredentialsRecord
import exposed.r2dbc.examples.cache.domain.repository.UserCredentialsCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 인증 정보 캐시 조회/무효화 API를 제공합니다.
 */
@RestController
@RequestMapping("/user-credentials")
class UserCredentialsController(
    private val repository: UserCredentialsCacheRepository,
) {
    companion object: KLoggingChannel()

    /**
     * 사용자 인증 정보 목록을 조회합니다.
     */
    @GetMapping
    suspend fun findAll(@RequestParam(name = "limit") limit: Int? = null): List<UserCredentialsRecord> {
        log.debug { "Finding all user credentials with limit: $limit" }
        return repository.findAll(limit = limit).toList()
    }

    /**
     * ID로 사용자 인증 정보 한 건을 조회합니다.
     */
    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): UserCredentialsRecord? {
        log.debug { "Getting user credentials with id: $id" }
        return repository.get(id)
    }

    /**
     * 복수 ID로 사용자 인증 정보를 조회합니다.
     */
    @GetMapping("/all")
    suspend fun getAll(@RequestParam(name = "ids") ids: List<String>): List<UserCredentialsRecord> {
        log.debug { "Getting all user credentials with ids: $ids" }
        return repository.getAll(ids)
    }

    /**
     * 지정한 키의 인증 정보 캐시를 무효화합니다.
     */
    @DeleteMapping("/invalidate")
    suspend fun invalidate(@RequestParam(name = "ids") ids: List<String>): Long {
        if (ids.isEmpty()) {
            return 0
        }
        log.debug { "Invalidating cache for ids: $ids" }
        return repository.invalidate(*ids.toTypedArray())
    }

    /**
     * 인증 정보 캐시 전체를 무효화합니다.
     */
    @DeleteMapping("/invalidate/all")
    suspend fun invalidateAll() {
        log.debug { "Invalidating all user credentials cache" }
        repository.invalidateAll()

    }

    /**
     * 패턴 기반으로 인증 정보 캐시를 무효화합니다.
     */
    @DeleteMapping("/invalidate/pattern")
    suspend fun invalidatePattern(@RequestParam(name = "pattern") pattern: String): Long {
        if (pattern.isEmpty()) {
            return 0
        }
        log.debug { "Invalidating cache for pattern: $pattern" }
        return repository.invalidateByPattern(pattern)
    }
}
