package exposed.r2dbc.examples.spring.modulith.publications.orders.events

import java.io.Serializable
import java.time.Instant

/** 주문 승인 후 publication log와 fulfillment handler가 공유하는 공개 이벤트입니다. */
data class OrderApprovedEvent(
    val orderKey: String,
    val customerId: String,
    val approvedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** custom R2DBC publication log가 노출하는 상태입니다. */
enum class PublicationStatus {
    PENDING,
    FAILED,
    COMPLETED,
}

/** fulfillment module이 소비하는 publication 기록의 공개 표현입니다. */
data class PublicationRecord(
    val id: Long,
    val listenerId: String,
    val event: OrderApprovedEvent,
    val status: PublicationStatus,
    val attempts: Int,
    val lastError: String?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** orders module의 custom R2DBC publication log와 fulfillment을 연결하는 비동기 경계입니다. */
interface PublicationLogPort {
    suspend fun findOutstanding(): List<PublicationRecord>

    suspend fun markAttempt(id: Long)

    suspend fun markCompleted(id: Long)

    suspend fun markFailed(id: Long, message: String)
}
