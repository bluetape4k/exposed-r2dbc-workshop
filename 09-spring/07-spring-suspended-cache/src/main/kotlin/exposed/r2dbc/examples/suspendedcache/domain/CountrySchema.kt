package exposed.r2dbc.examples.suspendedcache.domain

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object CountryTable: IntIdTable("countries") {
    val code = char("code", 2).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description").nullable()
}

data class CountryDTO(
    val code: String,
    val name: String,
    val description: String? = null,
)

fun ResultRow.toCountryDTO(): CountryDTO {
    return CountryDTO(
        code = this[CountryTable.code],
        name = this[CountryTable.name],
        description = this[CountryTable.description],
    )
}
