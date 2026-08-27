package exposed.r2dbc.examples.spring.modulith.publications.orders.internal

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp

/** orders module이 소유하는 주문 table입니다. */
internal object WorkshopOrders: LongIdTable("modulith_orders") {
    val orderKey = varchar("order_key", 80).uniqueIndex()
    val customerId = varchar("customer_id", 80)
    val status = varchar("status", 30)
    val approvedAt = timestamp("approved_at")
}

/** orders module이 소유하는 custom publication log table입니다. */
internal object WorkshopPublicationLog: LongIdTable("modulith_publication_log") {
    val listenerId = varchar("listener_id", 120)
    val eventType = varchar("event_type", 200)
    val orderKey = varchar("order_key", 80)
    val customerId = varchar("customer_id", 80)
    val approvedAt = timestamp("approved_at")
    val status = varchar("status", 20)
    val attempts = integer("attempts")
    val lastError = varchar("last_error", 240).nullable()
}
