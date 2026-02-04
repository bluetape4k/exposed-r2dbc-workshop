package exposed.r2dbc.examples.cache.domain.model

import exposed.r2dbc.examples.cache.utils.faker
import io.bluetape4k.codec.Base58
import io.bluetape4k.exposed.core.HasIdentifier
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Read Through 를 통해 DB에서 읽어온 사용자 정보를 캐시에 저장합니다.
 * Write Through 를 통해 DB에 사용자 정보를 저장합니다.
 * 캐시 Invalidate 시에 DB에 영향을 주지 않도록 해야 합니다.
 */
object UserTable: LongIdTable("users") {
    val username = varchar("username", 255).uniqueIndex()

    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)

    val address = varchar("address", 255).nullable()
    val zipcode = varchar("zipcode", 24).nullable()
    val birthDate = date("birth_date").nullable()

    val avatar = blob("avatar").nullable()

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").nullable()
}

data class UserRecord(
    override val id: Long = 0L,
    val username: String,
    val firstName: String,
    val lastName: String,
    val address: String? = null,
    val zipcode: String? = null,
    val birthDate: LocalDate? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
): HasIdentifier<Long> {
    var avatar: ByteArray? = null

    fun withId(id: Long) = copy(id = id)
}

fun ResultRow.toUserRecord() = UserRecord(
    id = this[UserTable.id].value,
    username = this[UserTable.username],
    firstName = this[UserTable.firstName],
    lastName = this[UserTable.lastName],
    address = this[UserTable.address],
    zipcode = this[UserTable.zipcode],
    birthDate = this[UserTable.birthDate],
    createdAt = this[UserTable.createdAt],
    updatedAt = this[UserTable.updatedAt]
).also {
    it.avatar = this[UserTable.avatar]?.bytes
}

fun newUserRecord(newId: Long = 0L) = UserRecord(
    id = newId,
    username = faker.credentials().username() + "." + Base58.randomString(4),
    firstName = faker.name().firstName(),
    lastName = faker.name().lastName(),
    address = faker.address().fullAddress(),
    zipcode = faker.address().zipCode(),
    birthDate = LocalDate.now(),
    createdAt = null,
    updatedAt = null
).also {
    it.avatar = faker.image().base64JPG().toByteArray()
}
