package exposed.r2dbc.examples.cache.ktor.config

import io.bluetape4k.testcontainers.storage.RedisServer
import org.redisson.api.RedissonClient

/**
 * Creates Redisson clients using the shared bluetape4k Redis Testcontainer.
 */
object KtorRedissonFactory {

    fun create(): RedissonClient =
        RedisServer.Launcher.RedissonLib.getRedisson(
            address = RedisServer.Launcher.redis.url,
            threads = 16,
            nettyThreads = 32,
        )
}
