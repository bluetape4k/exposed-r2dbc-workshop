package exposed.r2dbc.multitenant.webflux

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// NOTE: mysql 은 계정 문제로 schema 생성이 실패해서 안된다.
@ActiveProfiles("h2") // postgres 만 성공한다
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
