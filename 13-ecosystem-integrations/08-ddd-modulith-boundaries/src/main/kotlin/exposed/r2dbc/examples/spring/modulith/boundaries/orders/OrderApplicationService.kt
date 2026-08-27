package exposed.r2dbc.examples.spring.modulith.boundaries.orders

import exposed.r2dbc.examples.spring.modulith.boundaries.orders.events.OrderAcceptedEvent
import exposed.r2dbc.examples.spring.modulith.boundaries.orders.internal.ExposedOrderRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.Instant

/** 주문 접수를 요청하는 command입니다. */
data class AcceptOrderCommand(
    val orderKey: String,
    val customerId: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 주문 aggregate 저장 결과로 반환하는 조회 모델입니다. */
data class OrderSummary(
    val id: Long,
    val orderKey: String,
    val customerId: String,
    val status: String,
    val acceptedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** order transaction commit 후 공개 domain event를 발행하는 애플리케이션 서비스입니다. */
@Service
class OrderApplicationService(
    private val orderRepository: ExposedOrderRepository,
    private val events: ApplicationEventPublisher,
) {
    suspend fun accept(command: AcceptOrderCommand): OrderSummary {
        val summary = orderRepository.accept(command)
        events.publishEvent(
            OrderAcceptedEvent(
                orderKey = summary.orderKey,
                customerId = summary.customerId,
                acceptedAt = summary.acceptedAt,
            )
        )
        return summary
    }
}
