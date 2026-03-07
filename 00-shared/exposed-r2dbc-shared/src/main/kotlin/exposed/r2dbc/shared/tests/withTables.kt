package exposed.r2dbc.shared.tests

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager

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
        runCatching { SchemaUtils.drop(*tables) }
        SchemaUtils.create(*tables)
        commit()

        var statementFailure: Throwable? = null
        try {
            statement(testDB)
            commit()
        } catch (ex: Throwable) {
            statementFailure = ex
            throw ex
        } finally {
            try {
                SchemaUtils.drop(*tables)
                commit()
            } catch (ex: Throwable) {
                println("Failed to drop tables: ${ex.message}")
                val database = testDB.db!!
                val recoveryFailure = runCatching {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = database.transactionManager.defaultIsolationLevel!!,
                        db = database,
                    ) {
                        maxAttempts = 1
                        SchemaUtils.drop(*tables)
                    }
                }.exceptionOrNull()

                if (statementFailure != null) {
                    statementFailure.addSuppressed(ex)
                    recoveryFailure?.let(statementFailure::addSuppressed)
                } else if (recoveryFailure != null) {
                    throw recoveryFailure
                }
            }
        }
    }
}
