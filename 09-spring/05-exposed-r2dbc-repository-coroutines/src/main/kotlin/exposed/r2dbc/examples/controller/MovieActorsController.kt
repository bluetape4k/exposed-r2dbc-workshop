package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.domain.model.MovieActorCountRecord
import exposed.r2dbc.examples.domain.model.MovieWithActorRecord
import exposed.r2dbc.examples.domain.model.MovieWithProducingActorRecord
import exposed.r2dbc.examples.domain.repository.MovieR2dbcRepository
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
 * 영화-배우 관계 질의를 제공하는 API입니다.
 */
@RestController
@RequestMapping("/movie-actors")
class MovieActorsController(
    private val movieRepository: MovieR2dbcRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    /**
     * 특정 영화의 배우 포함 상세 정보를 조회합니다.
     */
    @GetMapping("/{movieId}")
    suspend fun getMovieWithActors(@PathVariable movieId: Long): MovieWithActorRecord? =
        suspendTransaction {
            movieRepository.getMovieWithActors(movieId)
        }

    /**
     * 영화별 배우 수 집계를 조회합니다.
     */
    @GetMapping("/count")
    suspend fun getMovieActorsCount(): List<MovieActorCountRecord> =
        suspendTransaction {
            movieRepository.getMovieActorsCount().toList()
        }

    /**
     * 제작자 겸 배우가 참여한 영화를 조회합니다.
     */
    @GetMapping("/acting-producers")
    suspend fun findMoviesWithActingProducers(): List<MovieWithProducingActorRecord> =
        suspendTransaction {
            movieRepository.findMoviesWithActingProducers().toList()
        }
}
