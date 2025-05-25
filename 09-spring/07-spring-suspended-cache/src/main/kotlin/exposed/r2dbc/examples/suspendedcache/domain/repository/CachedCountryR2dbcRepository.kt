package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCache
import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel

class CachedCountryR2dbcRepository(
    private val delegate: CountryR2dbcRepository,
    private val cacheManager: LettuceSuspendedCacheManager,
): CountryR2dbcRepository {

    companion object: KLoggingChannel() {
        const val CACHE_NAME = "caches:country:code"
    }

    private val cache: LettuceSuspendedCache<String, CountryDTO> by lazy {
        cacheManager.getOrCreate(
            name = CACHE_NAME,
            ttlSeconds = 60,
        )
    }

    override suspend fun findByCode(code: String): CountryDTO? {
        return cache.get(code)
            ?: delegate.findByCode(code)?.apply { cache.put(code, this) }
    }

    override suspend fun update(countryDTO: CountryDTO): Int {
        cache.evict(countryDTO.code)
        return delegate.update(countryDTO)
    }

    override suspend fun evictCacheAll() {
        cache.clear()
    }
}
