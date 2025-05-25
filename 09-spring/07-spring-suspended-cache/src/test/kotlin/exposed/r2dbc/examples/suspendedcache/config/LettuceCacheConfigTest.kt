package exposed.r2dbc.examples.suspendedcache.config

import exposed.r2dbc.examples.suspendedcache.AbstractSpringSuspendedCacheApplicationTest
import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.delay
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LettuceCacheConfigTest(
    @Autowired private val lettuceSuspendedCacheManager: LettuceSuspendedCacheManager,
): AbstractSpringSuspendedCacheApplicationTest() {

    companion object: KLoggingChannel()

    @Test
    fun `context loading`() {
        lettuceSuspendedCacheManager.shouldNotBeNull()
    }

    @Test
    fun `get cache`() = runSuspendIO {
        val cache = lettuceSuspendedCacheManager.getOrCreate<String, CountryDTO>("countries")
        cache.shouldNotBeNull()

        val countryKr = CountryDTO("KR", "South Korea", "동해물과 백두산이 마르고 닳도록")
        val countryUs = CountryDTO("US", "United States of America", "미국 국가는 몰라요")

        cache.put(countryKr.code, countryKr)
        cache.put(countryUs.code, countryUs)

        delay(10)

        cache.get(countryKr.code) shouldBeEqualTo countryKr
        cache.get(countryUs.code) shouldBeEqualTo countryUs
    }
}
