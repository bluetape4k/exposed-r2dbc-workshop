package exposed.r2dbc.examples.production.ktor.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-process realtime fan-out used by the Ktor production example.
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
