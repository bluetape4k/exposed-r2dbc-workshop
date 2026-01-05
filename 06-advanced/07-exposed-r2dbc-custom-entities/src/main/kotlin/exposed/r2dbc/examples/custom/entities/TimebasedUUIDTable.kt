package exposed.r2dbc.examples.custom.entities

import io.bluetape4k.idgenerators.uuid.TimebasedUuid
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import java.util.*

/**
 * 테이블의 `id` 컬럼을 클라이언트에서 생성한 Timebased UUID 값을 사용하는 Table
 */
open class TimebasedUUIDTable(
    name: String = "",
    columnName: String = "id",
): IdTable<UUID>(name) {
    final override val id = uuid(columnName)
        .clientDefault { TimebasedUuid.Reordered.nextId() }
        .entityId()

    final override val primaryKey = PrimaryKey(id)
}
