package exposed.r2dbc.examples.cache.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.testcontainers.storage.RedisServer
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig {

    companion object: KLoggingChannel() {
        @JvmStatic
        val redis = RedisServer.Launcher.redis
    }

    @Bean
    fun redissonClient(): RedissonClient {
        return RedisServer.Launcher.RedissonLib.getRedisson(redis.url)
    }
}
