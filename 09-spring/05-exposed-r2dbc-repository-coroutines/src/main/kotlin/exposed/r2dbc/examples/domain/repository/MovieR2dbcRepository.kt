package exposed.r2dbc.examples.domain.repository

import exposed.r2dbc.examples.domain.model.MovieActorCountRecord
import exposed.r2dbc.examples.domain.model.MovieRecord
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.examples.domain.model.MovieWithActorRecord
import exposed.r2dbc.examples.domain.model.MovieWithProducingActorRecord
import exposed.r2dbc.examples.domain.model.toActorRecord
import exposed.r2dbc.examples.domain.model.toMovieRecord
import exposed.r2dbc.examples.domain.model.toMovieWithActorRecord
import exposed.r2dbc.examples.domain.model.toMovieWithProducingActorRecord
import io.bluetape4k.coroutines.flow.extensions.bufferUntilChanged
import io.bluetape4k.exposed.r2dbc.repository.R2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 영화(Movie) 도메인에 대한 R2DBC Repository 구현체입니다.
 *
 * [R2dbcRepository] 인터페이스를 구현하여 기본 CRUD 기능을 제공하고,
 * 영화-배우 JOIN 쿼리, 검색, 집계 등 도메인별 커스텀 쿼리를 추가합니다.
 *
 * ## 주요 기능
 * - 영화 저장/수정 ([save])
 * - 파라미터 기반 영화 검색 ([searchMovies])
 * - 영화-배우 JOIN 조회 ([getAllMoviesWithActors], [getMovieWithActors])
 * - 영화별 배우 수 집계 ([getMovieActorsCount])
 * - 제작자 겸 배우 영화 조회 ([findMoviesWithActingProducers])
 *
 * ## Flow 그룹핑 패턴
 * 다대다 JOIN 결과에서 동일 영화에 속한 배우들을 모으기 위해
 * `bufferUntilChanged { it.first.id }` 를 사용합니다.
 * 이는 결과가 `movie_id` 순으로 정렬되어 있을 때 올바르게 작동합니다.
 *
 * @see R2dbcRepository
 */
@Repository
class MovieR2dbcRepository: R2dbcRepository<Long, MovieTable, MovieRecord> {

    companion object: KLoggingChannel() {
        private val MovieActorJoin: Join by lazy {
            MovieTable
                .innerJoin(ActorInMovieTable)
                .innerJoin(ActorTable)
        }

        private val moviesWithActingProducersJoin: Join by lazy {
            MovieTable
                .innerJoin(ActorInMovieTable)
                .innerJoin(
                    ActorTable,
                    onColumn = { ActorTable.id },
                    otherColumn = { ActorInMovieTable.actorId }
                ) {
                    MovieTable.producerName eq ActorTable.firstName
                }
        }
    }

    override val table = MovieTable

    override suspend fun ResultRow.toEntity(): MovieRecord = toMovieRecord()

    /**
     * 새 영화를 저장하고 DB에서 자동 생성된 ID가 포함된 [MovieRecord]를 반환합니다.
     *
     * @param movie 저장할 영화 정보 (id는 무시됨)
     * @return DB에 저장된 영화 (id 포함)
     */
    suspend fun save(movie: MovieRecord): MovieRecord {
        log.debug { "Save new movie. movie=$movie" }

        val id = MovieTable.insertAndGetId {
            it[name] = movie.name
            it[producerName] = movie.producerName
            if (movie.releaseDate.isNotBlank()) {
                it[releaseDate] = LocalDate.parse(movie.releaseDate)
            }
        }

        return movie.copy(id = id.value)
    }

