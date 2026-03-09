package exposed.r2dbc.multitenant.webflux.domain.repository

import exposed.r2dbc.multitenant.webflux.domain.model.ActorRecord
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.webflux.domain.model.toActorRecord
import io.bluetape4k.exposed.r2dbc.repository.R2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 멀티테넌트 환경에서 배우(Actor) 도메인의 데이터 접근 계층입니다.
 *
 * [R2dbcRepository]를 상속하여 기본 CRUD 기능을 제공하며,
 * 테넌트별 스키마가 `suspendTransactionWithCurrentTenant` 컨텍스트로 자동 적용됩니다.
 * 쿼리 파라미터 기반 검색 및 저장 기능을 포함합니다.
 */
@Repository
class ActorR2dbcRepository: R2dbcRepository<Long, ActorTable, ActorRecord> {

    companion object: KLoggingChannel()

    override val table = ActorTable

    override suspend fun ResultRow.toEntity(): ActorRecord = toActorRecord()

    fun searchActors(params: Map<String, String?>): Flow<ActorRecord> {
        log.debug { "Search Actors by params. params: $params" }

        val query = ActorTable.selectAll()

        params.forEach { (key, value) ->
            when (key) {
                LongIdTable::id.name      -> value?.run { query.andWhere { ActorTable.id eq value.toLong() } }
                ActorTable::firstName.name -> value?.run { query.andWhere { ActorTable.firstName eq value } }
                ActorTable::lastName.name -> value?.run { query.andWhere { ActorTable.lastName eq value } }
                ActorTable::birthday.name -> value?.run {
                    query.andWhere { ActorTable.birthday eq LocalDate.parse(value) }
                }
            }
        }

        return query.map { it.toEntity() }
    }

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
