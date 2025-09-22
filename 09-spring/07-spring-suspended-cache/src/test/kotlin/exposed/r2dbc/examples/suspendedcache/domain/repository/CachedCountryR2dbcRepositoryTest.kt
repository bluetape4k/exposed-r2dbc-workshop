package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import exposed.r2dbc.examples.suspendedcache.utils.DataPopulator
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class CachedCountryR2dbcRepositoryTest(
    @param:Autowired private val cacheManager: LettuceSuspendedCacheManager,
): AbstractCountryR2dbcRepositoryTest() {

    @Autowired
    @Qualifier("cachedCountryR2dbcRepository")
    override val countryRepository: CountryR2dbcRepository = uninitialized()

    @Test
    fun `모든 캐시를 삭제한다`() = runTest {
        val countryCache =
            cacheManager.getOrCreate<String, CountryDTO>(CachedCountryR2dbcRepository.CACHE_NAME)


        // 캐시를 채운다.
        DataPopulator.COUNTRY_CODES.map { code -> countryRepository.findByCode(code) }
        DataPopulator.COUNTRY_CODES.all { code -> countryCache.get(code) != null }.shouldBeTrue()

        // 캐시를 전부 비우고, 모두 NULL 임을 확인한다.
        countryRepository.evictCacheAll()
        DataPopulator.COUNTRY_CODES.all { code -> countryCache.get(code) == null }.shouldBeTrue()
    }
}
