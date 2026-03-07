package exposed.r2dbc.examples.suspendedcache.cache

import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class LettuceSuspendedCacheManagerTest {

    @Test
    fun `같은 이름의 캐시는 기존 인스턴스를 재사용한다`() {
        val redisClient = mockk<RedisClient>()
        val codec = mockk<LettuceBinaryCodec<String>>()
        val connection = mockk<StatefulRedisConnection<String, String>>()
        val commands = mockk<RedisReactiveCommands<String, String>>()

        every { redisClient.connect(codec) } returns connection
        every { connection.reactive() } returns commands

        val manager = LettuceSuspendedCacheManager(redisClient = redisClient)

        val first = manager.getOrCreate<String, String>("countries", codec = codec)
        val second = manager.getOrCreate<String, String>("countries", codec = codec)

        assertSame(first, second)
        verify(exactly = 1) { redisClient.connect(codec) }
    }

    @Test
    fun `codec이 없으면 Lettuce 기본 연결을 사용하고 close에서 연결을 정리한다`() {
        val redisClient = mockk<RedisClient>()
        val connection = mockk<StatefulRedisConnection<String, String>>()
        val commands = mockk<RedisReactiveCommands<String, String>>()

        every { redisClient.connect() } returns connection
        every { connection.reactive() } returns commands
        every { connection.close() } just runs

        val manager = LettuceSuspendedCacheManager(redisClient = redisClient)

        manager.getOrCreate<String, String>("plain")
        manager.close()

        verify(exactly = 1) { redisClient.connect() }
        verify(exactly = 1) { connection.close() }
    }
}
