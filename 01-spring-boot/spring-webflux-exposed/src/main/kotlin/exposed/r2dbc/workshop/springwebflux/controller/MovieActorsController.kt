package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.domain.model.MovieActorCountRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieWithActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieWithProducingActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.repository.MovieRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/movie-actors")
class MovieActorsController(
    private val movieRepository: MovieRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{movieId}")
    suspend fun getMovieWithActors(@PathVariable movieId: Long): MovieWithActorRecord? =
        suspendTransaction {
            movieRepository.getMovieWithActors(movieId)
        }

    @GetMapping("/count")
    suspend fun getMovieActorsCount(): List<MovieActorCountRecord> =
        suspendTransaction {
            movieRepository.getMovieActorsCount().toList()
        }

    @GetMapping("/acting-producers")
    suspend fun findMoviesWithActingProducers(): List<MovieWithProducingActorRecord> =
        suspendTransaction {
            movieRepository.findMoviesWithActingProducers().toList()
        }
}
