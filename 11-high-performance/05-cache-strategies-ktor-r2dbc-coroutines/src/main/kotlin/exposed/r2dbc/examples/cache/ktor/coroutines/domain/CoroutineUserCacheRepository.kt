package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class CoroutineUserCacheRepository internal constructor(
    private val database: R2dbcDatabase,
    private val cache: CoroutineUserCacheStore,
    private val observation: CoroutineCacheObservation,
    private val loader: SingleFlightCacheLoader<Long, UserRecord>,
    private val awaitReady: suspend () -> Unit,
) {
    companion object: KLoggingChannel() {
        private const val MAX_LOAD_DELAY_MILLIS = 1_000L
    }

    constructor(
        database: R2dbcDatabase,
        redissonClient: RedissonClient,
        cacheName: String,
        observation: CoroutineCacheObservation,
        loader: SingleFlightCacheLoader<Long, UserRecord>,
        awaitReady: suspend () -> Unit,
    ): this(
        database = database,
        cache = RedissonCoroutineUserCacheStore(redissonClient.getMap(cacheName)),
        observation = observation,
        loader = loader,
        awaitReady = awaitReady,
    )

    suspend fun findCached(id: Long, loadDelayMillis: Long = 0L): CoroutineCacheReadResult {
        awaitReady()
        id.requirePositiveNumber("id")
        if (loadDelayMillis !in 0L..MAX_LOAD_DELAY_MILLIS) {
            throw InvalidLoadDelayException(loadDelayMillis)
        }

        readCache(id)?.let { cached ->
            log.debug { "Cache hit. id=$id" }
            return CoroutineCacheReadResult.Hit(cached)
        }

        val loaded = loader.load(id) {
            if (loadDelayMillis > 0L) {
                delay(loadDelayMillis)
            }
            findByIdFromDb(id)?.also { user ->
                writeCacheSafely(id, user)
            }
        }

        return when (loaded) {
            is SingleFlightLoad.Primary -> loaded.value
                ?.let {
                    log.debug { "Cache miss and DB fallback. id=$id" }
                    CoroutineCacheReadResult.Miss(it)
                }
                ?: CoroutineCacheReadResult.NotFound

            is SingleFlightLoad.Shared -> loaded.value
                ?.let {
                    log.debug { "Coalesced behind in-flight DB fallback. id=$id" }
                    CoroutineCacheReadResult.Coalesced(it)
                }
                ?: CoroutineCacheReadResult.NotFound
        }
    }

    suspend fun findAllFromDb(): List<UserRecord> {
        awaitReady()
        return suspendTransaction(db = database, readOnly = true) {
            UserTable
                .selectAll()
                .orderBy(UserTable.id)
                .map { it.toUserRecord() }
                .toList()
        }
    }

    suspend fun create(request: UpsertUserRequest): UserRecord {
        awaitReady()
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
        writeCacheSafely(saved.id, saved)
        observation.recordWritten()
        return saved
    }

    suspend fun update(id: Long, request: UpsertUserRequest): UserRecord {
        awaitReady()
        id.requirePositiveNumber("id")
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
        writeCacheSafely(id, user)
        observation.recordWritten()
        return user
    }

    suspend fun invalidate(id: Long): Long {
        awaitReady()
        id.requirePositiveNumber("id")
        val removed = removeCache(id)
        observation.recordInvalidated(removed)
        return removed
    }

    suspend fun clearCache(): Long {
        awaitReady()
        val count = clearCacheMap()
        observation.recordInvalidated(count)
        return count
    }

    private suspend fun readCache(id: Long): UserRecord? =
        cache.get(id)

    private suspend fun writeCache(id: Long, user: UserRecord) {
        cache.put(id, user)
    }

    private suspend fun writeCacheSafely(id: Long, user: UserRecord) {
        try {
            writeCache(id, user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            observation.recordCacheWriteFailure()
            log.warn(e) { "Cache write failed after DB value was available. id=$id" }
        }
    }

    private suspend fun removeCache(id: Long): Long =
        cache.remove(id)

    private suspend fun clearCacheMap(): Long =
        cache.clear()

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

    private class RedissonCoroutineUserCacheStore(
        private val map: RMap<Long, UserRecord>,
    ): CoroutineUserCacheStore {
        override suspend fun get(id: Long): UserRecord? =
            withContext(Dispatchers.IO) {
                map[id]
            }

        override suspend fun put(id: Long, user: UserRecord) {
            withContext(Dispatchers.IO) {
                map[id] = user
            }
        }

        override suspend fun remove(id: Long): Long =
            withContext(Dispatchers.IO) {
                map.fastRemove(id)
            }

        override suspend fun clear(): Long =
            withContext(Dispatchers.IO) {
                val count = map.size.toLong()
                map.clear()
                count
            }
    }

    internal interface CoroutineUserCacheStore {
        suspend fun get(id: Long): UserRecord?
        suspend fun put(id: Long, user: UserRecord)
        suspend fun remove(id: Long): Long
        suspend fun clear(): Long
    }

}
