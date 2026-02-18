package exposed.r2dbc.workshop.springwebflux.domain.repository

import exposed.r2dbc.workshop.springwebflux.domain.model.MovieActorCountRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieWithActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieWithProducingActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.toActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.toMovieRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.toMovieWithActorRecord
import exposed.r2dbc.workshop.springwebflux.domain.model.toMovieWithProducingActorRecord
import io.bluetape4k.coroutines.flow.extensions.bufferUntilChanged
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MovieRepository {

    companion object: KLoggingChannel() {
        private val MovieActorJoin by lazy {
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

    suspend fun count(): Long =
        MovieTable.selectAll().count()

    suspend fun findById(movieId: Long): MovieRecord? {
        log.debug { "Find Movie by id. id: $movieId" }

        return MovieTable
            .selectAll()
            .where { MovieTable.id eq movieId }
            .firstOrNull()
            ?.toMovieRecord()
    }

    fun findAll(): Flow<MovieRecord> {
        return MovieTable.selectAll().map { it.toMovieRecord() }
    }

    fun searchMovie(params: Map<String, String?>): Flow<MovieRecord> {
        log.debug { "Search Movie by params. params: $params" }

        val query = MovieTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                MovieTable::id.name          -> value?.run { query.andWhere { MovieTable.id eq value.toLong() } }
                MovieTable::name.name        -> value?.run { query.andWhere { MovieTable.name eq value } }
                MovieTable::producerName.name -> value?.run { query.andWhere { MovieTable.producerName eq value } }
                MovieTable::releaseDate.name -> value?.run {
                    query.andWhere {
                        MovieTable.releaseDate eq LocalDateTime.parse(value)
                    }
                }
            }
        }

        return query.map { it.toMovieRecord() }
    }

    suspend fun create(movie: MovieRecord): MovieRecord {
        log.debug { "Create Movie. movie: $movie" }

        val id = MovieTable.insertAndGetId {
            it[name] = movie.name
            it[producerName] = movie.producerName
            if (movie.releaseDate.isNotBlank()) {
                it[releaseDate] = LocalDateTime.parse(movie.releaseDate)
            }
        }
        return movie.copy(id = id.value)
    }

    suspend fun deleteById(movieId: Long): Int {
        log.debug { "Delete Movie by id. id: $movieId" }
        return MovieTable.deleteWhere { MovieTable.id eq movieId }
    }

    /**
     * ```sql
     * SELECT MOVIES.ID,
     *        MOVIES."name",
     *        MOVIES.PRODUCER_NAME,
     *        MOVIES.RELEASE_DATE,
     *        ACTORS.ID,
     *        ACTORS.FIRST_NAME,
     *        ACTORS.LAST_NAME,
     *        ACTORS.BIRTHDAY
     *   FROM MOVIES
     *          INNER JOIN ACTORS_IN_MOVIES ON MOVIES.ID = ACTORS_IN_MOVIES.MOVIE_ID
     *          INNER JOIN ACTORS ON ACTORS.ID = ACTORS_IN_MOVIES.ACTOR_ID
     * ```
     */
    fun getAllMoviesWithActors(): Flow<MovieWithActorRecord> {
        log.debug { "Get all movies with actors." }

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
     * `movieId`에 해당하는 [Movie] 와 출현한 [Actor]들의 정보를 eager loading 으로 가져온다.
     * ```sql
     * -- H2
     * SELECT MOVIES.ID, MOVIES."name", MOVIES.PRODUCER_NAME, MOVIES.RELEASE_DATE
     *   FROM MOVIES
     *  WHERE MOVIES.ID = 1;
     *
     * SELECT ACTORS.ID,
     *        ACTORS.FIRST_NAME,
     *        ACTORS.LAST_NAME,
     *        ACTORS.BIRTHDAY,
     *        ACTORS_IN_MOVIES.MOVIE_ID,
     *        ACTORS_IN_MOVIES.ACTOR_ID
     *   FROM ACTORS INNER JOIN ACTORS_IN_MOVIES ON ACTORS_IN_MOVIES.ACTOR_ID = ACTORS.ID
     *  WHERE ACTORS_IN_MOVIES.MOVIE_ID = 1
     * ```
     */
    suspend fun getMovieWithActors(movieId: Long): MovieWithActorRecord? {
        log.debug { "Get Movie with actors. movieId=$movieId" }
        val actors = ActorTable
            .innerJoin(ActorInMovieTable)
            .selectAll()
            .where { ActorInMovieTable.movieId eq movieId }
            .map { it.toActorRecord() }
            .toList()

        return MovieTable
            .selectAll()
            .where { MovieTable.id eq movieId }
            .firstOrNull()
            ?.toMovieWithActorRecord(actors)
    }

    /**
     * ```sql
     * SELECT MOVIES.ID,
     *        MOVIES."name",
     *        COUNT(ACTORS.ID)
     *   FROM MOVIES
     *          INNER JOIN ACTORS_IN_MOVIES ON MOVIES.ID = ACTORS_IN_MOVIES.MOVIE_ID
     *          INNER JOIN ACTORS ON ACTORS.ID = ACTORS_IN_MOVIES.ACTOR_ID
     *  GROUP BY MOVIES.ID
     * ```
     */
    fun getMovieActorsCount(): Flow<MovieActorCountRecord> {
        log.debug { "Get Movie actors count." }

        return MovieActorJoin
            .select(MovieTable.id, MovieTable.name, ActorTable.id.count())
            .groupBy(MovieTable.id)
            .map {
                MovieActorCountRecord(
                    movieName = it[MovieTable.name],
                    actorCount = it[ActorTable.id.count()].toInt()
                )
            }
    }

    /**
     * ```sql
     * SELECT MOVIES."name",
     *        ACTORS.FIRST_NAME,
     *        ACTORS.LAST_NAME
     *   FROM MOVIES
     *          INNER JOIN ACTORS_IN_MOVIES ON MOVIES.ID = ACTORS_IN_MOVIES.MOVIE_ID
     *          INNER JOIN ACTORS ON ACTORS.ID = ACTORS_IN_MOVIES.ACTOR_ID AND (MOVIES.PRODUCER_NAME = ACTORS.FIRST_NAME)
     * ```
     */
    fun findMoviesWithActingProducers(): Flow<MovieWithProducingActorRecord> {
        log.debug { "Find movies with acting producers." }

        val query = moviesWithActingProducersJoin
            .select(
                MovieTable.name,
                ActorTable.firstName,
                ActorTable.lastName
            )

        return query.map { it.toMovieWithProducingActorRecord() }
    }
}
