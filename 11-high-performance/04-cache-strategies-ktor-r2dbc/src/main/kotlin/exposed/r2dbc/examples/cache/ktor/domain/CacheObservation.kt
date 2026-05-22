package exposed.r2dbc.examples.cache.ktor.domain

import kotlinx.atomicfu.atomic

/**
 * Application-local counters for cache route observations.
 */
class CacheObservation {
    private val hits = atomic(0L)
    private val misses = atomic(0L)
    private val notFound = atomic(0L)
    private val written = atomic(0L)
    private val invalidated = atomic(0L)

    fun record(result: CacheReadResult) {
        when (result) {
            is CacheReadResult.Hit      -> hits.incrementAndGet()
            is CacheReadResult.Miss     -> misses.incrementAndGet()
            CacheReadResult.NotFound    -> notFound.incrementAndGet()
        }
    }

    fun recordWritten() {
        written.incrementAndGet()
    }

    fun recordInvalidated(count: Long) {
        invalidated.addAndGet(count)
    }

    fun snapshot(): CacheStatsRecord =
        CacheStatsRecord(
            hits = hits.value,
            misses = misses.value,
            notFound = notFound.value,
            written = written.value,
            invalidated = invalidated.value,
        )
}

/**
 * Single-pass cache read outcome used as the source of truth for route status.
 */
sealed class CacheReadResult {
    data class Hit(val user: UserRecord): CacheReadResult()
    data class Miss(val user: UserRecord): CacheReadResult()
    data object NotFound: CacheReadResult()
}
