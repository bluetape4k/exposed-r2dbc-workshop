package exposed.r2dbc.examples.custom.entities

import io.bluetape4k.idgenerators.ksuid.KsuidMillis
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Primary Key 를 Client에서 생성되는 [KsuidMillis] 값으로 사용하는 Table
 */
open class KsuidMillisTable(name: String = "", columnName: String = "id"): IdTable<String>(name) {
    final override val id: Column<EntityID<String>> =
        varchar(columnName, 27).clientDefault { KsuidMillis.nextIdAsString() }.entityId()

    final override val primaryKey = PrimaryKey(id)
}
