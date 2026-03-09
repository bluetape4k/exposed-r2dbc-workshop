package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.dml.DMLTestData
import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.coroutines.CoroutineContext

/**
 * 중첩된 작업에 대해 savepoint를 사용하여 롤백할 수 있도록 합니다.
 */
suspend fun <T> runWithSavepoint(
    name: String = "savepoint_${Base58.randomString(8)}",
    rollback: Boolean = false,
    block: suspend R2dbcTransaction.() -> T,
): T? = withContext(Dispatchers.IO) {
    val tx = TransactionManager.currentOrNull() ?: error("No active transaction")

    val connection = tx.connection()
    val savepoint = connection.setSavepoint(name)

    try {
        block(tx)
    } catch (e: Exception) {
        connection.rollback(savepoint)
        null
    } finally {
        if (rollback) {
            connection.rollback(savepoint)
        }
        connection.releaseSavepoint(savepoint)
    }
}

suspend fun <T> runWithSavepointOrNewTransaction(
    name: String = "savepoint_${Base58.randomString(8)}",
    rollback: Boolean = false,
    context: CoroutineContext? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    block: suspend R2dbcTransaction.() -> T,
): T? {
    val currentTx = TransactionManager.currentOrNull()

    return if (currentTx != null) {
        runWithSavepoint(name, rollback, block)
    } else {
        suspendTransaction(transactionIsolation = transactionIsolation!!, readOnly = readOnly, db = db) {
            try {
                block(this)
            } catch (e: Exception) {
                rollback()
                null
            } finally {
                if (rollback) {
                    rollback()
                }
            }
        }
    }
}


/**
 * Exposed R2DBC에서 코루틴 환경의 중첩 트랜잭션(Savepoint 기반)을 사용하는 예제.
 *
 * 주요 학습 내용:
 * - `suspendTransaction { suspendTransaction { } }` 코루틴 중첩 트랜잭션 구성
 * - Savepoint를 통한 내부 트랜잭션 부분 롤백
 * - `rollback()` / `commit()` 수동 제어
 * - 외부 트랜잭션 커밋 후에도 내부 롤백 데이터가 영향받지 않음을 검증
 * - 코루틴 컨텍스트에서 자동 커밋(auto-commit) 모드와 Savepoint의 관계
 *
 * 주의사항:
 * - 코루틴 환경에서 중첩 트랜잭션은 R2DBC Savepoint API를 사용합니다.
 * - 자동 커밋 모드에서 Savepoint 동작은 DB마다 다를 수 있습니다.
 * - `withContext(Dispatchers.IO)` 와 조합 시 코루틴 컨텍스트 전환에 주의가 필요합니다.
 *
 * 모든 쿼리는 `withDb(testDB)` 블록 내에서 실행됩니다.
 */
class Ex05_NestedTransactions_Coroutines: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    private val db by lazy {
        R2dbcDatabase.connect(
            url = "r2dbc:h2:mem:///db1;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                useNestedTransactions = true
                defaultMaxAttempts = 1
            }
        )
    }

    val cities = DMLTestData.Cities

    private suspend fun cityCounts(): Int =
        cities.selectAll().count().toInt()

    private suspend fun cityNames(): List<String> =
        cities.selectAll().map { it[cities.name] }.toList()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `코루틴에서 중첩 트랜잭션 사용하기`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, cities, configure = { useNestedTransactions = true }) {
            // 외부 트랜잭션
            cities.selectAll().toList().shouldBeEmpty()
            cities.insert { it[name] = "city1" }
            cityCounts() shouldBeEqualTo 1
            cityNames() shouldBeEqualTo listOf("city1")

            // 중첩 1 (종료되면 rollback 함)
            runWithSavepointOrNewTransaction("savepoint1", rollback = true) {
                cities.insert { it[name] = "city2" }
                cityNames() shouldBeEqualTo listOf("city1", "city2")

                // 중첩 2
                runWithSavepointOrNewTransaction("savepoint2") {
                    cities.insert { it[name] = "city3" }
                    cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")
                }
                // 중첩 2의 작업은 commit 되었으므로, 현재 트랜잭션에 반영된다.
                cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")

                // 중첩 1의 작업은 롤백되었으므로, 현 트랜잭션에 반영되지 않는다.
            }

            // 중첩 1의 작업은 롤백되었으므로, 현재 트랜잭션 결과만 반영된다.
            cityNames() shouldBeEqualTo listOf("city1")
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `코루틴에서 중첩 트랜잭션 실패 후 외부 트랜잭션으로 복귀한다`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, cities) {
            TransactionManager.currentOrNull().shouldNotBeNull()

            try {
                runWithSavepointOrNewTransaction<Unit> {
                    maxAttempts = 1
                    error("Should be rethrow")
                }
            } catch (e: Exception) {
                e shouldBeInstanceOf IllegalStateException::class
            }

            TransactionManager.currentOrNull().shouldNotBeNull()
        }
    }

    private suspend fun assertSingleRecordInNewTransactionAndReset() =
        suspendTransaction(db = db) {
            val result = cities.selectAll().single()[cities.name]
            result shouldBeEqualTo "City A"
            cities.deleteAll()
        }
}
