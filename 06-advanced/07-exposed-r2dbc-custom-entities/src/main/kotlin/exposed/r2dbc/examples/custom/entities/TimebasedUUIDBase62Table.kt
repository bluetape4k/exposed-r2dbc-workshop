package exposed.r2dbc.examples.custom.entities

import io.bluetape4k.idgenerators.uuid.TimebasedUuid
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * 테이블의 `id` 컬럼을 클라이언트에서 생성한 Timebased UUID 값을 Base62로 인코딩한 값을 사용하는 Table
 */
open class TimebasedUUIDBase62Table(
    name: String = "",
    columnName: String = "id",
): IdTable<String>(name) {
    final override val id = varchar(columnName, 22)
        .clientDefault { TimebasedUuid.Reordered.nextIdAsString() }
        .entityId()

    final override val primaryKey = PrimaryKey(id)
}
