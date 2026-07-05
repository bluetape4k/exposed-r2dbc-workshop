package exposed.r2dbc.examples.ecosystem.starrocks

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal

class StarRocksOlapR2dbcTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `local R2DBC projection prepares StarRocks rollup rows without a StarRocks server`(testDB: TestDB) = runTest {
        withTables(testDB, StarRocksOrderEvents) {
            StarRocksOrderEvents.batchInsert(sampleEvents()) { event ->
                this[StarRocksOrderEvents.orderId] = event.orderId
                this[StarRocksOrderEvents.region] = event.region
                this[StarRocksOrderEvents.eventDate] = event.eventDate
                this[StarRocksOrderEvents.revenue] = event.revenue
            }

            val rollups = projectRegionalRevenue()
            val sql = renderStarRocksRollupSql()
            val profile = StarRocksAnalyticsProfile()

            rollups shouldBeEqualTo listOf(
                RegionalRevenueRollup("apac", "2026-06-29", orderCount = 2, grossRevenue = BigDecimal("42.50")),
                RegionalRevenueRollup("emea", "2026-06-29", orderCount = 1, grossRevenue = BigDecimal("31.00")),
            )
            sql.uppercase() shouldContain "GROUP BY"
            profile.jdbcUrlPreview() shouldBeEqualTo "jdbc:starrocks://localhost:9030/default_catalog.analytics"
            profile.r2dbcBoundary shouldContain "No default StarRocks R2DBC driver"
        }
    }

    private fun sampleEvents() = listOf(
        StarRocksOrderEvent(1001L, "apac", "2026-06-29", BigDecimal("12.50")),
        StarRocksOrderEvent(1002L, "apac", "2026-06-29", BigDecimal("30.00")),
        StarRocksOrderEvent(1003L, "emea", "2026-06-29", BigDecimal("31.00")),
    )
}
