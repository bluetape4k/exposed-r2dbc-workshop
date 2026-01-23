package exposed.r2dbc.shared.tests

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager

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

        try {
            statement(testDB)
            commit()
        } catch (ex: ExposedR2dbcException) {
            println("Failed to execute statement: ${ex.message}")
        } finally {
            try {
                SchemaUtils.drop(*tables)
                commit()
            } catch (ex: Throwable) {
                println("Failed to drop tables: ${ex.message}")
                val database = testDB.db!!
                inTopLevelSuspendTransaction(
                    transactionIsolation = database.transactionManager.defaultIsolationLevel!!,
                    db = database,
                ) {
                    maxAttempts = 1
                    runCatching { SchemaUtils.drop(*tables) }
                }
            }
        }
    }
}
