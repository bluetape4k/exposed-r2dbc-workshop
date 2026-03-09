package exposed.r2dbc.examples.custom.columns

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
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
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

/**
 * 클라이언트 측 기본값 생성 함수를 이용한 커스텀 ID 컬럼 예제.
 *
 * `bluetape4k-exposed` 확장이 제공하는 `timebasedGenerated()`, `snowflakeGenerated()`,
 * `ksuidGenerated()`, `ksuidMillisGenerated()` 함수를 사용하여
 * INSERT 시 자동으로 고유 ID 값을 생성하는 컬럼을 정의합니다.
 *
 * - [timebasedGenerated]: UUIDv1 기반의 시간 순서가 보장된 UUID 생성
 * - [snowflakeGenerated]: Snowflake 알고리즘 기반 BIGINT ID 생성
 * - [ksuidGenerated]: KSUID (K-Sortable Unique Identifier) 생성
 * - [ksuidMillisGenerated]: 밀리초 정밀도의 KSUID 생성
 */
class CustomClientDefaultFunctionsTest: AbstractR2dbcExposedTest() {

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
        val timebasedUuid: Column<UUID> = javaUUID("timebased_uuid").timebasedGenerated()

        // NOTE: MySQL 계열은 collate 를 지정하지 않으면 case insensitive 이므로, 중복이 발생할 수 있습니다.
        // NOTE: MySQL 을 테스트 시에는 varchar 컬럼에 collate=`utf8mb4_bin` 를 지정해주면 case sensitive 하여 unique 를 유지할 수 있습니다.
        val timebasedUuidString: Column<String> = varchar("timebased_uuid_string", 24).timebasedGenerated()
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
