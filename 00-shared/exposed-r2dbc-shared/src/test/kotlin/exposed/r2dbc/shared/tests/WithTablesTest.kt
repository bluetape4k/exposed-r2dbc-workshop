package exposed.r2dbc.shared.tests

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * [withTables] 유틸리티의 예외 전파와 정리 동작을 검증한다.
 */
class WithTablesTest: AbstractR2dbcExposedTest() {

    private object FailureTable: Table("with_tables_failure_test") {
        val name = varchar("name", 32)
    }

    @Test
    fun `withTables 는 statement 실패를 숨기지 않고 테이블 정리를 수행한다`() = runTest {
        assertFailsWith<ExposedR2dbcException> {
            withTables(TestDB.H2, FailureTable) {
                exec("SELECT * FROM definitely_missing_table")
            }
        }

        withTables(TestDB.H2, FailureTable) {
            FailureTable.selectAll().count() shouldBeEqualTo 0L
        }
    }
}
