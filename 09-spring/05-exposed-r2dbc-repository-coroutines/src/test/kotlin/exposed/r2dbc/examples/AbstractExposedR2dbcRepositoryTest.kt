package exposed.r2dbc.examples

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("h2") // h2 | mysql | postgres 중에 하나를 선택하세요
@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [ExposedR2dbcRepositoryApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
abstract class AbstractExposedR2dbcRepositoryTest {

    companion object: KLoggingChannel() {
        @JvmStatic
        val faker = Fakers.faker
    }
}
