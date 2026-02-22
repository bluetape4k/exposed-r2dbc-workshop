package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Connection의 Transaction Isolation Level을 설정하는 방법에 대한 테스트 코드입니다.
 */
class Ex01_TransactionIsolation: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * `@@tx_isolation` is deprecated in MySQL 8.0.3 and removed in 8.0.4
     */
    private val transactionIsolationSupportDB =
        TestDB.ALL_MARIADB + TestDB.MYSQL_V5 + TestDB.POSTGRESQL

    private val isolations = listOf(
        // Connection.TRANSACTION_NONE,              // not supported
        IsolationLevel.READ_UNCOMMITTED,
        IsolationLevel.READ_COMMITTED,
        IsolationLevel.REPEATABLE_READ,
        IsolationLevel.SERIALIZABLE,
    )

    /**
     * transaction 함수에서 transactionIsolation을 설정하는 방법
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `what transaction isolation was applied`(testDB: TestDB) = runTest {
        withDb(testDB) {
            isolations.forEach { isolation ->
                log.debug { "db: ${testDB.name}, isolation: $isolation" }
                inTopLevelSuspendTransaction(transactionIsolation = isolation) {
                    maxAttempts = 1
                    this.transactionIsolation shouldBeEqualTo isolation
                }
            }
        }
    }

    /**
     * HikariCP 에서 Transaction Isolation 을 설정하는 방법
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transaction isolation with repeatable read`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in transactionIsolationSupportDB }

        val db = R2dbcDatabase.connect(
            url = testDB.connection(),
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.REPEATABLE_READ
            },
        )
        val manager: R2dbcTransactionManager = TransactionManager.managerFor(db)

        suspendTransaction(db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.REPEATABLE_READ
            this.transactionIsolation shouldBeEqualTo IsolationLevel.REPEATABLE_READ
        }

        suspendTransaction(transactionIsolation = IsolationLevel.READ_COMMITTED, db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.REPEATABLE_READ
            this.transactionIsolation shouldBeEqualTo IsolationLevel.READ_COMMITTED
        }

        suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.REPEATABLE_READ
            this.transactionIsolation shouldBeEqualTo IsolationLevel.SERIALIZABLE
        }

        suspendTransaction(db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.REPEATABLE_READ
            this.transactionIsolation shouldBeEqualTo IsolationLevel.REPEATABLE_READ
        }

        TransactionManager.closeAndUnregister(db)
    }

    /**
     * HikariCP 는 `TRANSACTION_REPEATABLE_READ` 로 설정,
     * Exposed 의 Database Config에서는  `TRANSACTION_READ_COMMITTED` 로 설정했을 때 Exposed 의 설정을 따른다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transaction isolation with read committed`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in transactionIsolationSupportDB }

        val db = R2dbcDatabase.connect(
            url = testDB.connection(),
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )

        val manager = TransactionManager.managerFor(db)

        suspendTransaction(db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.READ_COMMITTED
        }

        suspendTransaction(transactionIsolation = IsolationLevel.REPEATABLE_READ, db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.READ_COMMITTED
            this.transactionIsolation shouldBeEqualTo IsolationLevel.REPEATABLE_READ
        }

        suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.READ_COMMITTED
            this.transactionIsolation shouldBeEqualTo IsolationLevel.SERIALIZABLE
        }

        suspendTransaction(db = db) {
            manager.defaultIsolationLevel shouldBeEqualTo IsolationLevel.READ_COMMITTED
            this.transactionIsolation shouldBeEqualTo IsolationLevel.READ_COMMITTED
        }

        TransactionManager.closeAndUnregister(db)
    }
}
