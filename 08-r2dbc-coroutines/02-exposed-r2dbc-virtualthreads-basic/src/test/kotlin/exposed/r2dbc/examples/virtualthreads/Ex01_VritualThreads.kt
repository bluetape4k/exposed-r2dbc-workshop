package exposed.r2dbc.examples.virtualthreads

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.collections.intRangeOf
import io.bluetape4k.concurrent.virtualthread.newVT
import io.bluetape4k.exposed.r2dbc.virtualThreadTransaction
import io.bluetape4k.junit5.coroutines.runSuspendVT
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
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
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JDK 21 Virtual Threads와 Exposed R2DBC를 조합하는 예제 테스트.
 *
 * ## Virtual Threads란?
 * JDK 21에서 정식 도입된 경량 스레드(Project Loom). OS 스레드와 1:1 매핑되지 않으므로
 * 수천~수백만 개의 동시 작업을 적은 메모리로 처리할 수 있습니다.
 *
 * ## Exposed R2DBC + Virtual Threads 조합
 * - [Dispatchers.newVT]: `Dispatchers.IO`와 유사하지만 Virtual Threads 기반 디스패처
 * - [virtualThreadTransaction]: Virtual Threads 위에서 새 R2DBC 트랜잭션을 생성
 * - [inTopLevelSuspendTransaction]: 최상위 suspend 트랜잭션을 Virtual Threads 컨텍스트에서 실행
 * - [runSuspendVT]: JUnit 5 테스트를 Virtual Threads 코루틴으로 실행하는 헬퍼
 *
 * ## 주요 패턴
 * 1. 순차 작업: `virtualThreadTransaction { ... }` 으로 기존 트랜잭션에서 분기
 * 2. 병렬 작업: `CoroutineScope(Dispatchers.newVT).async { ... }` 로 다수 비동기 실행
 * 3. 조건 조회: Virtual Threads 위에서도 Exposed DSL 쿼리를 동일하게 사용
 *
 * @see virtualThreadTransaction
 * @see inTopLevelSuspendTransaction
 */
// NOTE: 파일명 `Ex01_VritualThreads.kt`는 `Ex01_VirtualThreads.kt`의 오타입니다.
//       `git mv`로 파일명을 수정해야 하지만, 클래스명은 아래에서 수정되었습니다.
@EnabledOnJre(JRE.JAVA_21)
class Ex01_VirtualThreads: AbstractR2dbcExposedTest() {

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

    data class VRecord(val id: Int, val name: String?): Serializable

    fun ResultRow.toVRecord(): VRecord = VRecord(this[VTester.id].value, this[VTester.name])

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
    fun `virtual threads 를 이용하여 순차 작업 수행하기`(testDB: TestDB) = runSuspendVT {
        withTables(testDB, VTester) {
            val id = VTester.insertAndGetId { }

            // 내부적으로 새로운 트랜잭션을 생성하여 비동기 작업을 수행한다
            getTesterById(id.value)!![VTester.id].value shouldBeEqualTo id.value

            val result = getTesterById(1)!![VTester.id].value
            result shouldBeEqualTo 1
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `중첩된 virtual thread 용 트랜잭션을 async로 실행`(testDB: TestDB) = runSuspendVT {
        Assumptions.assumeTrue { testDB !in TestDB.ALL_MARIADB_LIKE }

        withTables(testDB, VTester) {
            val recordCount = 5
            delay(10)

            // val vtScope = CoroutineScope(Dispatchers.newVT)
            List(recordCount) { index ->
                coroutineScope {
                    async {
                        suspendTransaction {
                            maxAttempts = 10
                            log.debug { "Task[$index] inserting ..." }
                            // insert 를 수행하는 트랜잭션을 생성한다
                            VTester.insert { }
                        }
                    }
                }
            }.awaitAll()

            // 중첩 트랜잭션에서 virtual threads 를 이용하여 동시에 여러 작업을 수행한다.
            // val vtScope2 = CoroutineScope(Dispatchers.newVT)
            val rows = List(recordCount) { index ->
                coroutineScope {
                    async {
                        inTopLevelSuspendTransaction {
                            maxAttempts = 10
                            log.debug { "Task[$index] selecting ..." }
                            VTester.selectAll().map { it.toVRecord() }.toList()
                        }
                    }
                }
            }.awaitAll().flatten()

            // recordCount 개의 행을 가지는 `ResultRow` 를 recordCount 수만큼 가지는 List
            rows.shouldNotBeEmpty()
            rows shouldHaveSize recordCount * recordCount
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `다수의 비동기 작업을 수행 후 대기`(testDB: TestDB) = runSuspendVT {
        withTables(testDB, VTester) {
            val recordCount = 10
            val results = CopyOnWriteArrayList<Int>()

            val vtScope = CoroutineScope(Dispatchers.newVT)
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
            results.count() shouldBeEqualTo recordCount
            VTester.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `virtual threads 환경에서 조건 조회`(testDB: TestDB) = runSuspendVT {
        withTables(testDB, VTester) {
            listOf("alpha", "beta", "gamma").forEach { name ->
                VTester.insert { it[VTester.name] = name }
            }

            val row = VTester.selectAll()
                .where { VTester.name eq "beta" }
                .singleOrNull()

            row?.getOrNull(VTester.name) shouldBeEqualTo "beta"
        }
    }
}
