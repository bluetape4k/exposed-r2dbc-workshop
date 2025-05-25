package exposed.r2dbc.multitenant.webflux.domain.repository

import exposed.r2dbc.multitenant.webflux.domain.dto.ActorDTO
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.webflux.domain.model.toActorDTO
import io.bluetape4k.exposed.r2dbc.repository.ExposedR2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ActorR2dbcRepository: ExposedR2dbcRepository<ActorDTO, Long> {

    companion object: KLoggingChannel()

    override val table: IdTable<Long> = ActorTable

    override suspend fun ResultRow.toEntity(): ActorDTO = toActorDTO()

    fun searchActors(params: Map<String, String?>): Flow<ActorDTO> {
        log.debug { "Search Actors by params. params: $params" }

        val query = ActorTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                LongIdTable::id.name -> value?.run { query.andWhere { ActorTable.id eq value.toLong() } }
                ActorTable::firstName.name -> value?.run { query.andWhere { ActorTable.firstName eq value } }
                ActorTable::lastName.name -> value?.run { query.andWhere { ActorTable.lastName eq value } }
                ActorTable::birthday.name -> value?.run {
                    query.andWhere { ActorTable.birthday eq LocalDate.parse(value) }
                }
            }
        }

        return query.map { it.toEntity() }
    }

    suspend fun save(actor: ActorDTO): ActorDTO {
        log.debug { "Save new actor. actor=$actor" }

        val id = ActorTable.insertAndGetId {
            it[firstName] = actor.firstName
            it[lastName] = actor.lastName
            it[birthday] = actor.birthday?.let { LocalDate.parse(it) }
        }

        return actor.copy(id = id.value)
    }
}
