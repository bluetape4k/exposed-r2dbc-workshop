package exposed.r2dbc.examples.routing.config

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.support.DefaultTransactionDefinition
import reactor.core.publisher.Mono

/**
 * Reactive 트랜잭션 경계와 라우팅 read-only 힌트를 함께 적용하는 실행기입니다.
 */
@Component
class RoutingTransactionalExecutor(
    transactionManager: ReactiveTransactionManager,
    transactionalOperator: TransactionalOperator,
) {

    private val readWriteOperator = transactionalOperator

    private val readOnlyOperator = TransactionalOperator.create(
        transactionManager,
        DefaultTransactionDefinition().apply {
            isReadOnly = true
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        }
    )

    /**
     * read-write 트랜잭션으로 [block]을 실행합니다.
     */
    suspend fun <T: Any> readWrite(block: suspend () -> T): T =
        execute(readOnly = false, operator = readWriteOperator, block = block)

    /**
     * read-only 트랜잭션으로 [block]을 실행합니다.
     */
    suspend fun <T: Any> readOnly(block: suspend () -> T): T =
        execute(readOnly = true, operator = readOnlyOperator, block = block)

    private suspend fun <T: Any> execute(
        readOnly: Boolean,
        operator: TransactionalOperator,
        block: suspend () -> T,
    ): T {
        val publisher = Mono.deferContextual { _ ->
            mono {
                block()
            }.contextWrite { context ->
                context.put(RoutingContextKeys.READ_ONLY, readOnly)
            }
        }
        return operator.transactional(publisher).awaitSingle()
    }
}
