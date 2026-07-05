package exposed.r2dbc.examples.ecosystem.duckdb

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

class DuckDbEmbeddedAnalyticsR2dbcTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `local R2DBC analytics keeps DuckDB embedded boundary explicit`(testDB: TestDB) = runTest {
        withTables(testDB, DuckDbOrderEvents) {
            DuckDbOrderEvents.batchInsert(sampleEvents()) { event ->
                this[DuckDbOrderEvents.orderId] = event.orderId
                this[DuckDbOrderEvents.region] = event.region
                this[DuckDbOrderEvents.category] = event.category
                this[DuckDbOrderEvents.eventDate] = event.eventDate
                this[DuckDbOrderEvents.amount] = event.amount
            }

            val rows = projectDailyCategorySales()
            val sql = renderDailyCategorySalesSql()
            val boundary = DuckDbR2dbcBoundary.default()

            rows shouldBeEqualTo listOf(
                DailyCategorySales("apac", "book", "2026-06-29", orderCount = 2, grossAmount = BigDecimal("42.50")),
                DailyCategorySales("apac", "tool", "2026-06-29", orderCount = 1, grossAmount = BigDecimal("9.50")),
                DailyCategorySales("emea", "book", "2026-06-30", orderCount = 1, grossAmount = BigDecimal("31.00")),
            )
            sql.uppercase() shouldContain "GROUP BY"
            boundary.defaultMode shouldBeEqualTo "H2 R2DBC local projection"
            boundary.rationale shouldContain "DuckDB JDBC"
        }
    }

    private fun sampleEvents() = listOf(
        DuckDbOrderEvent(1001L, "apac", "book", "2026-06-29", BigDecimal("12.50")),
        DuckDbOrderEvent(1002L, "apac", "book", "2026-06-29", BigDecimal("30.00")),
        DuckDbOrderEvent(1003L, "apac", "tool", "2026-06-29", BigDecimal("9.50")),
        DuckDbOrderEvent(1004L, "emea", "book", "2026-06-30", BigDecimal("31.00")),
    )
}
