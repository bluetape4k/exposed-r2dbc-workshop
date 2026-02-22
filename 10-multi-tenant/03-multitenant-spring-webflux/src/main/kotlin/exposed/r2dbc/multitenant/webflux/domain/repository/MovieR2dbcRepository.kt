package exposed.r2dbc.multitenant.webflux.domain.repository

import exposed.r2dbc.multitenant.webflux.domain.model.MovieActorCountRecord
import exposed.r2dbc.multitenant.webflux.domain.model.MovieRecord
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.multitenant.webflux.domain.model.MovieWithActorRecord
import exposed.r2dbc.multitenant.webflux.domain.model.MovieWithProducingActorRecord
import exposed.r2dbc.multitenant.webflux.domain.model.toActorRecord
import exposed.r2dbc.multitenant.webflux.domain.model.toMovieRecord
import exposed.r2dbc.multitenant.webflux.domain.model.toMovieWithActorRecord
import exposed.r2dbc.multitenant.webflux.domain.model.toMovieWithProducingActorRecord
import io.bluetape4k.coroutines.flow.extensions.bufferUntilChanged
import io.bluetape4k.exposed.r2dbc.repository.ExposedR2dbcRepository
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
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class MovieR2dbcRepository: ExposedR2dbcRepository<MovieRecord, Long> {

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

    override val table: IdTable<Long> = MovieTable

    override suspend fun ResultRow.toEntity(): MovieRecord = toMovieRecord()

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

    fun searchMovies(params: Map<String, String?>): Flow<MovieRecord> {
        log.debug { "Search Movie by params. params: $params" }

        val query = MovieTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                LongIdTable::id.name         -> value?.run { query.andWhere { MovieTable.id eq value.toLong() } }
                MovieTable::name.name        -> value?.run { query.andWhere { MovieTable.name eq value } }
                MovieTable::producerName.name -> value?.run { query.andWhere { MovieTable.producerName eq value } }
                MovieTable::releaseDate.name -> value?.run {
                    query.andWhere { MovieTable.releaseDate eq LocalDate.parse(value) }
                }
            }
        }

        return query.map { it.toEntity() }
    }

    suspend fun getAllMoviesWithActors(): Flow<MovieWithActorRecord> {
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

                movie to actor
            }
            .bufferUntilChanged { it.first.id }
            .mapNotNull { pairs ->
                val movie = pairs.first().first
                val actors = pairs.map { it.second }
                movie.toMovieWithActorRecord(actors)
            }
    }

    suspend fun getMovieWithActors(movieId: Long): MovieWithActorRecord? {
        log.debug { "Get movie with actors. movieId: $movieId" }

        val row = MovieActorJoin
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
            .andWhere { MovieTable.id eq movieId }
            .firstOrNull() ?: return null

        val movie = row.toMovieRecord()
        val actor = row.toActorRecord()

        return movie.toMovieWithActorRecord(mutableSetOf(actor))
    }

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
