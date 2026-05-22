package exposed.r2dbc.examples.production.spring.app

import java.io.Serializable

/**
 * Registers an account and its workshop permission.
 */
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
 * Creates a validated production work item.
 */
data class CreateWorkItemRequest(
    val owner: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Describes an outbound call that must be persisted before dispatch.
 */
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
 * Account response returned by the authentication slice.
 */
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
 * Work item response returned by the application slice.
 */
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
 * Persisted realtime event returned by the realtime outbox slice.
 */
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
 * Outbound request response returned by the HTTP client outbox slice.
 */
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
data class ReadinessView(
    val status: String,
    val details: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Structured API error used by Spring and mirrored by the Ktor module.
 */
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
