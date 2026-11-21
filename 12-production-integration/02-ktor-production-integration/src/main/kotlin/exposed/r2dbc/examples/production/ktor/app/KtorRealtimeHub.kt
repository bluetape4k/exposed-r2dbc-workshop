package exposed.r2dbc.examples.production.ktor.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

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

    /**
     * live collector가 구독을 등록할 때까지 기다립니다.
     *
     * WebSocket route가 collector를 시작한 직후 publisher가 실행될 수 있으므로,
     * 구독 준비가 완료된 뒤에 live event를 전달해야 초기 이벤트 유실을 막을 수 있습니다.
     */
    internal suspend fun awaitSubscriber() {
        events.subscriptionCount.first { it > 0 }
    }

    private companion object {
        const val liveBufferCapacity = 64
    }
}
