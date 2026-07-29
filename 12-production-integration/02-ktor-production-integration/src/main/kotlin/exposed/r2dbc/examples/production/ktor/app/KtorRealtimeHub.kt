package exposed.r2dbc.examples.production.ktor.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Ktor production 예제에서 사용하는 프로세스 내부 realtime fan-out입니다.
 */
class KtorRealtimeHub: RealtimeDelivery {
    private val events = MutableSharedFlow<OutboxEventView>(
        extraBufferCapacity = liveBufferCapacity,
    )

    override suspend fun deliver(event: OutboxEventView): Boolean =
        events.subscriptionCount.value == 0 || events.tryEmit(event)

    fun live(): Flow<OutboxEventView> =
        events

    private companion object {
        const val liveBufferCapacity = 64
    }
}
