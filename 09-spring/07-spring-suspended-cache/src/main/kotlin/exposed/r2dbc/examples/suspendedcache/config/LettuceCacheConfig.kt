package exposed.r2dbc.examples.suspendedcache.config

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodecs
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LettuceCacheConfig {

    companion object: KLoggingChannel()

    private val redisServer = RedisServer.Launcher.redis

    @Bean
    fun redisClient(): RedisClient {
        return RedisClient.create(RedisURI.create(redisServer.url))
    }

    @Bean
    fun lettuceSuspendedCacheManager(redisClient: RedisClient): LettuceSuspendedCacheManager {
        return LettuceSuspendedCacheManager(
            redisClient = redisClient,
            ttlSeconds = 60L,
            codec = LettuceBinaryCodecs.lz4Fory(),
        )
    }
}
