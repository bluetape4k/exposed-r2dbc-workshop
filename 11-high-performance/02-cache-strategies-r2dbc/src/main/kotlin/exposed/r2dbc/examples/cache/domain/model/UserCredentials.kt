package exposed.r2dbc.examples.cache.domain.model

import exposed.r2dbc.examples.cache.utils.faker
import io.bluetape4k.codec.Base58
import io.bluetape4k.exposed.core.HasIdentifier
import io.bluetape4k.exposed.dao.id.TimebasedUUIDBase62Table
import io.bluetape4k.idgenerators.uuid.TimebasedUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant

/**
 * Read Through Only 를 통해 DB에서 읽어온 사용자 정보를 캐시에 저장합니다.
 * 캐시 Invalidate 시에 DB에 영향을 주지 않아야 합니다.
 */
object UserCredentialsTable: TimebasedUUIDBase62Table("user_credentials") {
    val username = varchar("username", 36).uniqueIndex()
    val email = varchar("email", 36)
    val lastLoginAt = timestamp("last_login_at").nullable()

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").nullable()
}

data class UserCredentialsRecord(
    override val id: String,
    val username: String,
    val email: String,
    val lastLoginAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
): HasIdentifier<String>

fun ResultRow.toUserCredentialsRecord() = UserCredentialsRecord(
    id = this[UserCredentialsTable.id].value,
    username = this[UserCredentialsTable.username],
    email = this[UserCredentialsTable.email],
    lastLoginAt = this[UserCredentialsTable.lastLoginAt],
    createdAt = this[UserCredentialsTable.createdAt],
    updatedAt = this[UserCredentialsTable.updatedAt],
)

fun newUserCredentialsRecord() = UserCredentialsRecord(
    id = TimebasedUuid.Reordered.nextIdAsString(),
    username = faker.credentials().username() + "." + Base58.randomString(4),
    email = faker.internet().emailAddress(),
    lastLoginAt = Instant.now(),
    createdAt = null,
    updatedAt = null,
)