    /**
     * 파라미터 Map을 기반으로 영화를 동적 조건으로 검색합니다.
     *
     * 지원 파라미터: `id`, `name`, `producerName`, `releaseDate`
     * 각 파라미터가 존재하면 AND 조건으로 추가됩니다.
     *
     * @param params 검색 조건 맵 (키: 컬럼명, 값: 검색값)
     * @return 조건에 맞는 영화 Flow
     */
    fun searchMovies(params: Map<String, String?>): Flow<MovieRecord> {
        log.debug { "Search Movie by params. params: $params" }

        val query = MovieTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                MovieTable::id.name          -> value?.run { query.andWhere { MovieTable.id eq value.toLong() } }
                MovieTable::name.name        -> value?.run { query.andWhere { MovieTable.name eq value } }
                MovieTable::producerName.name -> value?.run { query.andWhere { MovieTable.producerName eq value } }
                MovieTable::releaseDate.name -> value?.run {
                    query.andWhere { MovieTable.releaseDate eq LocalDate.parse(value) }
                }
            }
        }

        return query.map { it.toEntity() }
    }

    /**
     * 전체 영화 목록을 배우 정보와 함께 조회합니다.
     *
     * `MovieTable ⟵ ActorInMovieTable ⟶ ActorTable` 3-way INNER JOIN으로 구현되며,
     * `bufferUntilChanged`를 이용해 동일 영화에 속한 배우들을 하나의 레코드로 그룹핑합니다.
     *
     * @return 영화별 배우 목록이 포함된 [MovieWithActorRecord] Flow
     */
    fun getAllMoviesWithActors(): Flow<MovieWithActorRecord> {
        log.debug { "Get all movies with actors" }

        return MovieActorJoin
            .select(
                MovieTable.id,
                MovieTable.name,
                MovieTable.producerName,
                MovieTable.releaseDate,
                ActorTable.id,
                ActorTable.firstName,
                ActorTable.lastName,
                ActorTable.birthday
            )
            .map { row ->
                val movie = row.toMovieRecord()
                val actor = row.toActorRecord()
                log.debug { "Add actor in movie[${movie.id}]. actor=$actor" }

                movie to actor
            }
            .bufferUntilChanged { it.first.id }
            .mapNotNull { pairs ->
                val movie = pairs.first().first
                val actors = pairs.map { it.second }
                movie.toMovieWithActorRecord(actors)
            }
    }

    /**
     * 특정 영화를 배우 정보와 함께 조회합니다.
     *
     * @param movieId 조회할 영화 ID
     * @return 배우 목록이 포함된 [MovieWithActorRecord], 영화가 없으면 `null`
     */
    suspend fun getMovieWithActors(movieId: Long): MovieWithActorRecord? {
        log.debug { "Get movie with actors. movieId: $movieId" }

        return MovieActorJoin
            .select(
                MovieTable.id,
                MovieTable.name,
                MovieTable.producerName,
                MovieTable.releaseDate,
                ActorTable.id,
                ActorTable.firstName,
                ActorTable.lastName,
                ActorTable.birthday
            )
            .where { MovieTable.id eq movieId }
            .map { row ->
                val movie = row.toMovieRecord()
                val actor = row.toActorRecord()
                log.debug { "Add actor in movie[${movie.id}]. actor=$actor" }

                movie to actor
            }
            .bufferUntilChanged { it.first.id }
            .mapNotNull { pairs ->
                val movie = pairs.first().first
                val actors = pairs.map { it.second }
                movie.toMovieWithActorRecord(actors)
            }
            .firstOrNull()
    }

    /**
     * 영화별 출연 배우 수를 집계하여 반환합니다.
     *
     * `COUNT(ActorTable.id)`를 `GROUP BY MovieTable.name`으로 집계합니다.
     *
     * @return 영화 이름과 배우 수가 담긴 [MovieActorCountRecord] Flow
     */
    fun getMovieActorsCount(): Flow<MovieActorCountRecord> {
        log.debug { "Get movie actors count." }

        val actorCountAlias = ActorTable.id.count().alias("actorCount")

        return MovieActorJoin
            .select(
                MovieTable.name,
                actorCountAlias
            )
            .groupBy(MovieTable.name)
            .map { row ->
                MovieActorCountRecord(
                    movieName = row[MovieTable.name],
                    actorCount = row[actorCountAlias].toInt()
                )
            }
    }

    /**
     * 제작자(producerName)가 직접 배우로 출연한 영화를 조회합니다.
     *
     * `ActorTable`을 `MovieTable.producerName = ActorTable.firstName` 조건으로 JOIN하여
     * 제작자와 배우가 동일인인 경우를 찾습니다.
     *
     * @return 제작자 겸 배우 정보가 포함된 [MovieWithProducingActorRecord] Flow
     */
    fun findMoviesWithActingProducers(): Flow<MovieWithProducingActorRecord> {
        log.debug { "Find movies with acting producers." }

        return moviesWithActingProducersJoin
            .select(
                MovieTable.name,
                ActorTable.firstName,
                ActorTable.lastName
            )
            .map {
                it.toMovieWithProducingActorRecord()
            }
    }
}
