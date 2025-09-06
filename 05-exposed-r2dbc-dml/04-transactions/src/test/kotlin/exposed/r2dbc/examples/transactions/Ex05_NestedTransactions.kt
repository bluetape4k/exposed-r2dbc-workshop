package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.dml.DMLTestData
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class Ex05_NestedTransactions: R2dbcExposedTestBase() {

    companion object: KLogging()

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

    private suspend fun cityCounts(): Int = cities.selectAll().count().toInt()

    private suspend fun cityNames(): List<String> = cities.selectAll().map { it[cities.name] }.toList()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `중첩 트랜잭션 실행`(testDB: TestDB) = runTest {
        // 외부 트랜잭션
        withTables(testDB, cities, configure = { useNestedTransactions = true }) {
            cities.selectAll().toList().shouldBeEmpty()
            cities.insert { it[name] = "city1" }
            cityCounts() shouldBeEqualTo 1
            cityNames() shouldBeEqualTo listOf("city1")

            // 중첩 1
            suspendTransaction {
                cities.insert {
                    it[name] = "city2"
                }
                cityNames() shouldBeEqualTo listOf("city1", "city2")

                // 중첩 2
                suspendTransaction {
                    cities.insert { it[name] = "city3" }
                    cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")
                }
                // 중첩 2가 성공했으므로, 중접 1의 결과는 모두 반영되어야 한다.
                cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")

                // 중첩1을 강제 롤백한다
                rollback()
            }

            // 중첩1과 내부 트랜잭션의 작업은 모두 취소되고, 현재 트랜잭션 결과만 반영된다.
            cityNames() shouldBeEqualTo listOf("city1")
        }
    }

//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `outer transaction restored after nested transaction failed`(testDB: TestDB) = runTest {
//        runBlocking {
//            withTables(testDB, cities) {
//                TransactionManager.currentOrNull().shouldNotBeNull()
//
//                try {
//                    suspendTransaction(transactionIsolation = this.transactionIsolation) {
//                        maxAttempts = 1
//                        throw kotlin.IllegalStateException("Should be rethrow")
//                    }
//                } catch (e: Exception) {
//                    e shouldBeInstanceOf IllegalStateException::class
//                }
//
//                TransactionManager.currentOrNull().shouldNotBeNull()
//            }
//        }
//    }

//    @Test
//    fun `nested transaction not committed after database failure`() = runTest {
//        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }
//
//        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"
//
//        suspendTransaction(db = db) {
//            SchemaUtils.create(cities)
//        }
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            cities.insert { it[name] = "City A" }
//            cityCounts() shouldBeEqualTo 1
//
//            try {
//                suspendTransaction(transactionIsolation = db.transactionManager.defaultIsolationLevel, db = db) {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }           // 이 작업는 롤백됩니다.
//                    exec("${fakeSQLString}();")
//                }
//                fail("Should not reach here")
//            } catch (cause: SQLException) {
//                cause.toString() shouldContain fakeSQLString
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            cities.insert { it[cities.name] = "City A" }
//            cityCounts() shouldBeEqualTo 1
//
//            try {
//                suspendTransaction(db = db) {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[cities.name] = "City B" }      // 이 작업는 롤백됩니다.
//                    exec("SELECT * FROM non_existent_table")
//                }
//                fail("Should not reach here")
//            } catch (cause: SQLException) {
//                cause.toString() shouldContain "non_existent_table"
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            SchemaUtils.drop(cities)
//        }
//    }

//    @Test
//    fun `nested transaction not committed after exception`() = runTest {
//        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }
//
//        val exceptionMessage = "Failure!"
//
//        suspendTransaction(db = db) {
//            SchemaUtils.create(cities)
//        }
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//            cities.insert { it[name] = "City A" }
//            cities.selectAll().count().toInt() shouldBeEqualTo 1
//
//            try {
//                suspendTransaction(transactionIsolation = db.transactionManager.defaultIsolationLevel, db = db) {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }       // 이 코드는 실행되지 않는다.
//                    error(exceptionMessage)
//                }
//            } catch (cause: IllegalStateException) {
//                cause.toString() shouldContain exceptionMessage
//                currentCoroutineContext().cancel()
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//            cities.insert { it[name] = "City A" }
//            cities.selectAll().count().toInt() shouldBeEqualTo 1
//
//            try {
//                suspendTransaction(db = db) {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }       // 이 코드는 실행되지 않는다.
//                    error(exceptionMessage)
//                }
//            } catch (cause: IllegalStateException) {
//                cause.toString() shouldContain exceptionMessage
//            }
//        }
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            SchemaUtils.drop(cities)
//        }
//
//    }

    private suspend fun assertSingleRecordInNewTransactionAndReset() =
        suspendTransaction(db = db) {
            val result = cities.selectAll().single()[cities.name]
            result shouldBeEqualTo "City A"
            cities.deleteAll()
        }
}
