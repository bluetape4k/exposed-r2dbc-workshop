package exposed.r2dbc.examples.ecosystem.starrocks

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

object StarRocksOrderEvents: Table("starrocks_order_events") {
    val orderId = long("order_id")
    val region = varchar("region", 32)
    val eventDate = varchar("event_date", 10)
    val revenue = decimal("revenue", precision = 14, scale = 2)

    override val primaryKey: PrimaryKey = PrimaryKey(orderId)
}

data class StarRocksAnalyticsProfile(
    val host: String = "localhost",
    val port: Int = 9030,
    val catalog: String = "default_catalog",
    val database: String = "analytics",
    val r2dbcBoundary: String = "No default StarRocks R2DBC driver is opened by this workshop.",
): Serializable {

    init {
        host.requireNotBlank("host")
        catalog.requireNotBlank("catalog")
        database.requireNotBlank("database")
        require(port in 1..65535) { "port must be in 1..65535" }
    }

    fun jdbcUrlPreview(): String = "jdbc:starrocks://$host:$port/$catalog.$database"
}

data class StarRocksOrderEvent(
    val orderId: Long,
    val region: String,
    val eventDate: String,
    val revenue: BigDecimal,
): Serializable {
    init {
        region.requireNotBlank("region")
        eventDate.requireNotBlank("eventDate")
    }
}

data class RegionalRevenueRollup(
    val region: String,
    val eventDate: String,
    val orderCount: Long,
    val grossRevenue: BigDecimal,
): Serializable

suspend fun R2dbcTransaction.projectRegionalRevenue(): List<RegionalRevenueRollup> =
    StarRocksOrderEvents
        .selectAll()
        .toList()
        .map {
            StarRocksOrderEvent(
                orderId = it[StarRocksOrderEvents.orderId],
                region = it[StarRocksOrderEvents.region],
                eventDate = it[StarRocksOrderEvents.eventDate],
                revenue = it[StarRocksOrderEvents.revenue],
            )
        }
        .groupBy { it.region to it.eventDate }
        .map { (key, events) ->
            RegionalRevenueRollup(
                region = key.first,
                eventDate = key.second,
                orderCount = events.size.toLong(),
                grossRevenue = events.fold(BigDecimal.ZERO) { acc, event -> acc + event.revenue },
            )
        }
        .sortedWith(compareBy<RegionalRevenueRollup> { it.region }.thenBy { it.eventDate })

fun R2dbcTransaction.renderStarRocksRollupSql(): String {
    val orderCount = StarRocksOrderEvents.orderId.count()
    val grossRevenue = StarRocksOrderEvents.revenue.sum()
    return StarRocksOrderEvents
        .select(
            StarRocksOrderEvents.region,
            StarRocksOrderEvents.eventDate,
            orderCount,
            grossRevenue,
        )
        .groupBy(StarRocksOrderEvents.region, StarRocksOrderEvents.eventDate)
        .orderBy(StarRocksOrderEvents.region to SortOrder.ASC, StarRocksOrderEvents.eventDate to SortOrder.ASC)
        .prepareSQL(this, prepared = false)
}

private fun String.requireNotBlank(name: String): String {
    require(isNotBlank()) { "$name must not be blank" }
    return this
}
