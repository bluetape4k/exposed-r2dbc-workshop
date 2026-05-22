package exposed.r2dbc.examples.production.ktor.app

import java.io.Serializable

/**
 * Request for registering an account and permission.
 */
@kotlinx.serialization.Serializable
data class RegisterAccountRequest(
    val username: String,
    val apiKey: String,
    val permission: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Request for creating a validated work item.
 */
@kotlinx.serialization.Serializable
data class CreateWorkItemRequest(
    val owner: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Request for persisted outbound work with an idempotency key.
 */
@kotlinx.serialization.Serializable
data class EnqueueOutboundRequest(
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the Ktor authentication slice.
 */
@kotlinx.serialization.Serializable
data class AccountView(
    val id: String,
    val username: String,
    val permission: String,
    val sessionToken: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the Ktor application slice.
 */
@kotlinx.serialization.Serializable
data class WorkItemView(
    val id: String,
    val owner: String,
    val payload: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Persisted outbox event returned by realtime replay.
 */
@kotlinx.serialization.Serializable
data class OutboxEventView(
    val sequence: Long,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Response returned by the outbound HTTP outbox slice.
 */
@kotlinx.serialization.Serializable
data class OutboundRequestView(
    val id: String,
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Readiness response returned by the diagnostics slice.
 */
@kotlinx.serialization.Serializable
data class ReadinessView(
    val status: String,
    val details: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Structured API error shared by Ktor handlers.
 */
@kotlinx.serialization.Serializable
data class StructuredError(
    val code: String,
    val message: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Signals a duplicate outbound idempotency key.
 */
class DuplicateIdempotencyKeyException(
    key: String,
): RuntimeException("Duplicate idempotency key: $key")
