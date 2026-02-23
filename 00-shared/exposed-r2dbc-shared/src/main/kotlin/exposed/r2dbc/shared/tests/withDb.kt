package exposed.r2dbc.shared.tests

import io.bluetape4k.utils.Runtimex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.transactions.nullableTransactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

internal val registeredOnShutdown = ConcurrentHashMap.newKeySet<TestDB>()
internal val testDbSemaphores = ConcurrentHashMap<TestDB, Semaphore>()

internal var currentTestDB by nullableTransactionScope<TestDB>()

private object CurrentTestDBInterceptor: StatementInterceptor {
    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is TestDB }
    }
}

private suspend fun acquireSemaphoreSuspending(testDB: TestDB) =
    withContext(Dispatchers.IO) {
        testDbSemaphores.computeIfAbsent(testDB) { Semaphore(1, true) }.acquire()
    }

/**
 * 지정한 [TestDB]에 대해 코루틴 트랜잭션 컨텍스트를 열고 [statement]를 실행합니다.
 *
 * 테스트 DB별로 세마포어를 사용해 동시 접근을 직렬화하고, 최초 실행 시 연결 초기화 및
 * JVM 종료 훅 등록을 수행합니다.
 *
 * @param testDB 테스트 대상 DB 정보
 * @param configure 데이터베이스 구성 커스터마이징
 * @param statement 트랜잭션 내부에서 실행할 테스트 코드
 */
suspend fun withDb(
    testDB: TestDB,
    configure: (DatabaseConfig.Builder.() -> Unit)? = {},
    statement: suspend R2dbcTransaction.(TestDB) -> Unit,
) {

    acquireSemaphoreSuspending(testDB)
    try {
        val unregistered = testDB !in registeredOnShutdown
        val newConfiguration = configure != null && !unregistered

        if (unregistered) {
            testDB.beforeConnection()
            Runtimex.addShutdownHook {
                testDB.afterTestFinished()
                registeredOnShutdown.remove(testDB)
            }
            registeredOnShutdown += testDB
            testDB.db = testDB.connect(configure ?: {})
        }

        val registeredDb = testDB.db!!
        if (newConfiguration) {
            testDB.db = testDB.connect(configure)
        }
        val database = testDB.db!!
        suspendTransaction(
            db = database,
            transactionIsolation = database.transactionManager.defaultIsolationLevel,
        ) {
            maxAttempts = 1
            registerInterceptor(CurrentTestDBInterceptor)
            currentTestDB = testDB
            statement(testDB)
        }
        // revert any new configuration to not be carried over to the next test in suite
        if (configure != null) {
            testDB.db = registeredDb
        }
    } finally {
        testDbSemaphores.getValue(testDB).release()
    }
}
