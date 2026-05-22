package exposed.r2dbc.multitenant.onboarding.domain.model

import exposed.r2dbc.multitenant.onboarding.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.onboarding.domain.model.MovieSchema.MovieTable
import org.jetbrains.exposed.v1.core.ResultRow

/**
 * Converts an actor query row to [ActorRecord].
 */
fun ResultRow.toActorRecord() = ActorRecord(
    id = this[ActorTable.id].value,
    firstName = this[ActorTable.firstName],
    lastName = this[ActorTable.lastName],
    birthday = this[ActorTable.birthday]?.toString()
)

/**
 * Converts a movie query row to [MovieRecord].
 */
fun ResultRow.toMovieRecord() = MovieRecord(
    id = this[MovieTable.id].value,
    name = this[MovieTable.name],
    producerName = this[MovieTable.producerName],
    releaseDate = this[MovieTable.releaseDate].toString(),
)

/**
 * Combines a movie query row with actors into [MovieWithActorRecord].
 */
fun ResultRow.toMovieWithActorRecord(actors: List<ActorRecord>) = MovieWithActorRecord(
    id = this[MovieTable.id].value,
    name = this[MovieTable.name],
    producerName = this[MovieTable.producerName],
    releaseDate = this[MovieTable.releaseDate].toString(),
    actors = actors.toMutableList(),
)

/**
 * Combines a converted [MovieRecord] with actors into [MovieWithActorRecord].
 */
fun MovieRecord.toMovieWithActorRecord(actors: Collection<ActorRecord>) = MovieWithActorRecord(
    id = this.id,
    name = this.name,
    producerName = this.producerName,
    releaseDate = this.releaseDate,
    actors = actors.toMutableList(),
)

/**
 * Converts a movie and producer join row to [MovieWithProducingActorRecord].
 */
fun ResultRow.toMovieWithProducingActorRecord() = MovieWithProducingActorRecord(
    movieName = this[MovieTable.name],
    producerActorName = this[ActorTable.firstName] + " " + this[ActorTable.lastName]
)
