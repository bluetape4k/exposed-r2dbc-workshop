package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import io.bluetape4k.junit5.coroutines.runSuspendTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

class IndexControllerTest(
    @Autowired private val client: WebTestClient,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel()

    @Test
    fun `get index`() = runSuspendTest {
        client.httpGet("/")
            .expectStatus().isOk
            .returnResult<String>().responseBody
            .asFlow()
            .toList()
            .joinToString(separator = "")
            .shouldNotBeEmpty()
    }
}
