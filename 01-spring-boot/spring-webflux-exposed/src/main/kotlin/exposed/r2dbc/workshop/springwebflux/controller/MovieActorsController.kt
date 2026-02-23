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

/**
 * 영화-배우 관계 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/movie-actors")
class MovieActorsController(
    private val movieRepository: MovieRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    /**
     * 특정 영화의 배우 목록을 포함한 상세 정보를 조회합니다.
     */
    @GetMapping("/{movieId}")
    suspend fun getMovieWithActors(@PathVariable movieId: Long): MovieWithActorRecord? =
        suspendTransaction {
            movieRepository.getMovieWithActors(movieId)
        }

    /**
     * 영화별 배우 수 집계 결과를 조회합니다.
     */
    @GetMapping("/count")
    suspend fun getMovieActorsCount(): List<MovieActorCountRecord> =
        suspendTransaction {
            movieRepository.getMovieActorsCount().toList()
        }

    /**
     * 제작자이면서 배우로 참여한 영화 목록을 조회합니다.
     */
    @GetMapping("/acting-producers")
    suspend fun findMoviesWithActingProducers(): List<MovieWithProducingActorRecord> =
        suspendTransaction {
            movieRepository.findMoviesWithActingProducers().toList()
        }
}
