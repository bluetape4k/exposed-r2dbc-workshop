package exposed.r2dbc.examples.cache.config

import io.bluetape4k.concurrent.virtualthread.VirtualThreadExecutor
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.redis.redisson.codec.RedissonCodecs
import io.bluetape4k.testcontainers.storage.RedisServer
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class RedissonConfig {

    companion object: KLoggingChannel() {
        @JvmStatic
        val redis = RedisServer.Launcher.redis
    }

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config().apply {
            useSingleServer()
                .setAddress(redis.url)
                .setConnectionPoolSize(128)
                .setConnectionMinimumIdleSize(32) // 최소 연결을 충분히 확보하여 Latency 방지
                .setIdleConnectionTimeout(10000)  // 연결 유지를 넉넉히 (10초)
                .setTimeout(5000)
                .setRetryAttempts(3)
                .setRetryDelay { attempt -> Duration.ofMillis((attempt + 1) * 100L) }

                .setDnsMonitoringInterval(5000)  // DNS 변경 감지 (Cloud 환경 필수)

            executor = VirtualThreadExecutor
            threads = 256
            nettyThreads = 128
            codec = RedissonCodecs.LZ4ForyComposite
            setTcpNoDelay(true)
            setTcpUserTimeout(5000)
        }

        return Redisson.create(config)
    }

}
