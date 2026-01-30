package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.MovieActorCountDTO
import exposed.r2dbc.workshop.springwebflux.domain.MovieWithActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.MovieWithProducingActorDTO
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.reactive.awaitSingle
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult

class MovieActorsControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel()

    @Test
    fun `retrieve movie with actors`() = runSuspendIO {
        val movieId = 1L

        val movieWithActors = client
            .httpGet("/movie-actors/$movieId")
            .expectStatus().is2xxSuccessful
            .returnResult<MovieWithActorDTO>().responseBody
            .awaitSingle()

        log.debug { "movieWithActors[$movieId]=$movieWithActors" }

        movieWithActors.id shouldBeEqualTo movieId
    }

    @Test
    fun `retrieve movie and actor count group by movie name`() = runSuspendIO {
        val movieActorCounts = client
            .httpGet("/movie-actors/count")
            .expectStatus().is2xxSuccessful
            .expectBodyList<MovieActorCountDTO>()
            .returnResult().responseBody
            .shouldNotBeNull()

        movieActorCounts.forEach {
            log.debug { "movieActorCount=$it" }
        }
        movieActorCounts shouldHaveSize 4
    }

    @Test
    fun `retrieves movie with producing actor`() = runSuspendIO {
        val movieWithProducers = client
            .httpGet("/movie-actors/acting-producers")
            .expectStatus().is2xxSuccessful
            .expectBodyList<MovieWithProducingActorDTO>()
            .returnResult().responseBody
            .shouldNotBeNull()

        movieWithProducers.forEach {
            log.debug { "movieWithProducer=$it" }
        }
        movieWithProducers shouldHaveSize 1
    }
}
