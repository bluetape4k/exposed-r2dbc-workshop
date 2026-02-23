package exposed.r2dbc.workshop.springwebflux.controller

import exposed.r2dbc.workshop.springwebflux.domain.model.MovieRecord
import exposed.r2dbc.workshop.springwebflux.domain.repository.MovieRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 영화(Movie) 리소스의 기본 CRUD/검색 API를 제공합니다.
 */
@RestController
@RequestMapping("/movies")
class MovieController(
    private val movieRepository: MovieRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    /**
     * ID로 영화 한 건을 조회합니다.
     */
    @GetMapping("/{id}")
    suspend fun getMovieById(@PathVariable("id") movieId: Long): MovieRecord? =
        suspendTransaction {
            movieRepository.findById(movieId)
        }

    /**
     * 쿼리 파라미터 기반으로 영화를 검색합니다.
     */
    @GetMapping
    suspend fun searchMovies(request: ServerHttpRequest): List<MovieRecord> {
        val params = request.queryParams.map { it.key to it.value.first() }.toMap()
        return suspendTransaction {
            if (params.isEmpty()) movieRepository.findAll().toList()
            else movieRepository.searchMovie(params).toList()
        }
    }

    /**
     * 신규 영화를 생성합니다.
     */
    @PostMapping
    suspend fun createMovie(@RequestBody movie: MovieRecord): MovieRecord =
        suspendTransaction {
            movieRepository.create(movie)
        }

    /**
     * ID로 영화를 삭제하고 영향받은 행 수를 반환합니다.
     */
    @DeleteMapping("/{id}")
    suspend fun deleteMovie(@PathVariable("id") movieId: Long): Int =
        suspendTransaction {
            movieRepository.deleteById(movieId)
        }
}
