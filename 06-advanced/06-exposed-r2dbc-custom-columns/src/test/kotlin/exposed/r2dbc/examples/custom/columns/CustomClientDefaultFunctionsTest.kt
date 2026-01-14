package exposed.r2dbc.examples.custom.columns

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.ksuidGenerated
import io.bluetape4k.exposed.core.ksuidMillisGenerated
import io.bluetape4k.exposed.core.snowflakeGenerated
import io.bluetape4k.exposed.core.timebasedGenerated
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

class CustomClientDefaultFunctionsTest: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS clientgenerated (
     *      id SERIAL PRIMARY KEY,
     *      timebased_uuid uuid NOT NULL,
     *      timebased_uuid_string VARCHAR(36) NOT NULL,
     *      snowflake BIGINT NOT NULL,
     *      ksuid VARCHAR(27) NOT NULL,
     *      ksuid_millis VARCHAR(27) NOT NULL
     * );
     * ```
     */
    object ClientGenerated: IntIdTable() {
        val timebasedUuid: Column<UUID> = uuid("timebased_uuid").timebasedGenerated()
        val timebasedUuidString: Column<String> = varchar("timebased_uuid_string", 36).timebasedGenerated()
        val snowflake: Column<Long> = long("snowflake").snowflakeGenerated()
        val ksuid: Column<String> = varchar("ksuid", 27).ksuidGenerated()
        val ksuidMillis: Column<String> = varchar("ksuid_millis", 27).ksuidMillisGenerated()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL - 클라이언트에서 기본값으로 생성하는 함수`(testDB: TestDB) = runTest {
        withTables(testDB, ClientGenerated) {
            val entityCount = 100
            val values = List(entityCount) { it + 1 }
            ClientGenerated.batchInsert(values) {}

            val rows = ClientGenerated.selectAll().toList()

            rows.map { it[ClientGenerated.timebasedUuid] }.distinct() shouldHaveSize entityCount
            rows.map { it[ClientGenerated.timebasedUuidString] }.distinct() shouldHaveSize entityCount
            rows.map { it[ClientGenerated.snowflake] }.distinct() shouldHaveSize entityCount
            rows.map { it[ClientGenerated.ksuid] }.distinct() shouldHaveSize entityCount
            rows.map { it[ClientGenerated.ksuidMillis] }.distinct() shouldHaveSize entityCount
        }
    }
}
