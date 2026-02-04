package exposed.r2dbc.workshop.springwebflux.domain.model

import org.jetbrains.exposed.v1.core.ResultRow


fun ResultRow.toActorRecord() = ActorRecord(
    id = this[MovieSchema.ActorTable.id].value,
    firstName = this[MovieSchema.ActorTable.firstName],
    lastName = this[MovieSchema.ActorTable.lastName],
    birthday = this[MovieSchema.ActorTable.birthday]?.toString()
)


fun ResultRow.toMovieRecord() = MovieRecord(
    id = this[MovieSchema.MovieTable.id].value,
    name = this[MovieSchema.MovieTable.name],
    producerName = this[MovieSchema.MovieTable.producerName],
    releaseDate = this[MovieSchema.MovieTable.releaseDate].toString(),
)

fun ResultRow.toMovieWithActorRecord(actors: List<ActorRecord>) = MovieWithActorRecord(
    id = this[MovieSchema.MovieTable.id].value,
    name = this[MovieSchema.MovieTable.name],
    producerName = this[MovieSchema.MovieTable.producerName],
    releaseDate = this[MovieSchema.MovieTable.releaseDate].toString(),
    actors = actors.toMutableList(),
)

fun MovieRecord.toMovieWithActorRecord(actors: Collection<ActorRecord>) = MovieWithActorRecord(
    id = this.id,
    name = this.name,
    producerName = this.producerName,
    releaseDate = this.releaseDate,
    actors = actors.toMutableList(),
)

fun ResultRow.toMovieWithProducingActorRecord() = MovieWithProducingActorRecord(
    movieName = this[MovieSchema.MovieTable.name],
    producerActorName = this[MovieSchema.ActorTable.firstName] + " " + this[MovieSchema.ActorTable.lastName]
)
