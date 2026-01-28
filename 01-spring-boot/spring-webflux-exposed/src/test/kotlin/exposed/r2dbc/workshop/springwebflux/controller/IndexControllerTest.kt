package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

class IndexControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel()

    @Test
    fun `get index`() = runTest {
        client.httpGet("/")
            .expectStatus().is2xxSuccessful
            .expectBody<String>()
            .returnResult().responseBody
            .shouldNotBeNull()
            .shouldNotBeEmpty()
    }
}
