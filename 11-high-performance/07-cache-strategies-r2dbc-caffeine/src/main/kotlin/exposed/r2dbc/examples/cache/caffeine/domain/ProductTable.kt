package exposed.r2dbc.examples.cache.caffeine.domain

import org.jetbrains.exposed.v1.core.dao.id.IdTable

/** SKU를 기본 키로 사용하는 Caffeine 예제 테이블입니다. */
object ProductTable : IdTable<String>("products") {
    override val id = varchar("sku", 40).entityId()
    val name = varchar("name", 120)
    val version = integer("version")

    override val primaryKey = PrimaryKey(id)
}
