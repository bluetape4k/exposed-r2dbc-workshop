package exposed.r2dbc.examples.ecosystem.bigquery

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class BigQueryDryRunR2dbcTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `dry run request uses rendered R2DBC SQL without external credentials`(testDB: TestDB) = runTest {
        withTables(testDB, BigQueryDryRunSource) {
            val sql = renderBigQueryCandidateSql()
            val request = BigQueryDryRunProfile(
                projectId = "bluetape4k",
                dataset = "analytics",
                location = "asia-northeast3",
                labels = mapOf("chapter" to "13", "driver" to "r2dbc"),
            ).toDryRunRequest(sql)

            request.sql.uppercase() shouldContain "SELECT"
            request.sql.uppercase() shouldContain "BIGQUERY_DRY_RUN_SOURCE"
            request.defaultDataset shouldBeEqualTo "bluetape4k.analytics"
            request.location shouldBeEqualTo "asia-northeast3"
            request.labels["driver"] shouldBeEqualTo "r2dbc"
            request.dryRun.shouldBeTrue()
            request.useLegacySql shouldBeEqualTo false
        }
    }
}
