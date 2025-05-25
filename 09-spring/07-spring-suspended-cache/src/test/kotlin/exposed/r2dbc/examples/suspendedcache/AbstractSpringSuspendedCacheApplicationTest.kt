package exposed.r2dbc.examples.suspendedcache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [SpringSuspendedCacheApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
abstract class AbstractSpringSuspendedCacheApplicationTest {

    companion object: KLoggingChannel()

}
