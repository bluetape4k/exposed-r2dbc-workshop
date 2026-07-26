package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@JvmInline
value class TenantId(val value: String): Serializable {
    init {
        value.requireNotBlank("tenantId")
        require(TenantIdPattern.matches(value)) { "tenantId must be lowercase letters, digits, or hyphens" }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
        private val TenantIdPattern = Regex("[a-z][a-z0-9-]{1,30}")
    }
}

enum class TenantLifecycleStatus {
    PROVISIONING,
    ACTIVE,
    FAILED,
}

enum class TenantFailureCode {
    RESERVE,
    POOL,
    SCHEMA,
    PROBE,
    PUBLISH,
    RECOVERY,
}

data class TenantMetadata(
    val tenantId: TenantId,
    val displayName: String,
    val status: TenantLifecycleStatus,
    val attempt: Int,
    val reservationToken: UUID,
    val version: Long,
    val leaseExpiresAt: Instant,
    val lastFailureCode: TenantFailureCode?,
    val createdAt: Instant,
    val updatedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

sealed interface TenantClaim {
    data class Owner(val metadata: TenantMetadata): TenantClaim
    data class Active(val metadata: TenantMetadata): TenantClaim
    data class Pending(val metadata: TenantMetadata): TenantClaim
    data class Conflict(val metadata: TenantMetadata): TenantClaim
}

fun TenantMetadata.asOwner(): TenantClaim.Owner = TenantClaim.Owner(this)
