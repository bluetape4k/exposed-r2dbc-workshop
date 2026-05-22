package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import kotlinx.atomicfu.atomic

/**
 * Application-local counters for cache route observations.
 */
class CoroutineCacheObservation {
    private val hits = atomic(0L)
    private val misses = atomic(0L)
    private val coalesced = atomic(0L)
    private val notFound = atomic(0L)
    private val written = atomic(0L)
    private val invalidated = atomic(0L)
    private val cancellations = atomic(0L)
    private val loadFailures = atomic(0L)
    private val cacheWriteFailures = atomic(0L)

    fun record(result: CoroutineCacheReadResult) {
        when (result) {
            is CoroutineCacheReadResult.Hit      -> hits.incrementAndGet()
            is CoroutineCacheReadResult.Miss     -> misses.incrementAndGet()
            is CoroutineCacheReadResult.Coalesced -> coalesced.incrementAndGet()
            CoroutineCacheReadResult.NotFound    -> notFound.incrementAndGet()
        }
    }

    fun recordWritten() {
        written.incrementAndGet()
    }

    fun recordInvalidated(count: Long) {
        invalidated.addAndGet(count)
    }

    fun recordCancellation() {
        cancellations.incrementAndGet()
    }

    fun recordLoadFailure() {
        loadFailures.incrementAndGet()
    }

    fun recordCacheWriteFailure() {
        cacheWriteFailures.incrementAndGet()
    }

    fun snapshot(): CoroutineCacheStatsRecord =
        CoroutineCacheStatsRecord(
            hits = hits.value,
            misses = misses.value,
            coalesced = coalesced.value,
            notFound = notFound.value,
            written = written.value,
            invalidated = invalidated.value,
            cancellations = cancellations.value,
            loadFailures = loadFailures.value,
            cacheWriteFailures = cacheWriteFailures.value,
        )
}

/**
 * Single-pass cache read outcome used as the source of truth for route status.
 */
sealed class CoroutineCacheReadResult {
    data class Hit(val user: UserRecord): CoroutineCacheReadResult()
    data class Miss(val user: UserRecord): CoroutineCacheReadResult()
    data class Coalesced(val user: UserRecord): CoroutineCacheReadResult()
    data object NotFound: CoroutineCacheReadResult()
}
