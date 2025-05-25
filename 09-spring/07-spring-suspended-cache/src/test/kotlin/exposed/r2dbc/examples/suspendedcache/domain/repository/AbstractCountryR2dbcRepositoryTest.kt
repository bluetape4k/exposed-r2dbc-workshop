package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.AbstractSpringSuspendedCacheApplicationTest
import exposed.r2dbc.examples.suspendedcache.utils.DataPopulator
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

abstract class AbstractCountryR2dbcRepositoryTest: AbstractSpringSuspendedCacheApplicationTest() {

    companion object: KLoggingChannel()

    abstract val countryRepository: CountryR2dbcRepository

    @Test
    fun `모든 국가 정보를 로드합니다`() = runSuspendIO {
        countryRepository.evictCacheAll()

        log.debug { "1. 모든 국가 정보를 로드합니다..." }
        val countries = DataPopulator.COUNTRY_CODES.map { code ->
            countryRepository.findByCode(code)
        }
        countries.all { it != null }.shouldBeTrue()

        log.debug { "2. 모든 국가 정보를 로드합니다..." }
        val countries2 = DataPopulator.COUNTRY_CODES.map { code ->
            countryRepository.findByCode(code)
        }

        countries2.all { it != null }.shouldBeTrue()
    }

    @Test
    fun `국가 정보를 Update 합니다`() = runSuspendIO {
        countryRepository.evictCacheAll()

        log.debug { "1. 모든 국가 정보를 로드합니다..." }
        val countries = DataPopulator.COUNTRY_CODES.map { code ->
            countryRepository.findByCode(code)
        }
        countries.all { it != null }.shouldBeTrue()

        log.debug { "2. 모든 국가 정보를 Update 합니다..." }
        countries.forEach {
            it?.let {
                countryRepository.update(it.copy(name = "${it.name} - updated"))
            }
        }

        log.debug { "3. 모든 국가 정보를 로드합니다..." }
        val countries2 = DataPopulator.COUNTRY_CODES.map { code ->
            countryRepository.findByCode(code)
        }

        countries2.all { it != null }.shouldBeTrue()
    }
}
