package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.provider.Arguments

abstract class AbstractCustomIdTableTest: R2dbcExposedTestBase() {

    companion object: KLoggingChannel() {
        const val GET_TESTDB_AND_ENTITY_COUNT = "getTestDBAndEntityCount"

        @JvmStatic
        fun getTestDBAndEntityCount(): List<Arguments> {
            val recordCounts = listOf(50, 500)

            val testDBs = TestDB.enabledDialects() - TestDB.ALL_MARIADB_LIKE

            return testDBs.map { testDB ->
                recordCounts.map { entityCount ->
                    Arguments.of(testDB, entityCount)
                }
            }.flatten()
        }
    }

    @AfterEach
    fun afterEach() {
        Thread.sleep(10)
    }
}
