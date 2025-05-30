package exposed.r2dbc.examples.suspendedcache.cache

import io.bluetape4k.coroutines.flow.extensions.chunked
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class LettuceSuspendedCache<K: Any, V: Any>(
    val name: String,
    val commands: RedisCoroutinesCommands<String, V>,
    private val ttlSeconds: Long? = null,
) {
    companion object: KLoggingChannel()

    private fun keyStr(key: K): String = "$name:$key"

    suspend fun get(key: K): V? = commands.get(keyStr(key))

    suspend fun put(key: K, value: V) {
        if (ttlSeconds != null) {
            commands.setex(keyStr(key), ttlSeconds, value)
        } else {
            commands.set(keyStr(key), value)
        }
    }

    suspend fun evict(key: K) {
        commands.del(keyStr(key))
    }

    suspend fun clear() {
        commands.keys("$name:*")
            .chunked(100, true)
            .collect { keys ->
                commands.del(*keys.toTypedArray())
            }
    }
}
