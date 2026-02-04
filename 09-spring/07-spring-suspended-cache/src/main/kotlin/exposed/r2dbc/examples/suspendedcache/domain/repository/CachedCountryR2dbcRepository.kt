package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCache
import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel

class CachedCountryR2dbcRepository(
    private val delegate: CountryR2dbcRepository,
    private val cacheManager: LettuceSuspendedCacheManager,
): CountryR2dbcRepository {

    companion object: KLoggingChannel() {
        const val CACHE_NAME = "caches:country:code"
    }

    private val cache: LettuceSuspendedCache<String, CountryRecord> by lazy {
        cacheManager.getOrCreate(
            name = CACHE_NAME,
            ttlSeconds = 60,
        )
    }

    override suspend fun findByCode(code: String): CountryRecord? {
        return cache.get(code)
            ?: delegate.findByCode(code)?.apply { cache.put(code, this) }
    }

    override suspend fun update(countryRecord: CountryRecord): Int {
        cache.evict(countryRecord.code)
        return delegate.update(countryRecord)
    }

    override suspend fun evictCacheAll() {
        cache.clear()
    }
}
