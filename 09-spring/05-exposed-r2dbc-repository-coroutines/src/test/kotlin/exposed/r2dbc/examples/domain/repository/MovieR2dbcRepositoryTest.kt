package exposed.r2dbc.examples.domain.repository

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.dto.MovieDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MovieR2dbcRepositoryTest(
    @param:Autowired private val movieRepository: MovieR2dbcRepository,
    @param:Autowired private val actorRepository: ActorR2dbcRepository,
): AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel() {
        private fun newMovieDTO() = MovieDTO(
            id = 0L,
            name = faker.book().title(),
            producerName = faker.name().fullName(),
            releaseDate = faker.timeAndDate().birthday(20, 80).toString()
        )
    }

    @Test
    fun `find movie by id`() = runTest {
        val movieId = 1L

        val movie = suspendTransaction {
            movieRepository.findById(movieId)
        }

        log.debug { "movie: $movie" }
        movie.id shouldBeEqualTo movieId
    }

    /**
     * ```sql
     * SELECT MOVIES.ID, MOVIES."name", MOVIES.PRODUCER_NAME, MOVIES.RELEASE_DATE
     *   FROM MOVIES
     *  WHERE MOVIES.PRODUCER_NAME = 'Johnny'
     * ```
     */
    @Test
    fun `search movies`() = runTest {
        val params = mapOf("producerName" to "Johnny")

        val movies = suspendTransaction {
            movieRepository.searchMovies(params).toList()
        }

        movies.forEach {
            log.debug { "movie: $it" }
        }
        movies.shouldNotBeEmpty() shouldHaveSize 2
    }

    @Test
    fun `save new movie`() = runTest {
        val movie = newMovieDTO()

        val savedMovie = suspendTransaction {
            movieRepository.save(movie)
        }

        log.debug { "saved movie: $savedMovie" }
        savedMovie shouldBeEqualTo movie.copy(id = savedMovie.id)
    }

    @Test
    fun `delete movie`() = runTest {
        val movie = newMovieDTO()
        val movieDto = suspendTransaction {
            movieRepository.save(movie)
        }

        val movieId = movieDto.id
        suspendTransaction {
            movieRepository.deleteById(movieId)
        }

        val deletedMovie = suspendTransaction {
            movieRepository.findByIdOrNull(movieId)
        }

        log.debug { "deleted movie: $deletedMovie" }
        deletedMovie.shouldBeNull()
    }

    @Test
    fun `get all movies and actors`() = runTest {
        suspendTransaction {
            val movies = movieRepository.getAllMoviesWithActors().toList()
            movies.shouldNotBeEmpty()

            movies.forEach { movie ->
                log.debug { "movie: ${movie.name}" }
                movie.actors.shouldNotBeEmpty()
                movie.actors.forEach { actor ->
                    log.debug { "  actor: ${actor.firstName} ${actor.lastName}" }
                }
            }
        }
    }

    @Test
    fun `get movie by id with actors`() = runTest {
        suspendTransaction {
            val movieId = 1L
            val movieWithActors = movieRepository.getMovieWithActors(movieId)

            log.debug { "movieWithActors: $movieWithActors" }

            movieWithActors.shouldNotBeNull()
            movieWithActors.id shouldBeEqualTo movieId
            movieWithActors.actors shouldHaveSize 3
        }
    }

    @Test
    fun `get movie and actors count`() = runTest {
        suspendTransaction {
            val movieActorsCount = movieRepository.getMovieActorsCount().toList()
            movieActorsCount.shouldNotBeEmpty()
            movieActorsCount.forEach {
                log.debug { "movie: ${it.movieName}, actors count: ${it.actorCount}" }
            }
        }
    }

    @Test
    fun `find movies with acting producers`() = runTest {
        suspendTransaction {
            val movies = movieRepository.findMoviesWithActingProducers().toList()

            movies.forEach {
                log.debug { "movie: ${it.movieName}, producer: ${it.producerActorName}" }
            }
            movies.shouldNotBeEmpty() shouldHaveSize 1
        }
    }
}
