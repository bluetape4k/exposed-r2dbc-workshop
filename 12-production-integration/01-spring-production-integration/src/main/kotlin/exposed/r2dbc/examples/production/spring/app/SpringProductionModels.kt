package exposed.r2dbc.examples.production.spring.app

import java.io.Serializable

/**
 * Registers an account and its workshop permission.
 */
data class RegisterAccountRequest(
    val username: String,
    val apiKey: String,
    val permission: String,
    val password: String = "password",
    val displayName: String = username,
    val roles: Set<String> = setOf("USER"),
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
    val displayName: String,
    val permission: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Authenticated profile returned by the Spring Security slice.
 */
data class AuthProfileView(
    val username: String,
    val displayName: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Session metadata persisted by the authentication slice.
 */
data class SessionView(
    val token: String?,
    val username: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Collection wrapper for persisted session metadata.
 */
data class SessionsView(
    val sessions: List<SessionView>,
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
    val status: OutboxStatus,
    val attempts: Int,
    val lastError: String?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Collection wrapper for persisted realtime outbox rows.
 */
data class OutboxEventsView(
    val events: List<OutboxEventView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Realtime outbox publish attempt summary.
 */
data class PublishOutboxView(
    val attempted: Int,
    val delivered: Int,
    val failed: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Explicit delivery state persisted with each realtime outbox row.
 */
enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}

/**
 * Delivery boundary used by the realtime outbox publisher.
 */
interface RealtimeDelivery {
    suspend fun deliver(event: OutboxEventView): Boolean
}

/**
 * Outbound request response returned by the HTTP client outbox slice.
 */
data class OutboundRequestView(
    val id: String,
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
    val status: OutboundStatus,
    val attempts: Int,
    val lastStatusCode: Int?,
    val lastError: String?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Collection wrapper for persisted outbound HTTP rows.
 */
data class OutboundRequestsView(
    val requests: List<OutboundRequestView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Outbound HTTP dispatch attempt summary.
 */
data class DispatchOutboundView(
    val attempted: Int,
    val succeeded: Int,
    val retryableFailed: Int,
    val permanentFailed: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Explicit delivery state persisted with each outbound HTTP row.
 */
enum class OutboundStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    RETRYABLE_FAILED,
    PERMANENT_FAILED,
}

/**
 * Result returned by the outbound HTTP client boundary.
 */
data class OutboundDispatchResult(
    val statusCode: Int,
    val error: String? = null,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * HTTP delivery boundary used by the outbound outbox dispatcher.
 */
interface OutboundDelivery {
    suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult
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

/**
 * Signals a role mismatch after authentication succeeds.
 */
class PermissionDeniedException(
    permission: String,
): RuntimeException("$permission permission is required")

/**
 * Repository-owned authentication account.
 */
data class AuthAccount(
    val id: String,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val permission: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
