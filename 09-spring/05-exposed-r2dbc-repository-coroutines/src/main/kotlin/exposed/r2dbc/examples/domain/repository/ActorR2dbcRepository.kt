package exposed.r2dbc.examples.domain.repository

import exposed.r2dbc.examples.domain.model.ActorRecord
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.toActorRecord
import io.bluetape4k.exposed.r2dbc.repository.R2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 배우(Actor) 엔티티에 대한 Exposed R2DBC 저장소 구현체.
 */
@Repository
class ActorR2dbcRepository: R2dbcRepository<Long, ActorTable, ActorRecord> {

    companion object: KLoggingChannel()

    override val table = ActorTable

    override suspend fun ResultRow.toEntity(): ActorRecord = toActorRecord()

    /**
     * 요청 파라미터 기반으로 배우 목록을 동적 검색한다.
     */
    fun searchActors(params: Map<String, String?>): Flow<ActorRecord> {
        log.debug { "Search Actors by params. params: $params" }

        val query = ActorTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                ActorTable::id.name       -> value?.run { query.andWhere { ActorTable.id eq value.toLong() } }
                ActorTable::firstName.name -> value?.run { query.andWhere { ActorTable.firstName eq value } }
                ActorTable::lastName.name -> value?.run { query.andWhere { ActorTable.lastName eq value } }
                ActorTable::birthday.name -> value?.run {
                    query.andWhere { ActorTable.birthday eq LocalDate.parse(value) }
                }
            }
        }

        return query.map { it.toEntity() }
    }

    /**
     * 신규 배우를 저장하고 생성된 식별자를 반영한 레코드를 반환한다.
     */
    suspend fun save(actor: ActorRecord): ActorRecord {
        log.debug { "Save new actor. actor=$actor" }

        val id = ActorTable.insertAndGetId {
            it[firstName] = actor.firstName
            it[lastName] = actor.lastName
            it[birthday] = actor.birthday?.let { LocalDate.parse(it) }
        }

        return actor.copy(id = id.value)
    }
}
