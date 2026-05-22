package exposed.r2dbc.multitenant.ktor.domain.model

import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.MovieTable
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toActorRecord(): ActorRecord =
    ActorRecord(
        id = this[ActorTable.id].value,
        firstName = this[ActorTable.firstName],
        lastName = this[ActorTable.lastName],
        birthday = this[ActorTable.birthday]?.toString(),
    )

internal fun ResultRow.toMovieRecord(): MovieRecord =
    MovieRecord(
        id = this[MovieTable.id].value,
        name = this[MovieTable.name],
        producerName = this[MovieTable.producerName],
        releaseDate = this[MovieTable.releaseDate].toString(),
    )
