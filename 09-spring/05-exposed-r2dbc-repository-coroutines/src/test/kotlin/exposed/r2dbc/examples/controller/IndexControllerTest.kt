package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

class IndexControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel()

    @Test
    fun `get index`() = runSuspendIO {
        client.httpGet("/")
            .returnResult<String>().responseBody
            .asFlow()
            .toList().shouldNotBeEmpty()
            .let {
                log.debug { "Response body: ${it.joinToString("")}" }
            }
    }
}
