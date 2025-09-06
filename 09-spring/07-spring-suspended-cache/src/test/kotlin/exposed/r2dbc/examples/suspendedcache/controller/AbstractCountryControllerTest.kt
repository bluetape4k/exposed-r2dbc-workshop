package exposed.r2dbc.examples.suspendedcache.controller

import exposed.r2dbc.examples.suspendedcache.AbstractSpringSuspendedCacheApplicationTest
import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import exposed.r2dbc.examples.suspendedcache.utils.DataPopulator
import io.bluetape4k.coroutines.flow.async
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.spring.tests.httpGet
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

abstract class AbstractCountryControllerTest: AbstractSpringSuspendedCacheApplicationTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 3
    }

    @Autowired
    private val client: WebTestClient = uninitialized()

    abstract val basePath: String

    @RepeatedTest(REPEAT_SIZE)
    fun `find country by code as sequential`() = runTest {
        DataPopulator.COUNTRY_CODES.forEach { code ->
            val country = client
                .httpGet("/$basePath/countries/$code")
                .returnResult<CountryDTO>().responseBody
                .awaitSingle()
            country.code shouldBeEqualTo code
        }
    }

    @RepeatedTest(REPEAT_SIZE)
    fun `find country by code as parallel`() = runTest {
        DataPopulator.COUNTRY_CODES.asFlow()
            .async { code ->
                val country = client
                    .httpGet("/$basePath/countries/$code")
                    .returnResult<CountryDTO>().responseBody
                    .awaitSingle()

                code to country
            }
            .collect { (code, country) ->
                country.code shouldBeEqualTo code
            }
    }
}
