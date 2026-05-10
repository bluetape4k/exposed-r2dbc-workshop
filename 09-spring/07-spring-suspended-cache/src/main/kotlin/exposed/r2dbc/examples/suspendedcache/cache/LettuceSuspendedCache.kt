package exposed.r2dbc.examples.suspendedcache.cache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList

/**
 * Lettuce coroutine API를 이용해 Redis를 비동기 방식으로 다루는 간단한 suspended cache 구현체입니다.
 *
 * cache name을 prefix로 사용하여 논리 캐시를 구분하고, 필요 시 TTL 기반 만료를 적용합니다.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class LettuceSuspendedCache<K: Any, V: Any>(
    /** Redis 키 prefix로 사용하는 캐시 이름입니다. */
    val name: String,
    /** 실제 Redis 명령을 수행하는 coroutine 명령 객체입니다. */
    val commands: RedisCoroutinesCommands<String, V>,
    /** 캐시 key index를 관리하는 String codec 명령 객체입니다. */
    private val keyCommands: RedisCoroutinesCommands<String, String>,
    private val ttlSeconds: Long? = null,
) {
    companion object: KLoggingChannel()

    private fun keyStr(key: K): String = "$name:$key"
    private val indexKey: String = "$name:__keys"

    /**
     * [key]에 해당하는 캐시 값을 조회합니다.
     */
    suspend fun get(key: K): V? = commands.get(keyStr(key))

    /**
     * [key]에 [value]를 저장합니다.
     *
     * 인스턴스 생성 시 TTL이 지정되었다면 `SETEX`, 아니면 `SET`을 사용합니다.
     */
    suspend fun put(key: K, value: V) {
        val redisKey = keyStr(key)
        if (ttlSeconds != null) {
            commands.setex(redisKey, ttlSeconds, value)
        } else {
            commands.set(redisKey, value)
        }
        keyCommands.sadd(indexKey, redisKey)
    }

    /**
     * [key]에 해당하는 캐시 엔트리를 제거합니다.
     */
    suspend fun evict(key: K) {
        val redisKey = keyStr(key)
        commands.del(redisKey)
        keyCommands.srem(indexKey, redisKey)
    }

    /**
     * 현재 cache namespace에 속한 모든 키를 삭제합니다.
     *
     * Redis 전체 keyspace를 훑지 않도록 캐시가 저장한 key index만 조회해 삭제합니다.
     */
    suspend fun clear() {
        val keys = keyCommands.smembers(indexKey)
            .filterNotNull()
            .toList()
        keys
            .chunked(100)
            .forEach { chunk ->
                if (chunk.isNotEmpty()) {
                    keyCommands.unlink(*chunk.toTypedArray())
                }
            }
        keyCommands.unlink(indexKey)
    }
}
