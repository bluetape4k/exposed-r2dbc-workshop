package exposed.r2dbc.examples.cache.ktor.domain

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.redisson.api.RMap
import org.redisson.api.RedissonClient

/**
 * Redisson-backed cache-aside repository for Ktor route examples.
 */
class UserCacheRepository(
    private val database: R2dbcDatabase,
    redissonClient: RedissonClient,
    cacheName: String,
    private val observation: CacheObservation,
) {
    private val cache: RMap<Long, UserRecord> = redissonClient.getMap(cacheName)

    companion object: KLoggingChannel()

    suspend fun findCached(id: Long): CacheReadResult {
        require(id > 0) { "id must be positive" }
        readCache(id)?.let { cached ->
            log.debug { "Cache hit. id=$id" }
            return CacheReadResult.Hit(cached)
        }

        val user = findByIdFromDb(id)
            ?: return CacheReadResult.NotFound
        writeCache(id, user)
        log.debug { "Cache miss and DB fallback. id=$id" }
        return CacheReadResult.Miss(user)
    }

    suspend fun findAllFromDb(): List<UserRecord> =
        suspendTransaction(db = database, readOnly = true) {
            UserTable
                .selectAll()
                .orderBy(UserTable.id)
                .map { it.toUserRecord() }
                .toList()
        }

    suspend fun create(request: UpsertUserRequest): UserRecord {
        val user = request.normalized()
        val birthDate = user.birthDate.toLocalDateOrNull()
        val saved = suspendTransaction(db = database) {
            val id = UserTable.insertAndGetId {
                it[username] = user.username
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[address] = user.address
                it[zipcode] = user.zipcode
                it[UserTable.birthDate] = birthDate
            }
            user.withId(id.value)
        }
        writeCache(saved.id, saved)
        observation.recordWritten()
        return saved
    }

    suspend fun update(id: Long, request: UpsertUserRequest): UserRecord {
        require(id > 0) { "id must be positive" }
        val user = request.normalized().withId(id)
        val birthDate = user.birthDate.toLocalDateOrNull()
        val updated = suspendTransaction(db = database) {
            UserTable.update({ UserTable.id eq id }) {
                it[username] = user.username
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[address] = user.address
                it[zipcode] = user.zipcode
                it[UserTable.birthDate] = birthDate
            }
        }
        if (updated == 0) {
            throw UserNotFoundException(id)
        }
        writeCache(id, user)
        observation.recordWritten()
        return user
    }

    suspend fun invalidate(id: Long): Long {
        require(id > 0) { "id must be positive" }
        val removed = removeCache(id)
        observation.recordInvalidated(removed)
        return removed
    }

    suspend fun clearCache(): Long {
        val count = clearCacheMap()
        observation.recordInvalidated(count)
        return count
    }

    private suspend fun readCache(id: Long): UserRecord? =
        withContext(Dispatchers.IO) {
            cache[id]
        }

    private suspend fun writeCache(id: Long, user: UserRecord) {
        withContext(Dispatchers.IO) {
            cache[id] = user
        }
    }

    private suspend fun removeCache(id: Long): Long =
        withContext(Dispatchers.IO) {
            cache.fastRemove(id)
        }

    private suspend fun clearCacheMap(): Long =
        withContext(Dispatchers.IO) {
            val count = cache.size.toLong()
            cache.clear()
            count
        }

    private suspend fun findByIdFromDb(id: Long): UserRecord? =
        suspendTransaction(db = database, readOnly = true) {
            UserTable
                .selectAll()
                .where { UserTable.id eq id }
                .map { it.toUserRecord() }
                .singleOrNull()
        }

    private fun UpsertUserRequest.normalized(): UserRecord =
        toUserRecord().copy(
            username = username.requireNotBlank("username"),
            firstName = firstName.requireNotBlank("firstName"),
            lastName = lastName.requireNotBlank("lastName"),
        )
}
