package exposed.r2dbc.examples.coroutines

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 코루틴 + Flow 조합으로 Exposed R2DBC 쿼리 결과를 처리하는 예제 테스트입니다.
 *
 * 단순 조회 스트리밍과 비동기 병렬 INSERT 후 검증 패턴을 함께 다룹니다.
 */
class Ex02_CoroutinesFlow: AbstractR2dbcExposedTest() {

    /**
     * 코루틴 예제에서 사용하는 단순 숫자 데이터 테이블입니다.
     */
    object CoroutineFlowTester: IntIdTable("coroutine_flow_tester") {
        val seq = integer("seq").uniqueIndex()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `Flow로 select 결과를 순서대로 수집한다`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, CoroutineFlowTester) {
            (1..5).forEach { value ->
                CoroutineFlowTester.insert {
                    it[seq] = value
                }
            }

            val values = CoroutineFlowTester.selectAll()
                .orderBy(CoroutineFlowTester.seq)
                .map { it[CoroutineFlowTester.seq] }
                .toList()

            values shouldBeEqualTo listOf(1, 2, 3, 4, 5)
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `빈 테이블 Flow 조회는 빈 리스트를 반환한다`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, CoroutineFlowTester) {
            val values = CoroutineFlowTester.selectAll()
                .orderBy(CoroutineFlowTester.seq)
                .map { it[CoroutineFlowTester.seq] }
                .toList()

            values.shouldBeEmpty()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `여러 inTopLevelSuspendTransaction 작업을 병렬로 실행한다`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, CoroutineFlowTester) {
            val expected = (1..8).toList()
            val ioScope = CoroutineScope(Dispatchers.IO)

            expected.map { value ->
                ioScope.async {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = db.transactionManager.defaultIsolationLevel!!,
                        db = db,
                    ) {
                        maxAttempts = 5
                        CoroutineFlowTester.insert { it[seq] = value }
                    }
                }
            }.awaitAll()

            val insertedValues = CoroutineFlowTester.selectAll()
                .orderBy(CoroutineFlowTester.seq)
                .map { it[CoroutineFlowTester.seq] }
                .toList()

            insertedValues shouldBeEqualTo expected
        }
    }
}
