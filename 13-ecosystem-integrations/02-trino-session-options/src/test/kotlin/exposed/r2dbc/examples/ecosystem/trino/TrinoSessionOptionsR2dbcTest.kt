package exposed.r2dbc.examples.ecosystem.trino

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TrinoSessionOptionsR2dbcTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `session profile builds explain request from local R2DBC SQL`(testDB: TestDB) = runTest {
        withTables(testDB, TrinoLineItems) {
            val querySql = renderTrinoCandidateSql()
            val session = TrinoSessionProfile(
                catalog = "memory",
                schema = "workshop",
                source = "exposed-r2dbc-workshop",
                clientTags = listOf("chapter-13", "local"),
                sessionProperties = mapOf("query_max_run_time" to "30s"),
            ).toQuerySession()

            val explainSql = renderTrinoExplainSql(querySql)

            querySql.uppercase() shouldContain "TRINO_LINE_ITEMS"
            explainSql.uppercase() shouldContain "EXPLAIN"
            explainSql shouldContain querySql
            session.catalogSchema shouldBeEqualTo "memory.workshop"
            session.headers["X-Trino-Source"] shouldBeEqualTo "exposed-r2dbc-workshop"
            session.headers["X-Trino-Client-Tags"] shouldBeEqualTo "chapter-13,local"
            session.headers["X-Trino-Session"] shouldBeEqualTo "query_max_run_time=30s"
        }
    }
}
