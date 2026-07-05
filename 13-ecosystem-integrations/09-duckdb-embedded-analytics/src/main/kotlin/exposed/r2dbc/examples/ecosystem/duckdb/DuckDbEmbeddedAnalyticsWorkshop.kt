package exposed.r2dbc.examples.ecosystem.duckdb

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.io.Serializable
import java.math.BigDecimal

object DuckDbOrderEvents: Table("duckdb_order_events") {
    val orderId = long("order_id")
    val region = varchar("region", 32)
    val category = varchar("category", 48)
    val eventDate = varchar("event_date", 10)
    val amount = decimal("amount", precision = 14, scale = 2)

    override val primaryKey: PrimaryKey = PrimaryKey(orderId)
}

data class DuckDbR2dbcBoundary(
    val defaultMode: String,
    val rationale: String,
): Serializable {
    companion object {
        fun default(): DuckDbR2dbcBoundary =
            DuckDbR2dbcBoundary(
                defaultMode = "H2 R2DBC local projection",
                rationale = "DuckDB JDBC remains the embedded engine path; this module keeps the R2DBC workshop executable without a DuckDB R2DBC driver.",
            )
    }
}

data class DuckDbOrderEvent(
    val orderId: Long,
    val region: String,
    val category: String,
    val eventDate: String,
    val amount: BigDecimal,
): Serializable {
    init {
        region.requireNotBlank("region")
        category.requireNotBlank("category")
        eventDate.requireNotBlank("eventDate")
    }
}

data class DailyCategorySales(
    val region: String,
    val category: String,
    val eventDate: String,
    val orderCount: Long,
    val grossAmount: BigDecimal,
): Serializable

suspend fun R2dbcTransaction.projectDailyCategorySales(): List<DailyCategorySales> =
    DuckDbOrderEvents
        .selectAll()
        .toList()
        .map {
            DuckDbOrderEvent(
                orderId = it[DuckDbOrderEvents.orderId],
                region = it[DuckDbOrderEvents.region],
                category = it[DuckDbOrderEvents.category],
                eventDate = it[DuckDbOrderEvents.eventDate],
                amount = it[DuckDbOrderEvents.amount],
            )
        }
        .groupBy { Triple(it.region, it.category, it.eventDate) }
        .map { (key, events) ->
            DailyCategorySales(
                region = key.first,
                category = key.second,
                eventDate = key.third,
                orderCount = events.size.toLong(),
                grossAmount = events.fold(BigDecimal.ZERO) { acc, event -> acc + event.amount },
            )
        }
        .sortedWith(compareBy<DailyCategorySales> { it.region }.thenBy { it.category }.thenBy { it.eventDate })

fun R2dbcTransaction.renderDailyCategorySalesSql(): String {
    val orderCount = DuckDbOrderEvents.orderId.count()
    val grossAmount = DuckDbOrderEvents.amount.sum()
    return DuckDbOrderEvents
        .select(
            DuckDbOrderEvents.region,
            DuckDbOrderEvents.category,
            DuckDbOrderEvents.eventDate,
            orderCount,
            grossAmount,
        )
        .groupBy(DuckDbOrderEvents.region, DuckDbOrderEvents.category, DuckDbOrderEvents.eventDate)
        .orderBy(
            DuckDbOrderEvents.region to SortOrder.ASC,
            DuckDbOrderEvents.category to SortOrder.ASC,
            DuckDbOrderEvents.eventDate to SortOrder.ASC,
        )
        .prepareSQL(this, prepared = false)
}

private fun String.requireNotBlank(name: String): String {
    require(isNotBlank()) { "$name must not be blank" }
    return this
}
