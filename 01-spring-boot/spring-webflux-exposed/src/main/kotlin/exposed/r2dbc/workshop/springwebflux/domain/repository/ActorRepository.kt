package exposed.r2dbc.workshop.springwebflux.domain.repository


import exposed.r2dbc.shared.repository.MovieSchema.ActorTable
import exposed.r2dbc.workshop.springwebflux.domain.ActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.toActorDTO
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ActorRepository {

    companion object: KLoggingChannel()

    suspend fun count(): Long =
        ActorTable.selectAll().count()

    suspend fun findById(id: Long): ActorDTO? {
        log.debug { "Find Actor by id. id: $id" }
        return ActorTable.selectAll()
            .where { ActorTable.id eq id }
            .firstOrNull()
            ?.toActorDTO()
    }

    suspend fun findAll(): List<ActorDTO> {
        return ActorTable.selectAll().map { it.toActorDTO() }.toFastList()
    }

    suspend fun searchActor(params: Map<String, String?>): List<ActorDTO> {
        log.debug { "Search Actor by params. params: $params" }

        val query = ActorTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                ActorTable::id.name -> value?.run { query.andWhere { ActorTable.id eq value.toLong() } }
                ActorTable::firstName.name -> value?.run { query.andWhere { ActorTable.firstName eq value } }
                ActorTable::lastName.name -> value?.run { query.andWhere { ActorTable.lastName eq value } }
                ActorTable::birthday.name -> value?.run {
                    query.andWhere { ActorTable.birthday eq LocalDate.parse(value) }
                }
            }
        }
        return query.map { it.toActorDTO() }.toFastList()
    }

    suspend fun create(actor: ActorDTO): ActorDTO {
        log.debug { "Create Actor. actor: $actor" }

        val id = ActorTable.insertAndGetId {
            it[firstName] = actor.firstName
            it[lastName] = actor.lastName
            actor.birthday?.run {
                it[birthday] = runCatching { LocalDate.parse(actor.birthday) }.getOrNull()
            }
        }
        return actor.copy(id = id.value)
    }

    suspend fun deleteById(actorId: Long): Int {
        log.debug { "Delete Actor by id. id: $actorId" }
        return ActorTable.deleteWhere { ActorTable.id eq actorId }
    }
}
