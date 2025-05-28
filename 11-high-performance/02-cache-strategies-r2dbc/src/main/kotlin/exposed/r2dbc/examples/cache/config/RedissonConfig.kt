package exposed.r2dbc.examples.cache.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.testcontainers.storage.RedisServer
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
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
        val env = System.getenv()

        val config = Config().apply {
            useSingleServer()
                .setAddress(redis.url)
                .setConnectionPoolSize(100)
                .setConnectionMinimumIdleSize(10)
                .setIdleConnectionTimeout(1000)
                .setTimeout(1000)
                .setRetryAttempts(3)
                .setRetryInterval(300)
        }

        return Redisson.create(config)
    }

}
