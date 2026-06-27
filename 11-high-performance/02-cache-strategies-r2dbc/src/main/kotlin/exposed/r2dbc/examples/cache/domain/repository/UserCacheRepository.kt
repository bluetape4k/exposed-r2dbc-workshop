package exposed.r2dbc.examples.cache.domain.repository

import exposed.r2dbc.examples.cache.domain.model.UserRecord
import exposed.r2dbc.examples.cache.domain.model.UserTable
import exposed.r2dbc.examples.cache.domain.model.toUserRecord
import io.bluetape4k.exposed.r2dbc.redisson.repository.AbstractR2dbcRedissonRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.redis.redisson.cache.RedissonCacheConfig
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Read Through / Write Through + Near Cache 전략으로 사용자(User) 정보를 캐시하는 Repository 입니다.
 *
 * [AbstractR2dbcRedissonRepository]를 상속하여 Redisson MapCache 기반의 캐시 계층을 제공합니다.
 * Redis에 캐시된 데이터가 없으면(Cache Miss) DB에서 조회 후 Redis에 적재(Read Through)하고,
 * 데이터를 저장하면 즉시 DB와 Redis 양쪽에 반영(Write Through)합니다.
 *
 * ## 캐시 전략 구성
 * - **Read Through**: `get(id)` 호출 시 캐시 Miss → DB 자동 조회 후 Redis 적재
 * - **Write Through**: `put(entity)` 호출 시 Redis 저장 + DB 즉시 반영
 * - **Near Cache**: 애플리케이션 내 Caffeine 로컬 캐시 → Redis 라운드트립 절감
 * - `deleteFromDBOnInvalidate = false`: 캐시 무효화는 Redis/Caffeine 계층에만 적용하고 DB 데이터는 유지합니다.
 *
 * ## 캐시 키
 * `exposed:coroutines:users:<id>` 형태로 Redis에 저장됩니다.
 *
 * @see AbstractR2dbcRedissonRepository
 * @see UserRecord
 */
@Repository
class UserCacheRepository(redissonClient: RedissonClient): AbstractR2dbcRedissonRepository<Long, UserRecord>(
    redissonClient = redissonClient,
    config = RedissonCacheConfig.READ_WRITE_THROUGH_WITH_NEAR_CACHE.copy(
        name = "exposed:coroutines:users",
        deleteFromDBOnInvalidate = false,
    ),
    trustedBinaryCache = true,
) {
    companion object: KLoggingChannel()

    override val table = UserTable
    override fun extractId(entity: UserRecord): Long = entity.id
    override suspend fun ResultRow.toEntity() = toUserRecord()

    override fun BatchInsertStatement.insertEntity(entity: UserRecord) {
        log.debug { "Insert entity: $entity" }
        if (entity.id != 0L) {
            this[UserTable.id] = entity.id
        }
        this[UserTable.username] = entity.username
        this[UserTable.firstName] = entity.firstName
        this[UserTable.lastName] = entity.lastName
        this[UserTable.address] = entity.address
        this[UserTable.zipcode] = entity.zipcode
        this[UserTable.birthDate] = entity.birthDate
        this[UserTable.avatar] = entity.avatar?.let { ExposedBlob(it) }
        this[UserTable.createdAt] = Instant.now()
    }

    override fun UpdateStatement.updateEntity(entity: UserRecord) {
        log.debug { "Update entity: $entity" }
        this[UserTable.username] = entity.username
        this[UserTable.firstName] = entity.firstName
        this[UserTable.lastName] = entity.lastName
        this[UserTable.address] = entity.address
        this[UserTable.zipcode] = entity.zipcode
        this[UserTable.birthDate] = entity.birthDate
        this[UserTable.avatar] = entity.avatar?.let { ExposedBlob(it) }
        this[UserTable.updatedAt] = Instant.now()
    }
}
