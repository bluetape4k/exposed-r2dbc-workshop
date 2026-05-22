package exposed.r2dbc.multitenant.security

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("h2")
@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [SecurityTenantApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractMultitenantTest {

    companion object: KLoggingChannel()
}
