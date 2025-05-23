package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.params.provider.Arguments

abstract class AbstractCustomIdTableTest: R2dbcExposedTestBase() {

    companion object: KLoggingChannel() {
        const val GET_TESTDB_AND_ENTITY_COUNT = "getTestDBAndEntityCount"

        @JvmStatic
        fun getTestDBAndEntityCount(): List<Arguments> {
            val recordCounts = listOf(100, 500)

            return TestDB.enabledDialects().map { testDB ->
                recordCounts.map { entityCount ->
                    Arguments.of(testDB, entityCount)
                }
            }.flatten()
        }
    }
}
