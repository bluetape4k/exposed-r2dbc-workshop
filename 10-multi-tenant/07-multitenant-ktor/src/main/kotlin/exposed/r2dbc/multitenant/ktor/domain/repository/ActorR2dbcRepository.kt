package exposed.r2dbc.multitenant.ktor.domain.repository

import exposed.r2dbc.multitenant.ktor.domain.model.ActorRecord
import exposed.r2dbc.multitenant.ktor.domain.model.CreateActorRequest
import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.ktor.domain.model.toActorRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDate

/**
 * Exposed R2DBC actor repository used inside tenant-aware transactions.
 */
class ActorR2dbcRepository {

    companion object: KLoggingChannel()

    fun findAll(): Flow<ActorRecord> {
        log.debug { "Find all actors for current tenant schema" }
        return ActorTable
            .selectAll()
            .map { it.toActorRecord() }
    }

    fun findById(id: Long): Flow<ActorRecord> {
        require(id > 0) { "id must be positive" }
        log.debug { "Find actor by id. id=$id" }
        return ActorTable
            .selectAll()
            .where { ActorTable.id eq id }
            .map { it.toActorRecord() }
    }

    suspend fun findOne(id: Long): ActorRecord? =
        findById(id).singleOrNull()

    suspend fun save(request: CreateActorRequest): ActorRecord {
        val firstName = request.firstName.requireNotBlank("firstName")
        val lastName = request.lastName.requireNotBlank("lastName")
        val birthday = request.birthday?.takeIf { it.isNotBlank() }
        log.debug { "Save actor. firstName=$firstName, lastName=$lastName" }

        val id = ActorTable.insertAndGetId {
            it[ActorTable.firstName] = firstName
            it[ActorTable.lastName] = lastName
            it[ActorTable.birthday] = birthday?.let { value -> LocalDate.parse(value) }
        }
        return ActorRecord(
            id = id.value,
            firstName = firstName,
            lastName = lastName,
            birthday = birthday,
        )
    }
}
