package exposed.r2dbc.examples.cache.ktor.domain

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate

/**
 * User table for the Ktor cache strategy example.
 */
object UserTable: LongIdTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
    val address = varchar("address", 255).nullable()
    val zipcode = varchar("zipcode", 24).nullable()
    val birthDate = date("birth_date").nullable()
}

internal fun ResultRow.toUserRecord(): UserRecord =
    UserRecord(
        id = this[UserTable.id].value,
        username = this[UserTable.username],
        firstName = this[UserTable.firstName],
        lastName = this[UserTable.lastName],
        address = this[UserTable.address],
        zipcode = this[UserTable.zipcode],
        birthDate = this[UserTable.birthDate]?.toString(),
    )

internal fun UpsertUserRequest.toUserRecord(id: Long = 0L): UserRecord =
    UserRecord(
        id = id,
        username = username,
        firstName = firstName,
        lastName = lastName,
        address = address,
        zipcode = zipcode,
        birthDate = birthDate,
    )

internal fun String?.toLocalDateOrNull(): LocalDate? =
    this?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
