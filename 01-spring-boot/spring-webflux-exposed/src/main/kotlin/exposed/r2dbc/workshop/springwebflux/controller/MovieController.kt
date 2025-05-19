package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.domain.MovieDTO
import exposed.r2dbc.workshop.springwebflux.domain.repository.MovieRepository
import exposed.r2dbc.workshop.springwebflux.domain.toMovieDTO
import io.bluetape4k.logging.KLogging
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
) {

    companion object: KLogging()

    @GetMapping("/{id}")
    suspend fun getMovieById(@PathVariable("id") movieId: Long): MovieDTO? =
        suspendTransaction(readOnly = true) {
            movieRepository.findById(movieId)?.toMovieDTO()
        }

    @GetMapping
    suspend fun searchMovies(request: ServerHttpRequest): List<MovieDTO> {
        val params = request.queryParams.map { it.key to it.value.first() }.toMap()
        return when {
            params.isEmpty() -> suspendTransaction(readOnly = true) {
                movieRepository.findAll().map { it.toMovieDTO() }
            }
            else -> suspendTransaction(readOnly = true) {
                movieRepository.searchMovie(params).map { it.toMovieDTO() }
            }
        }
    }

    @PostMapping
    suspend fun createMovie(@RequestBody movie: MovieDTO): MovieDTO =
        suspendTransaction {
            movieRepository.create(movie).toMovieDTO()
        }

    @DeleteMapping("/{id}")
    suspend fun deleteMovie(@PathVariable("id") movieId: Long): Int =
        suspendTransaction {
            movieRepository.deleteById(movieId)
        }
}
