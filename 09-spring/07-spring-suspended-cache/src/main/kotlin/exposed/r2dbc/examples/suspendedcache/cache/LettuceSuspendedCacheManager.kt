package exposed.r2dbc.examples.suspendedcache.cache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import java.util.concurrent.ConcurrentHashMap

/**
 * 이름별 [LettuceSuspendedCache] 인스턴스를 재사용하는 cache manager입니다.
 *
 * 동일한 이름으로 요청하면 기존 캐시를 반환하여 Redis connection과 codec 구성을 공유합니다.
 * manager가 생성한 Redis 연결은 [close] 호출 시 모두 정리됩니다.
 */
class LettuceSuspendedCacheManager(
    /** 캐시 연결 생성에 사용할 공용 Redis 클라이언트입니다. */
    val redisClient: RedisClient,
    /** 개별 캐시에서 지정하지 않았을 때 사용할 기본 TTL입니다. */
    val ttlSeconds: Long? = null,
    /** 개별 캐시에서 지정하지 않았을 때 사용할 기본 codec입니다. */
    val codec: LettuceBinaryCodec<Any>? = null,
): AutoCloseable {

    companion object: KLoggingChannel()

    private data class ManagedCache(
        val cache: LettuceSuspendedCache<out Any, out Any>,
        val connection: StatefulRedisConnection<*, *>,
    )

    private val caches = ConcurrentHashMap<String, ManagedCache>()

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    /**
     * [name]에 해당하는 cache를 반환하거나 새로 생성합니다.
     *
     * `ttlSeconds`와 `codec`를 전달하지 않으면 manager 기본값을 사용합니다.
     * codec이 모두 비어 있으면 Lettuce 기본 String codec으로 연결합니다.
     */
    fun <K: Any, V: Any> getOrCreate(
        name: String,
        ttlSeconds: Long? = null,
        codec: LettuceBinaryCodec<V>? = null,
    ): LettuceSuspendedCache<K, V> {
        val ttl = ttlSeconds ?: this.ttlSeconds
        val redisCodec = codec ?: this.codec

        return caches.computeIfAbsent(name) {
            val conn = openConnection(redisCodec)
            val commands = conn.coroutines() as RedisCoroutinesCommands<String, V>

            ManagedCache(
                cache = LettuceSuspendedCache<K, V>(
                    name = name,
                    commands = commands,
                    ttlSeconds = ttl,
                ),
                connection = conn,
            )
        }.cache as LettuceSuspendedCache<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V: Any> openConnection(codec: LettuceBinaryCodec<V>?): StatefulRedisConnection<String, V> {
        repeat(2) { attempt ->
            try {
                return if (codec != null) {
                    redisClient.connect(codec)
                } else {
                    redisClient.connect() as StatefulRedisConnection<String, V>
                }
            } catch (ex: RedisConnectionException) {
                if (attempt == 1) {
                    throw ex
                }
                Thread.sleep(200L)
            }
        }
        error("unreachable")
    }

    /**
     * manager가 생성한 모든 Redis 연결을 닫고 cache 레지스트리를 비웁니다.
     */
    override fun close() {
        caches.values.forEach { managed ->
            managed.connection.close()
        }
        caches.clear()
    }
}
