package exposed.r2dbc.workshop.springwebflux.domain

import exposed.r2dbc.workshop.springwebflux.domain.MovieSchema.ActorTable
import exposed.r2dbc.workshop.springwebflux.domain.MovieSchema.MovieTable
import org.jetbrains.exposed.v1.core.ResultRow


fun ResultRow.toActorDTO() = ActorDTO(
    id = this[ActorTable.id].value,
    firstName = this[ActorTable.firstName],
    lastName = this[ActorTable.lastName],
    birthday = this[ActorTable.birthday]?.toString()
)


fun ResultRow.toMovieDTO() = MovieDTO(
    id = this[MovieTable.id].value,
    name = this[MovieTable.name],
    producerName = this[MovieTable.producerName],
    releaseDate = this[MovieTable.releaseDate].toString(),
)

fun ResultRow.toMovieWithActorDTO(actors: List<ActorDTO>) = MovieWithActorDTO(
    id = this[MovieTable.id].value,
    name = this[MovieTable.name],
    producerName = this[MovieTable.producerName],
    releaseDate = this[MovieTable.releaseDate].toString(),
    actors = actors.toMutableList(),
)

fun MovieDTO.toMovieWithActorDTO(actors: Collection<ActorDTO>) = MovieWithActorDTO(
    id = this.id,
    name = this.name,
    producerName = this.producerName,
    releaseDate = this.releaseDate,
    actors = actors.toMutableList(),
)

fun ResultRow.toMovieWithProducingActorDTO() = MovieWithProducingActorDTO(
    movieName = this[MovieTable.name],
    producerActorName = this[ActorTable.firstName] + " " + this[ActorTable.lastName]
)
