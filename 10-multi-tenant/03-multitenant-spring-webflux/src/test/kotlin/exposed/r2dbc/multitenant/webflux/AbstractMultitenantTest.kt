package exposed.r2dbc.multitenant.webflux

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// NOTE: R2dbc 에서는 mysql, h2 가 schema 를 지원하지 않는다. (JDBC 에서는 지원함)
@ActiveProfiles("postgres") // postgres 만 성공한다 
@SpringBootTest(
    classes = [ExposedMultitenantWebfluxApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractMultitenantTest {

    companion object: KLoggingChannel() {
        @JvmStatic
        val faker = Fakers.faker
    }

}
