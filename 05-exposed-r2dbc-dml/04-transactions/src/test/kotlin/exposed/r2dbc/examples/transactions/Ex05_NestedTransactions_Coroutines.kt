package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.dml.DMLTestData
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.codec.Base58
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
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


class Ex05_NestedTransactions_Coroutines: R2dbcExposedTestBase() {

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

//    @Test
//    fun `DB 예외 시 중첩 트랜잭션은 commit 되지 않습니다`() = runSuspendIO {
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
//                kotlin.test.fail("Should not reach here")
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
//            cities.insert { it[name] = "City A" }
//            cityCounts() shouldBeEqualTo 1
//
//            suspendTransaction(db = db) {
//                try {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }           // 이 작업는 롤백됩니다.
//                    exec("SELECT * FROM non_existent_table")
//                    fail("Should not reach here")
//                } catch (cause: SQLException) {
//                    try {
//                        rollback()
//                    } catch (e: Exception) {
//                        exposedLogger.warn(
//                            "Transaction rollback failed: ${cause.message}. Statement: $currentStatement",
//                            cause
//                        )
//                    }
//                    cause.toString() shouldContain "non_existent_table"
//                }
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
//    fun `일반 예외 시 중첩 트랜잭션은 commit 되지 않습니다`() = runSuspendIO {
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
//                    error(exceptionMessage)
//                }
//            } catch (cause: Exception) {
//                cause.toString() shouldContain exceptionMessage
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            cities.insert { it[name] = "City A" }
//            cityCounts() shouldBeEqualTo 1
//
//            suspendTransaction(db = db) {
//                try {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }           // 이 작업는 롤백됩니다.
//                    error(exceptionMessage)
//                } catch (cause: Exception) {
//                    rollback()
//                    cause.toString() shouldContain exceptionMessage
//                }
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
//    fun `외부는 코루틴용, 내부는 일반 transaction 인 경우에도 rollback 된다`() = runSuspendIO {
//        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }
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
//            transaction(db = db) {
//                try {
//                    val innerTxId = this.id
//                    innerTxId shouldNotBeEqualTo outerTxId
//
//                    cities.insert { it[name] = "City B" }           // 이 작업는 롤백됩니다.
//                    exec("SELECT * FROM non_existent_table")
//                } catch (cause: SQLException) {
//                    rollback()
//                }
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
//    fun `외부는 일반 트랜잭션, 내부는 코루틴용 트랜잭션`() = runSuspendIO {
//        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }
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
//            runBlocking {
//                suspendTransaction(db = db) {
//                    try {
//                        val innerTxId = this.id
//                        innerTxId shouldNotBeEqualTo outerTxId
//
//                        cities.insert { it[name] = "City B" }           // 이 작업는 롤백됩니다.
//                        exec("SELECT * FROM non_existent_table")
//                    } catch (cause: SQLException) {
//                        rollback()
//                    }
//                }
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            SchemaUtils.drop(cities)
//        }
//    }

    private suspend fun assertSingleRecordInNewTransactionAndReset() =
        suspendTransaction(db = db) {
            val result = cities.selectAll().single()[cities.name]
            result shouldBeEqualTo "City A"
            cities.deleteAll()
        }
}
