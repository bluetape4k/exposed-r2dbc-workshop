package exposed.r2dbc.examples.virtualthreads

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.collections.intRangeOf
import io.bluetape4k.concurrent.virtualthread.VT
import io.bluetape4k.exposed.r2dbc.virtualThreadTransaction
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CopyOnWriteArrayList

class Ex01_VritualThreads: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS virtualthreads_tester (id SERIAL PRIMARY KEY)
     * ```
     */
    object VTester: IntIdTable("virtualthreads_table") {
        val name = varchar("name", 50).nullable()
    }

    /**
     * ```sql
     * CREATE TABLE IF NOT EXISTS virtualthreads_tester_unique (id INT PRIMARY KEY);
     * ALTER TABLE virtualthreads_tester_unique ADD CONSTRAINT virtualthreads_tester_unique_id_unique UNIQUE (id);
     * ```
     */
    object VTesterUnique: Table("virtualthreads_table_unique") {
        val id = integer("id").uniqueIndex()
        override val primaryKey = PrimaryKey(id)
    }

    @Suppress("UnusedReceiverParameter")
    suspend fun R2dbcTransaction.getTesterById(id: Int): ResultRow? =
        virtualThreadTransaction {
            VTester.selectAll()
                .where { VTester.id eq id }
                .singleOrNull()
        }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `virtual threads 를 이용하여 순차 작업 수행하기`(testDB: TestDB) = runTest {
        withTables(testDB, VTester) {
            val id = VTester.insertAndGetId { }
            commit()

            // 내부적으로 새로운 트랜잭션을 생성하여 비동기 작업을 수행한다
            getTesterById(id.value)!![VTester.id].value shouldBeEqualTo id.value

            val result = getTesterById(1)!![VTester.id].value
            result shouldBeEqualTo 1
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `중첩된 virtual thread 용 트랜잭션을 async로 실행`(testDB: TestDB) = runTest {
        withTables(testDB, VTester) {
            val recordCount = 10

            val vtScope = CoroutineScope(Dispatchers.VT)
            List(recordCount) { index ->
                vtScope.async {
                    suspendTransaction {
                        log.debug { "Task[$index] inserting ..." }
                        // insert 를 수행하는 트랜잭션을 생성한다
                        VTester.insert { }
                    }
                }
            }.awaitAll()

            // 중첩 트랜잭션에서 virtual threads 를 이용하여 동시에 여러 작업을 수행한다.
            val rows = List(recordCount) { index ->
                vtScope.async {
                    suspendTransaction {
                        log.debug { "Task[$index] selecting ..." }
                        VTester.selectAll().toList(CopyOnWriteArrayList())
                    }
                }
            }.awaitAll().flatten()

            // recordCount 개의 행을 가지는 `ResultRow` 를 recordCount 수만큼 가지는 List
            rows.shouldNotBeEmpty()

            val count = VTester.selectAll().count()
            count shouldBeEqualTo recordCount.toLong()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `다수의 비동기 작업을 수행 후 대기`(testDB: TestDB) = runTest {
        withTables(testDB, VTester) {
            val recordCount = 10
            val results = CopyOnWriteArrayList<Int>()

            val vtScope = CoroutineScope(Dispatchers.VT)

            List(recordCount) { index ->
                vtScope.launch {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = db.transactionManager.defaultIsolationLevel!!,
                        db = db
                    ) {
                        maxAttempts = 5
                        log.debug { "Task[$index] inserting ..." }
                        // insert 를 수행하는 트랜잭션을 생성한다
                        VTester.insert { }
                        results.add(index + 1)
                    }
                }
            }.joinAll()

            results.sorted() shouldBeEqualTo intRangeOf(1, recordCount)
            VTester.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }
}
