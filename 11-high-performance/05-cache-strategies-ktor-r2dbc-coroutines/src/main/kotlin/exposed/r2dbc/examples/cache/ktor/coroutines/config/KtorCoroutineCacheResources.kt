package exposed.r2dbc.examples.cache.ktor.coroutines.config

import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineCacheObservation
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.SingleFlightCacheLoader
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UserDataInitializer
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UserRecord
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.redisson.api.RedissonClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Groups resources owned by one Ktor coroutine cache application instance.
 */
class KtorCoroutineCacheResources private constructor(
    private val databaseOwner: KtorCoroutineCacheDatabase,
    val redissonClient: RedissonClient,
    val cacheName: String,
    val observation: CoroutineCacheObservation,
    producerTimeout: Duration,
): AutoCloseable {

    private val loaderJob = SupervisorJob()
    private val loaderScope = CoroutineScope(
        loaderJob + Dispatchers.IO + CoroutineName(LOADER_COROUTINE_NAME)
    )
    val database: R2dbcDatabase = databaseOwner.database

    private val initialized = loaderScope.async {
        UserDataInitializer(database).initialize()
    }

    val loader: SingleFlightCacheLoader<Long, UserRecord> =
        SingleFlightCacheLoader(
            scope = loaderScope,
            producerTimeout = producerTimeout,
            onCallerCancelled = observation::recordCancellation,
            onProducerFailed = { observation.recordLoadFailure() },
        )

    suspend fun awaitInitialized() {
        initialized.await()
    }

    override fun close() {
        runBlocking {
            loaderJob.cancelAndJoin()
        }
        try {
            redissonClient.shutdown()
        } finally {
            databaseOwner.close()
        }
    }

    companion object {
        /**
         * Creates database and Redis resources for the example.
         */
        fun create(
            databaseName: String = "ktor_cache_strategies",
            cacheName: String = "exposed:ktor:r2dbc:users",
            maxPoolSize: Int = 8,
            producerTimeout: Duration = 5.seconds,
        ): KtorCoroutineCacheResources =
            KtorCoroutineCacheResources(
                databaseOwner = KtorCoroutineCacheDatabase.create(databaseName = databaseName, maxPoolSize = maxPoolSize),
                redissonClient = KtorCoroutineRedissonFactory.create(),
                cacheName = cacheName,
                observation = CoroutineCacheObservation(),
                producerTimeout = producerTimeout,
            )

        private const val LOADER_COROUTINE_NAME = "ktor-coroutine-cache-loader"
    }
}
