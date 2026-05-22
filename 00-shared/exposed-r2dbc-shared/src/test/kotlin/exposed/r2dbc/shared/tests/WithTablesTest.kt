package exposed.r2dbc.shared.tests

import kotlinx.coroutines.test.runTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

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

    @Test
    fun `withTables 는 statement cancellation 을 그대로 전파한다`() = runTest {
        val failure = assertFailsWith<CancellationException> {
            withTables(TestDB.H2, FailureTable) {
                throw CancellationException("cancel statement")
            }
        }

        failure.shouldBeInstanceOf<CancellationException>()
        failure.suppressed.size shouldBeEqualTo 0
    }

    @Test
    fun `cleanup 실패는 statement cancellation 에 suppressed 로 붙지 않는다`() {
        val statementFailure = CancellationException("cancel statement")

        suppressCleanupFailures(
            statementFailure = statementFailure,
            cleanupFailure = IllegalStateException("cleanup failed"),
            recoveryFailure = IllegalStateException("recovery failed"),
        )

        statementFailure.suppressed.size shouldBeEqualTo 0
    }

    @Test
    fun `cleanup 실패는 일반 statement 실패에는 suppressed 로 남긴다`() {
        val statementFailure = IllegalStateException("statement failed")

        suppressCleanupFailures(
            statementFailure = statementFailure,
            cleanupFailure = IllegalStateException("cleanup failed"),
            recoveryFailure = IllegalStateException("recovery failed"),
        )

        statementFailure.suppressed.size shouldBeEqualTo 2
    }
}
