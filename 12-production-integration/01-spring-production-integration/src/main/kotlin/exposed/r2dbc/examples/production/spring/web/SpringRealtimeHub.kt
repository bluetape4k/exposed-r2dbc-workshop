package exposed.r2dbc.examples.production.spring.web

import exposed.r2dbc.examples.production.spring.app.OutboxEventView
import exposed.r2dbc.examples.production.spring.app.RealtimeDelivery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * In-process realtime fan-out used by the Spring production example.
 */
@Component
class SpringRealtimeHub: RealtimeDelivery {
    private val emitMutex = Mutex()
    private val sink = Sinks.many().multicast().directBestEffort<OutboxEventView>()

    override suspend fun deliver(event: OutboxEventView): Boolean =
        emitMutex.withLock {
            val result = sink.tryEmitNext(event)
            result.isSuccess || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
        }

    fun live(): Flux<OutboxEventView> =
        sink.asFlux()
}
