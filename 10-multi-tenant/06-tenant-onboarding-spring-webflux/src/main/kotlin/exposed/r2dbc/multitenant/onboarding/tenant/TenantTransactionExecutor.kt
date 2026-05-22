package exposed.r2dbc.multitenant.onboarding.tenant

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import kotlin.coroutines.CoroutineContext

/**
 * Executes request-path Exposed work against the explicit tenant routing database.
 */
@Component
class TenantTransactionExecutor(
    @param:Qualifier("tenantRoutingDatabase")
    private val tenantRoutingDatabase: R2dbcDatabase,
    private val registry: TenantConnectionFactoryRegistry,
) {

    /**
     * Runs [statement] while preserving Reactor tenant context for the routing factory.
     */
    suspend fun <T> execute(statement: suspend () -> T): T {
        val coroutineContext = currentCoroutineContext()
        val result = Mono.deferContextual<ResultBox<T>> { contextView ->
            val bridgedContext = coroutineContext.withReactorContext(ReactorContext(contextView))
            mono(bridgedContext) {
                suspendTransaction(db = tenantRoutingDatabase) {
                    ResultBox(statement())
                }
            }
        }.awaitSingle()
        return result.value
    }

    /**
     * Runs [statement] under an explicit tenant context.
     */
    suspend fun <T> execute(
        tenantId: TenantId,
        statement: suspend () -> T,
    ): T =
        suspendTransaction(db = registry.getDatabase(tenantId)) {
            statement()
        }

    private fun CoroutineContext.withReactorContext(reactorContext: ReactorContext): CoroutineContext =
        minusKey(Job.Key).minusKey(ReactorContext.Key) + reactorContext

    private class ResultBox<T>(val value: T)
}
