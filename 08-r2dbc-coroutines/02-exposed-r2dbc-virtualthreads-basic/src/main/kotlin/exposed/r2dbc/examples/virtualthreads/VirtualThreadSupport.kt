package exposed.r2dbc.examples.virtualthreads

import io.bluetape4k.concurrent.virtualthread.VirtualThreadExecutor
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransactionAsync
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import java.util.concurrent.ExecutorService

suspend fun <T> virtualThreadTransaction(
    executor: ExecutorService? = VirtualThreadExecutor,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T,
): T =
    virtualThreadTransactionAsync(executor, db, transactionIsolation, readOnly, statement).await()

suspend fun <T> virtualThreadTransactionAsync(
    executor: ExecutorService? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T,
): Deferred<T> {
    val dispatcher = executor?.asCoroutineDispatcher() ?: VirtualThreadExecutor.asCoroutineDispatcher()
    val isolationLevel = transactionIsolation ?: db?.transactionManager?.defaultIsolationLevel

    return suspendTransactionAsync(
        context = dispatcher,
        db = db,
        transactionIsolation = isolationLevel,
        readOnly = readOnly
    ) {
        statement()
    }
}
