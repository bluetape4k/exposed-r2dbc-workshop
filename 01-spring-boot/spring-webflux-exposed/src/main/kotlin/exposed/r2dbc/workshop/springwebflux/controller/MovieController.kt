package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.domain.MovieDTO
import exposed.r2dbc.workshop.springwebflux.domain.repository.MovieRepository
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
    private val movieRepository: MovieRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{id}")
    suspend fun getMovieById(@PathVariable("id") movieId: Long): MovieDTO? =
        suspendTransaction {
            movieRepository.findById(movieId)
        }

    @GetMapping
    suspend fun searchMovies(request: ServerHttpRequest): List<MovieDTO> {
        val params = request.queryParams.map { it.key to it.value.first() }.toMap()
        return suspendTransaction {
            if (params.isEmpty()) movieRepository.findAll().toFastList()
            else movieRepository.searchMovie(params).toFastList()
        }
    }

    @PostMapping
    suspend fun createMovie(@RequestBody movie: MovieDTO): MovieDTO =
        suspendTransaction {
            movieRepository.create(movie)
        }

    @DeleteMapping("/{id}")
    suspend fun deleteMovie(@PathVariable("id") movieId: Long): Int =
        suspendTransaction {
            movieRepository.deleteById(movieId)
        }
}
