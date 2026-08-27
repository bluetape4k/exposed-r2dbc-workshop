package exposed.r2dbc.examples.spring.modulith.boundaries.invalid.shipping

import exposed.r2dbc.examples.spring.modulith.boundaries.invalid.orders.events.OrderAcceptedEvent
import exposed.r2dbc.examples.spring.modulith.boundaries.invalid.orders.internal.LeakyOrderRepository

/** named interface 밖의 orders internal dependency를 의도적으로 만든 fixture입니다. */
class ShippingBoundaryLeak(
    private val leakyOrderRepository: LeakyOrderRepository,
) {
    fun reserveAfter(event: OrderAcceptedEvent): Boolean =
        leakyOrderRepository.exists(event.orderKey)
}
