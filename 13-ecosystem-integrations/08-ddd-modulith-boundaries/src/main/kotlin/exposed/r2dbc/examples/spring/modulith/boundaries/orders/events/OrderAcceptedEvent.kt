package exposed.r2dbc.examples.spring.modulith.boundaries.orders.events

import java.io.Serializable
import java.time.Instant

/** shipping bounded context가 소비할 수 있는 주문 접수 event입니다. */
data class OrderAcceptedEvent(
    val orderKey: String,
    val customerId: String,
    val acceptedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
