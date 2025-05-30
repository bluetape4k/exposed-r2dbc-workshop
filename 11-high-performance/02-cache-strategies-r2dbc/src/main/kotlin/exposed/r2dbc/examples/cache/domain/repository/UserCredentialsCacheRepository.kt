package exposed.r2dbc.examples.cache.domain.repository


import exposed.r2dbc.examples.cache.domain.model.UserCredentialsDTO
import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import exposed.r2dbc.examples.cache.domain.model.toUserCredentialsDTO
import io.bluetape4k.exposed.r2dbc.redisson.repository.AbstractR2dbcCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.redis.redisson.cache.RedisCacheConfig
import org.jetbrains.exposed.v1.core.ResultRow
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository

/**
 * Read-Only 캐시를 이용하여, DB의 사용자 인증 정보를 캐시합니다.
 */
@Repository
class UserCredentialsCacheRepository(
    redissonClient: RedissonClient,
): AbstractR2dbcCacheRepository<UserCredentialsDTO, String>(
    redissonClient = redissonClient,
    cacheName = "exposed:coroutines:user-credentials",
    config = RedisCacheConfig.READ_ONLY_WITH_NEAR_CACHE,
) {

    companion object: KLoggingChannel()

    override val entityTable = UserCredentialsTable
    override suspend fun ResultRow.toEntity() = toUserCredentialsDTO()

    // READ-ONLY 이므로, doUpdateEntity, doInsertEntity 를 구현하지 않습니다.
}
