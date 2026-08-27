package exposed.r2dbc.examples.spring.modulith.publications.orders

import exposed.r2dbc.examples.spring.modulith.publications.orders.internal.ExposedOrderRepository
import org.springframework.stereotype.Service
import java.io.Serializable

/** 주문 승인을 요청하는 command입니다. */
data class ApproveOrderCommand(
    val orderKey: String,
    val customerId: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 주문 승인 transaction 결과입니다. */
data class OrderSummary(
    val id: Long,
    val orderKey: String,
    val customerId: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 주문 저장과 custom publication log 기록을 하나의 R2DBC transaction으로 묶습니다. */
@Service
class OrderApplicationService(
    private val orderRepository: ExposedOrderRepository,
) {
    suspend fun approve(command: ApproveOrderCommand): OrderSummary =
        orderRepository.approve(command)
}
