package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.MovieActorCountDTO
import exposed.r2dbc.workshop.springwebflux.domain.MovieWithActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.MovieWithProducingActorDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

class MovieActorsControllerTest(
    @Autowired private val client: WebTestClient,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel()

    @Test
    fun `get movie with actors`() = runTest {
        val movieId = 1L

        val movieWithActors = client
            .httpGet("/movie-actors/$movieId")
            .returnResult<MovieWithActorDTO>().responseBody
            .awaitSingle()

        log.debug { "movieWithActors[$movieId]=$movieWithActors" }

        movieWithActors.shouldNotBeNull()
        movieWithActors.id shouldBeEqualTo movieId
    }

    @Test
    fun `get movie and actor count group by movie name`() = runTest {
        val movieActorCounts = client
            .httpGet("/movie-actors/count")
            .returnResult<MovieActorCountDTO>().responseBody
            .asFlow()
            .toList()

        movieActorCounts.forEach {
            log.debug { "movieActorCount=$it" }
        }
        movieActorCounts.shouldNotBeNull() shouldHaveSize 4
    }

    @Test
    fun `get movie and acting producer`() = runTest {
        val movieWithProducers = client
            .httpGet("/movie-actors/acting-producers")
            .returnResult<MovieWithProducingActorDTO>().responseBody
            .asFlow()
            .toList()

        movieWithProducers.forEach {
            log.debug { "movieWithProducer=$it" }
        }
        movieWithProducers shouldHaveSize 1
    }
}
