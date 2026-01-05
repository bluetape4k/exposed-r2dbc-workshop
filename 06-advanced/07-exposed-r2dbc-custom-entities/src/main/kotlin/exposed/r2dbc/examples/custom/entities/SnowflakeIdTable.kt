package exposed.r2dbc.examples.custom.entities

import io.bluetape4k.idgenerators.snowflake.Snowflakers
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * 테이블의 `id` 컬럼을 클라이언트에서 생성한 Snowflake ID 값을 사용하는 Table
 */
open class SnowflakeIdTable(
    name: String = "",
    columnName: String = "id",
): IdTable<Long>(name) {
    final override val id = long(columnName)
        .clientDefault { Snowflakers.Global.nextId() }
        .entityId()

    final override val primaryKey = PrimaryKey(id)
}
