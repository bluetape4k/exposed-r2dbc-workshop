package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpGet
import org.amshove.kluent.shouldNotBeBlank
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

class IndexControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractCacheStrategyTest() {

    companion object: KLoggingChannel()

    @Test
    fun `call index`() = runSuspendIO {
        client
            .httpGet("/")
            .expectBody<String>()
            .returnResult().responseBody
            .apply {
                log.debug { "Build properties: $this" }
            }
            .shouldNotBeNull()
            .shouldNotBeBlank()
    }
}
