package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shares one in-flight producer per key so concurrent coroutine callers do not
 * stampede the database on an overlapping cold cache miss.
 */
class SingleFlightCacheLoader<K: Any, V: Any>(
    private val scope: CoroutineScope,
    private val producerTimeout: Duration = 5.seconds,
    private val onCallerCancelled: () -> Unit = {},
    private val onProducerFailed: (Throwable) -> Unit = {},
) {
    private val inFlight = ConcurrentHashMap<K, Deferred<V?>>()

    val inFlightSize: Int
        get() = inFlight.size

    /**
     * Runs or joins a per-key producer and tells the caller whether it created
     * the load or coalesced behind an existing one.
     */
    suspend fun load(key: K, producer: suspend () -> V?): SingleFlightLoad<V> {
        val created = AtomicBoolean(false)
        val deferred = inFlight.computeIfAbsent(key) {
            created.set(true)
            scope.async {
                val produced = withTimeoutOrNull(producerTimeout) {
                    ProducerResult(producer())
                } ?: throw CacheLoadTimeoutException(key.toString())

                produced.value
            }.also { running ->
                running.invokeOnCompletion { cause ->
                    inFlight.remove(key, running)
                    if (cause != null && cause !is CancellationException) {
                        onProducerFailed(cause)
                    }
                }
            }
        }

        return try {
            val value = deferred.await()
            if (created.get()) {
                SingleFlightLoad.Primary(value)
            } else {
                SingleFlightLoad.Shared(value)
            }
        } catch (e: CancellationException) {
            onCallerCancelled()
            throw e
        }
    }

    private data class ProducerResult<V>(val value: V?)
}

/**
 * Result of joining a single-flight producer.
 */
sealed class SingleFlightLoad<out V: Any> {
    data class Primary<V: Any>(val value: V?): SingleFlightLoad<V>()
    data class Shared<V: Any>(val value: V?): SingleFlightLoad<V>()
}

/**
 * Raised when a single-flight cache producer exceeds the configured timeout.
 */
class CacheLoadTimeoutException(key: String): RuntimeException("cache load timed out for key: $key")
