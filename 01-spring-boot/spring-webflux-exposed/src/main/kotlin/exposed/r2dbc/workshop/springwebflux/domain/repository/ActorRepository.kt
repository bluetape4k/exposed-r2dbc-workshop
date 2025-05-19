package exposed.r2dbc.workshop.springwebflux.domain.repository


import exposed.r2dbc.shared.repository.MovieSchema.ActorTable
import exposed.r2dbc.workshop.springwebflux.domain.ActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.MovieSchema.ActorEntity
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ActorRepository {

    companion object: KLogging()

    suspend fun count(): Long =
        ActorTable.selectAll().count()

    suspend fun findById(id: Long): ActorEntity? {
        log.debug { "Find Actor by id. id: $id" }
        return ActorEntity.findById(id)
    }

    suspend fun findAll(): List<ActorEntity> {
        val rows = ActorTable.selectAll().toList()
        return rows.map { ActorEntity.wrapRow(it) }
    }

    suspend fun searchActor(params: Map<String, String?>): List<ActorEntity> {
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
        return query.toList().map { ActorEntity.wrapRow(it) }
    }

    suspend fun create(actor: ActorDTO): ActorEntity {
        log.debug { "Create Actor. actor: $actor" }

        return ActorEntity.new {
            firstName = actor.firstName
            lastName = actor.lastName
            actor.birthday?.let {
                birthday = runCatching { LocalDate.parse(actor.birthday) }.getOrNull()
            }
        }
    }

    suspend fun deleteById(actorId: Long): Int {
        log.debug { "Delete Actor by id. id: $actorId" }
        return ActorTable.deleteWhere { ActorTable.id eq actorId }
    }
}
