package exposed.r2dbc.examples.cache

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("h2")  // h2 | postgres | mysql
@SpringBootTest(
    classes = [CacheStrategyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractCacheStrategyTest {

    companion object: KLoggingChannel() {
        @JvmStatic
        val faker = Fakers.faker
    }

    @BeforeAll
    fun beforeAll() {
        Thread.sleep(10)
    }

}
