package exposed.r2dbc.examples.cache.ktor.config

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.redisson.api.RedissonClient

/**
 * Groups resources owned by one Ktor cache strategy application instance.
 */
class KtorCacheResources private constructor(
    private val databaseOwner: KtorCacheDatabase,
    val redissonClient: RedissonClient,
    val cacheName: String,
): AutoCloseable {

    val database: R2dbcDatabase = databaseOwner.database

    override fun close() {
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
        ): KtorCacheResources =
            KtorCacheResources(
                databaseOwner = KtorCacheDatabase.create(databaseName = databaseName, maxPoolSize = maxPoolSize),
                redissonClient = KtorRedissonFactory.create(),
                cacheName = cacheName,
            )
    }
}
