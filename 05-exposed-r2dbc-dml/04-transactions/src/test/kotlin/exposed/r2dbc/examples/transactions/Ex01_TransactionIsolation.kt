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
 * Exposed R2DBC에서 트랜잭션 격리 수준(Transaction Isolation Level)을 설정하는 예제.
 *
 * 주요 학습 내용:
 * - `suspendTransaction(transactionIsolation = ...)` 으로 트랜잭션별 격리 수준 지정
 * - `R2dbcDatabaseConfig { defaultR2dbcIsolationLevel = ... }` 으로 DB 기본값 설정
 * - `inTopLevelSuspendTransaction(transactionIsolation = ...)` 으로 최상위 트랜잭션 격리 수준 지정
 *
 * 지원 격리 수준:
 * - [IsolationLevel.READ_UNCOMMITTED]: 커밋되지 않은 데이터 읽기 허용 (Dirty Read 발생 가능)
 * - [IsolationLevel.READ_COMMITTED]: 커밋된 데이터만 읽기 (기본값, Non-repeatable Read 발생 가능)
 * - [IsolationLevel.REPEATABLE_READ]: 트랜잭션 내 반복 읽기 일관성 보장 (Phantom Read 발생 가능)
 * - [IsolationLevel.SERIALIZABLE]: 완전 직렬화 수준 (가장 엄격, 성능 저하 가능)
 *
 * 주의사항:
 * - PostgreSQL은 READ_UNCOMMITTED를 READ_COMMITTED로 처리합니다.
 * - MySQL 8.0.3+ 에서는 `@@tx_isolation` 변수가 제거되어 `REPEATABLE_READ`가 기본값입니다.
 * - `R2dbcDatabaseConfig`의 `defaultR2dbcIsolationLevel`은 전체 연결의 기본 격리 수준을 설정하며,
 *   개별 `suspendTransaction`에서 재정의할 수 있습니다.
 *
 * 모든 쿼리는 `withDb(testDB)` 블록 내에서 실행됩니다.
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
