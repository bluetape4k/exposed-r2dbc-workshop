package exposed.r2dbc.examples.cache.ktor.coroutines.config

import io.bluetape4k.testcontainers.storage.RedisServer
import org.redisson.api.RedissonClient

/**
 * Creates Redisson clients using the shared bluetape4k Redis Testcontainer.
 */
object KtorCoroutineRedissonFactory {

    fun create(): RedissonClient =
        RedisServer.Launcher.RedissonLib.getRedisson(
            address = RedisServer.Launcher.redis.url,
            threads = 16,
            nettyThreads = 32,
        )
}
