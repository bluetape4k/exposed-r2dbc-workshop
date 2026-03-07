package exposed.r2dbc.examples.suspendedcache.cache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.KeyScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

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
    private val ttlSeconds: Long? = null,
) {
    companion object: KLoggingChannel()

    private fun keyStr(key: K): String = "$name:$key"

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
        if (ttlSeconds != null) {
            commands.setex(keyStr(key), ttlSeconds, value)
        } else {
            commands.set(keyStr(key), value)
        }
    }

    /**
     * [key]에 해당하는 캐시 엔트리를 제거합니다.
     */
    suspend fun evict(key: K) {
        commands.del(keyStr(key))
    }

    /**
     * 현재 cache namespace에 속한 모든 키를 삭제합니다.
     *
     * `KEYS` 대신 `SCAN`을 사용해 대량 키 환경에서도 Redis 메인 스레드를 장시간 점유하지 않도록 합니다.
     */
    suspend fun clear() {
        val scanArgs = KeyScanArgs.Builder.matches("$name:*").limit(100)
        var cursor: ScanCursor = ScanCursor.INITIAL

        do {
            val result = if (cursor == ScanCursor.INITIAL) {
                commands.scan(scanArgs)
            } else {
                commands.scan(cursor, scanArgs)
            } ?: break

            result.keys
                .chunked(100)
                .forEach { keys ->
                    if (keys.isNotEmpty()) {
                        commands.unlink(*keys.toTypedArray())
                    }
                }

            cursor = result
        } while (!cursor.isFinished)
    }
}
