package exposed.r2dbc.examples.ecosystem.trino

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.select
import java.io.Serializable

object TrinoLineItems: Table("trino_line_items") {
    val orderKey = long("order_key")
    val partKey = long("part_key")
    val quantity = decimal("quantity", precision = 12, scale = 2)
    val returnFlag = varchar("return_flag", 1)
}

data class TrinoSessionProfile(
    val catalog: String,
    val schema: String,
    val source: String,
    val clientTags: List<String> = emptyList(),
    val sessionProperties: Map<String, String> = emptyMap(),
): Serializable {

    init {
        catalog.requireNotBlank("catalog")
        schema.requireNotBlank("schema")
        source.requireNotBlank("source")
        clientTags.forEach { it.requireNotBlank("clientTags") }
        sessionProperties.forEach { (key, value) ->
            key.requireNotBlank("sessionProperties key")
            value.requireNotBlank("sessionProperties[$key]")
        }
    }

    fun toQuerySession(): TrinoQuerySession {
        val headers = linkedMapOf(
            "X-Trino-Catalog" to catalog,
            "X-Trino-Schema" to schema,
            "X-Trino-Source" to source,
        )
        if (clientTags.isNotEmpty()) {
            headers["X-Trino-Client-Tags"] = clientTags.joinToString(",")
        }
        if (sessionProperties.isNotEmpty()) {
            headers["X-Trino-Session"] = sessionProperties.entries.joinToString(",") { (key, value) ->
                "$key=$value"
            }
        }
        return TrinoQuerySession(catalogSchema = "$catalog.$schema", headers = headers)
    }
}

data class TrinoQuerySession(
    val catalogSchema: String,
    val headers: Map<String, String>,
): Serializable

fun R2dbcTransaction.renderTrinoCandidateSql(): String =
    TrinoLineItems
        .select(
            TrinoLineItems.orderKey,
            TrinoLineItems.partKey,
            TrinoLineItems.quantity,
            TrinoLineItems.returnFlag,
        )
        .prepareSQL(this, prepared = false)

fun renderTrinoExplainSql(sql: String): String =
    "EXPLAIN ${sql.requireNotBlank("sql")}"

private fun String.requireNotBlank(name: String): String {
    require(isNotBlank()) { "$name must not be blank" }
    return this
}
