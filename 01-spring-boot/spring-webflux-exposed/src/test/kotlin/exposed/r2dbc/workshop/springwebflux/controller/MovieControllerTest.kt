package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.MovieDTO
import io.bluetape4k.junit5.coroutines.runSuspendTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpDelete
import io.bluetape4k.spring.tests.httpGet
import io.bluetape4k.spring.tests.httpPost
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult


class MovieControllerTest(
    @Autowired private val client: WebTestClient,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel() {
        private fun newMovieDTO(): MovieDTO = MovieDTO(
            name = faker.book().title(),
            producerName = faker.name().fullName(),
            releaseDate = faker.timeAndDate().birthday(20, 80).atTime(0, 0).toString()
        )
    }

    @Test
    fun `get movie by id`() = runSuspendTest {
        val id = 1L

        val movie = client
            .httpGet("/movies/$id")
            .returnResult<MovieDTO>().responseBody
            .awaitSingle()

        log.debug { "movie=$movie" }

        movie.shouldNotBeNull()
        movie.id shouldBeEqualTo id
    }

    @Test
    fun `search movies by producer name`() = runSuspendTest {
        val producerName = "Johnny"

        val movies = client.httpGet("/movies?producerName=$producerName")
            .returnResult<MovieDTO>().responseBody
            .asFlow()
            .toList()

        movies.size shouldBeEqualTo 2
    }

    @Test
    fun `create new movie`() = runSuspendTest {
        val newMovie = newMovieDTO()

        val saved = client
            .httpPost("/movies", newMovie)
            .returnResult<MovieDTO>().responseBody
            .awaitSingle()

        log.debug { "saved=$saved" }
        saved.shouldNotBeNull() shouldBeEqualTo newMovie.copy(id = saved.id)
    }

    @Test
    fun `delete movie`() = runSuspendTest {
        val newMovie = newMovieDTO()

        val saved = client
            .httpPost("/movies", newMovie)
            .returnResult<MovieDTO>().responseBody
            .awaitSingle()

        val deletedCount = client
            .httpDelete("/movies/${saved.id}")
            .returnResult<Int>().responseBody
            .awaitSingle()

        deletedCount shouldBeEqualTo 1
    }
}
