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
    val password: String = "password",
    val displayName: String = username,
    val roles: Set<String> = setOf("USER"),
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
    val displayName: String,
    val permission: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Principal installed by Ktor basic authentication.
 */
data class AuthPrincipal(
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
 * Authenticated profile returned by the Ktor authentication slice.
 */
@kotlinx.serialization.Serializable
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
 * Session metadata persisted by the Ktor authentication slice.
 */
@kotlinx.serialization.Serializable
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
@kotlinx.serialization.Serializable
data class SessionsView(
    val sessions: List<SessionView>,
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
@kotlinx.serialization.Serializable
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
@kotlinx.serialization.Serializable
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
@kotlinx.serialization.Serializable
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
