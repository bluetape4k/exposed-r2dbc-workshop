package exposed.r2dbc.shared.tests

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotNull
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import kotlin.coroutines.cancellation.CancellationException

private object WithTablesLogger : KLogging()

internal fun suppressCleanupFailures(
    statementFailure: Throwable,
    cleanupFailure: Throwable,
    recoveryFailure: Throwable?,
) {
    if (statementFailure is CancellationException) {
        WithTablesLogger.log.warn(cleanupFailure) { "Failed to drop tables after cancellation" }
        recoveryFailure?.let {
            WithTablesLogger.log.warn(it) { "Failed to recover table cleanup after cancellation" }
        }
        return
    }

    statementFailure.addSuppressed(cleanupFailure)
    recoveryFailure?.let(statementFailure::addSuppressed)
}

/**
 * 테스트 실행 전/후 테이블을 생성/정리하면서 [statement]를 수행합니다.
 *
 * 실행 중 오류가 발생하더라도 `finally`에서 테이블 정리를 재시도하여
 * 다음 테스트에 영향을 주지 않도록 보장합니다.
 *
 * @param testDB 테스트 대상 DB 정보
 * @param tables 테스트에 사용할 테이블 목록
 * @param configure 데이터베이스 구성 커스터마이징
 * @param statement 테이블이 준비된 트랜잭션에서 실행할 테스트 코드
 */
suspend fun withTables(
    testDB: TestDB,
    vararg tables: Table,
    configure: (DatabaseConfig.Builder.() -> Unit)? = null,
    statement: suspend R2dbcTransaction.(TestDB) -> Unit,
) {
    withDb(testDB, configure = configure) {
        try {
            SchemaUtils.drop(*tables)
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Throwable) {
            // Ignore stale table cleanup failures before the test schema is created.
        }
        SchemaUtils.create(*tables)
        commit()

        var statementFailure: Throwable? = null
        try {
            statement(testDB)
            commit()
        } catch (ex: CancellationException) {
            statementFailure = ex
            throw ex
        } catch (ex: Throwable) {
            statementFailure = ex
            throw ex
        } finally {
            try {
                SchemaUtils.drop(*tables)
                commit()
            } catch (ex: CancellationException) {
                if (statementFailure is CancellationException) {
                    WithTablesLogger.log.warn(ex) { "Table cleanup was cancelled after statement cancellation" }
                    throw statementFailure
                }
                statementFailure?.let(ex::addSuppressed)
                throw ex
            } catch (ex: Throwable) {
                if (statementFailure !is CancellationException) {
                    WithTablesLogger.log.warn(ex) { "Failed to drop tables" }
                }
                val database = testDB.db.requireNotNull("testDB.db")
                val recoveryFailure = try {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = database.transactionManager.defaultIsolationLevel!!,
                        db = database,
                    ) {
                        maxAttempts = 1
                        SchemaUtils.drop(*tables)
                    }
                    null
                } catch (recoveryCancellation: CancellationException) {
                    if (statementFailure is CancellationException) {
                        WithTablesLogger.log.warn(ex) { "Failed to drop tables after cancellation" }
                        WithTablesLogger.log.warn(recoveryCancellation) {
                            "Table cleanup recovery was cancelled after statement cancellation"
                        }
                        throw statementFailure
                    }
                    statementFailure?.let(recoveryCancellation::addSuppressed)
                    recoveryCancellation.addSuppressed(ex)
                    throw recoveryCancellation
                } catch (recoveryException: Throwable) {
                    recoveryException
                }

                if (statementFailure != null) {
                    suppressCleanupFailures(statementFailure, ex, recoveryFailure)
                } else if (recoveryFailure != null) {
                    throw recoveryFailure
                }
            }
        }
    }
}
