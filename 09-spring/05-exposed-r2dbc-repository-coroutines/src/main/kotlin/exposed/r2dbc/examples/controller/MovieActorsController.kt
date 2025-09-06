package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.domain.repository.MovieR2dbcRepository
import exposed.r2dbc.examples.dto.MovieActorCountDTO
import exposed.r2dbc.examples.dto.MovieWithActorDTO
import exposed.r2dbc.examples.dto.MovieWithProducingActorDTO
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
    private val movieRepository: MovieR2dbcRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{movieId}")
    suspend fun getMovieWithActors(@PathVariable movieId: Long): MovieWithActorDTO? =
        suspendTransaction {
            movieRepository.getMovieWithActors(movieId)
        }

    @GetMapping("/count")
    suspend fun getMovieActorsCount(): List<MovieActorCountDTO> =
        suspendTransaction {
            movieRepository.getMovieActorsCount().toList()
        }

    @GetMapping("/acting-producers")
    suspend fun findMoviesWithActingProducers(): List<MovieWithProducingActorDTO> =
        suspendTransaction {
            movieRepository.findMoviesWithActingProducers().toList()
        }
}
