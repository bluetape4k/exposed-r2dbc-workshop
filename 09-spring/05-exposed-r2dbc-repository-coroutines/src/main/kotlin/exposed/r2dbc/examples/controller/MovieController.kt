package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.domain.model.MovieRecord
import exposed.r2dbc.examples.domain.model.MovieWithActorRecord
import exposed.r2dbc.examples.domain.repository.MovieR2dbcRepository
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/movies")
class MovieController(
    private val movieRepository: MovieR2dbcRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping
    suspend fun getMoviews(): List<MovieRecord> =
        suspendTransaction {
            movieRepository.findAll().toFastList()
        }

    @GetMapping("/{id}")
    suspend fun getMovieWithActors(@PathVariable id: Long): MovieWithActorRecord? =
        suspendTransaction {
            movieRepository.getMovieWithActors(id)
        }

    @GetMapping("/search")
    suspend fun searchMovies(request: ServerHttpRequest): List<MovieRecord> {
        val params = request.queryParams.map { it.key to it.value.firstOrNull() }.toMap()
        return when {
            params.isEmpty() -> emptyList()
            else -> suspendTransaction {
                movieRepository.searchMovies(params).toFastList()
            }
        }
    }

    @PostMapping
    suspend fun saveMovie(@RequestBody movie: MovieRecord): MovieRecord =
        suspendTransaction {
            movieRepository.save(movie)
        }

    @DeleteMapping("/{id}")
    suspend fun deleteMovieById(@PathVariable("id") id: Long): Int =
        suspendTransaction {
            movieRepository.deleteById(id)
        }
}
