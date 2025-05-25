package exposed.r2dbc.examples.suspendedcache.config

import exposed.r2dbc.examples.suspendedcache.AbstractSpringSuspendedCacheApplicationTest
import exposed.r2dbc.examples.suspendedcache.domain.repository.CountryR2dbcRepository
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class R2dbcRepositoryConfigTest(
    @Autowired @Qualifier("defaultCountryR2dbcRepository")
    private val defaultCountryRepository: CountryR2dbcRepository,
    @Autowired @Qualifier("cachedCountryR2dbcRepository")
    private val cachedCountryRepository: CountryR2dbcRepository,
): AbstractSpringSuspendedCacheApplicationTest() {

    companion object: KLoggingChannel()

    @Test
    fun `defaultCountrySuspendedRepository가 생성되어야 한다`() = runSuspendIO {
        defaultCountryRepository.shouldNotBeNull()
        defaultCountryRepository.findByCode("KR").shouldNotBeNull()
    }

    @Test
    fun `cachedCountrySuspendedRepository가 생성되어야 한다`() = runSuspendIO {
        cachedCountryRepository.shouldNotBeNull()
        cachedCountryRepository.findByCode("KR").shouldNotBeNull()
    }
}
