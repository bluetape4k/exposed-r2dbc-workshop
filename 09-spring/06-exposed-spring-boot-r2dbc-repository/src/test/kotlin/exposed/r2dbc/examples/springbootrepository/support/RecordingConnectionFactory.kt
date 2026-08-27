package exposed.r2dbc.examples.springbootrepository.support

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * 테스트에서 connection acquire와 close 균형만 관찰하는 얇은 R2DBC factory입니다.
 *
 * SQL statement 수나 provider 내부 round-trip은 관찰하지 않습니다. 이 recorder는
 * Flow 취소 뒤 connection이 반환되는지와 outer transaction의 connection 범위를
 * 확인하는 테스트 보조 장치로만 사용합니다.
 */
class RecordingConnectionFactory(
    private val delegate: ConnectionFactory,
) : ConnectionFactory {

    private val acquired = AtomicInteger()
    private val closed = AtomicInteger()

    /** 지금까지 delegate에서 acquire한 connection 수입니다. */
    val acquiredCount: Int
        get() = acquired.get()

    /** close publisher가 종료된 connection 수입니다. */
    val closedCount: Int
        get() = closed.get()

    /** 현재 recorder가 보유한 것으로 관찰되는 connection 수입니다. */
    val openCount: Int
        get() = acquiredCount - closedCount

    override fun create(): Publisher<out Connection> =
        Mono.from(delegate.create()).map { connection ->
            acquired.incrementAndGet()
            RecordingConnection(connection, closed)
        }

    override fun getMetadata(): ConnectionFactoryMetadata = delegate.metadata
}

private class RecordingConnection(
    private val delegate: Connection,
    private val closed: AtomicInteger,
) : Connection by delegate {

    private val closeObserved = AtomicBoolean(false)

    override fun close(): Publisher<Void> =
        Mono.from(delegate.close()).doFinally {
            if (closeObserved.compareAndSet(false, true)) {
                closed.incrementAndGet()
            }
        }
}
