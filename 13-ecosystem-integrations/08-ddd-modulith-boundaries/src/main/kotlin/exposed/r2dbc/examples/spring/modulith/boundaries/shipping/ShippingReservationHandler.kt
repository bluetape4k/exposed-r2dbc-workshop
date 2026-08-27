package exposed.r2dbc.examples.spring.modulith.boundaries.shipping

import exposed.r2dbc.examples.spring.modulith.boundaries.orders.events.OrderAcceptedEvent
import exposed.r2dbc.examples.spring.modulith.boundaries.shipping.internal.ExposedShippingReservationRepository
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.Instant

/** shipping bounded context가 소유하는 reservation 조회 모델입니다. */
data class ShippingReservation(
    val id: Long,
    val orderKey: String,
    val customerId: String,
    val reservedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 공개 order event를 비동기 coroutine 경계에서 shipping 저장소로 전달합니다. */
@Service
class ShippingReservationHandler(
    private val repository: ExposedShippingReservationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EventListener
    fun reserveShipment(event: OrderAcceptedEvent) {
        scope.launch {
            repository.reserve(event)
        }
    }

    suspend fun findReservation(orderKey: String): ShippingReservation? =
        repository.findByOrderKey(orderKey)

    suspend fun clear() {
        repository.clear()
    }

    @PreDestroy
    fun stop() {
        scope.cancel()
    }
}
