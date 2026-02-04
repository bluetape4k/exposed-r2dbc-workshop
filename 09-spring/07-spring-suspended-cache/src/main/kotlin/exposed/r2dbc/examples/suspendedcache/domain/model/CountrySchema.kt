package exposed.r2dbc.examples.suspendedcache.domain.model

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object CountryTable: IntIdTable("countries") {
    val code = varchar("code", 2).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description", eagerLoading = true).nullable()
}

data class CountryRecord(
    val code: String,
    val name: String,
    val description: String? = null,
)

fun ResultRow.toCountryRecord(): CountryRecord {
    return CountryRecord(
        code = this[CountryTable.code].uppercase(),
        name = this[CountryTable.name],
        description = this[CountryTable.description]?.takeIf { it.isNotBlank() },
    )
}
