package exposed.r2dbc.examples.virtualthreads

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.collections.intRangeOf
import io.bluetape4k.exposed.r2dbc.virtualThreadTransaction
import io.bluetape4k.exposed.r2dbc.virtualThreadTransactionAsync
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

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
    fun `virtual threads 를 이용하여 순차 작업 수행하기`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, VTester) {

            virtualThreadTransaction {
                val id = VTester.insertAndGetId { }
                commit()

                // 내부적으로 새로운 트랜잭션을 생성하여 비동기 작업을 수행한다
                getTesterById(id.value)!![VTester.id].value shouldBeEqualTo id.value
            }

            val result = getTesterById(1)!![VTester.id].value
            result shouldBeEqualTo 1
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `중첩된 virtual thread 용 트랜잭션을 async로 실행`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, VTester) {
            val recordCount = 10

            virtualThreadTransaction {
                List(recordCount) { index ->
                    virtualThreadTransactionAsync {
                        log.debug { "Task[$index] inserting ..." }
                        // insert 를 수행하는 트랜잭션을 생성한다
                        VTester.insert { }
                    }
                }.awaitAll()
                commit()

                // 중첩 트랜잭션에서 virtual threads 를 이용하여 동시에 여러 작업을 수행한다.
                val futures: List<Deferred<List<ResultRow>>> = List(recordCount) { index ->
                    virtualThreadTransactionAsync {
                        log.debug { "Task[$index] selecting ..." }
                        VTester.selectAll().toList()
                    }
                }
                // recordCount 개의 행을 가지는 `ResultRow` 를 recordCount 수만큼 가지는 List 
                val rows: List<ResultRow> = futures.awaitAll().flatten()
                rows.shouldNotBeEmpty()
            }

            val count = virtualThreadTransaction {
                VTester.selectAll().count()
            }
            count shouldBeEqualTo recordCount.toLong()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `다수의 비동기 작업을 수행 후 대기`(testDB: TestDB) = runSuspendIO {
        withTables(testDB, VTester) {
            val recordCount = 10

            val results: List<Int> = List(recordCount) { index ->
                virtualThreadTransactionAsync {
                    maxAttempts = 5
                    log.debug { "Task[$index] inserting ..." }
                    // insert 를 수행하는 트랜잭션을 생성한다
                    VTester.insert { }
                    index + 1
                }
            }.awaitAll()

            results shouldBeEqualTo intRangeOf(1, recordCount)

            VTester.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }

//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `virtual threads 용 트랜잭션과 일반 transaction 홉용하기`(testDB: TestDB) = runSuspendIO {
//        withTables(testDB, VTester) {
//            // val database = this.db // Transaction 에서 db 를 가져온다
//            var virtualThreadOk = true
//            var platformThreadOk = true
//
//            val row = virtualThreadTransaction {
//                try {
//                    VTester.selectAll().toList()
//                } catch (e: Throwable) {
//                    virtualThreadOk = false
//                    null
//                }
//            }
//
//            val row2 = transaction {
//                try {
//                    VTester.selectAll().toList()
//                } catch (e: Throwable) {
//                    platformThreadOk = false
//                    null
//                }
//            }
//
//            virtualThreadOk.shouldBeTrue()
//            platformThreadOk.shouldBeTrue()
//        }
//    }

//    class TesterEntity(id: EntityID<Int>): IntEntity(id) {
//        companion object: IntEntityClass<TesterEntity>(VTester)
//
//        override fun equals(other: Any?): Boolean = idEquals(other)
//        override fun hashCode(): Int = idHashCode()
//        override fun toString(): String = "TesterEntity(id=$id)"
//    }

//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `virtual thread 트랜잭션에서 예외 처리`(testDB: TestDB) {
//        withTables(testDB, VTester) {
//            val database = this.db
//            val outerConn = this.connection
//            val id = TesterEntity.new { }.id
//            commit()
//
//            // 여기서 중복된 Id 로 엔티티를 생성하려고 해서 중복 Id 예외가 발생한다
//            var innerConn: ExposedConnection<*>? = null
//            assertFailsWith<ExecutionException> {
//                newVirtualThreadTransaction {
//                    maxAttempts = 1
//
//                    innerConn = this.connection
//                    innerConn.isClosed.shouldBeFalse()
//                    innerConn shouldNotBeEqualTo outerConn
//
//                    // 중복된 ID를 삽입하려고 하면 예외가 발생한다.
//                    TesterEntity.new(id.value) { }
//                }
//            }.cause shouldBeInstanceOf ExposedSQLException::class
//
//            // 내부 트랜잭션은 예외가 발생하고, 해당 connection은 닫힌다.
//            innerConn.shouldNotBeNull().isClosed.shouldBeTrue()
//
//            // 외부 트랜잭션은 예외가 발생하지 않고, 기존 데이터에 영향이 없다.
//            TesterEntity.count() shouldBeEqualTo 1L
//        }
//    }
}
