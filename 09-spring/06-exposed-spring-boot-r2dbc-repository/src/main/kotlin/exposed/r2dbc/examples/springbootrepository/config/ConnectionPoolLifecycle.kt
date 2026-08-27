package exposed.r2dbc.examples.springbootrepository.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.r2dbc.pool.ConnectionPool
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.beans.factory.DisposableBean

/**
 * 애플리케이션 소유 [R2dbcDatabase]와 [ConnectionPool]을 Spring의 유일한
 * disposable 경계에 연결합니다.
 *
 * [destroy]는 context close와 테스트의 명시적 정리가 겹쳐도 Exposed manager를
 * 먼저 unregister하고 pool을 한 번만 dispose합니다. close 과정의 예외는 삼키지
 * 않고 Spring lifecycle 호출자에게 그대로 전달하여 graceful shutdown 실패가
 * 관찰 가능하게 유지됩니다.
 */
class ConnectionPoolLifecycle(
    private val database: R2dbcDatabase,
    private val connectionPool: ConnectionPool,
) : DisposableBean {

    companion object : KLoggingChannel()

    private val closed = AtomicBoolean(false)

    /** Spring context 종료 시 Exposed manager unregister 후 pool을 한 번만 정리합니다. */
    override fun destroy() {
        if (closed.compareAndSet(false, true)) {
            try {
                // R2dbcDatabase.connect가 등록한 global manager를 pool보다 먼저 제거합니다.
                TransactionManager.closeAndUnregister(database)
            } finally {
                log.info { "r2dbc_pool_dispose" }
                connectionPool.dispose()
            }
        }
    }
}
