package exposed.r2dbc.multitenant.onboarding.tenant

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable
import java.time.Instant

/**
 * Validated tenant identifier used after the HTTP controller boundary.
 */
@JvmInline
value class TenantId(val value: String): Serializable {
    init {
        value.requireNotBlank("tenantId")
        if (!TenantIdRegex.matches(value)) {
            throw InvalidTenantRequestException("Invalid tenant id")
        }
    }

    override fun toString(): String = value

    companion object {
        private const val serialVersionUID = 1L

        private val TenantIdRegex = Regex("[a-z][a-z0-9-]{1,30}")

        fun parse(raw: String?): TenantId {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isBlank()) {
                throw InvalidTenantRequestException("tenantId must not be blank")
            }
            return TenantId(normalized)
        }
    }
}

/**
 * Tenant provisioning state stored as a string enum for stable examples.
 */
enum class TenantStatus {
    PROVISIONING,
    ACTIVE,
    FAILED,
}

/**
 * Persisted tenant metadata.
 */
data class TenantMetadata(
    val tenantId: TenantId,
    val displayName: String,
    val databaseName: String,
    val r2dbcUrl: String,
    val status: TenantStatus,
    val createdAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Public onboarding request body.
 */
data class TenantOnboardingRequest(
    val tenantId: String,
    val displayName: String,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Public onboarding response. The raw R2DBC URL is intentionally omitted.
 */
data class TenantOnboardingResponse(
    val tenantId: String,
    val displayName: String,
    val status: TenantStatus,
    val createdAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L

        fun from(metadata: TenantMetadata): TenantOnboardingResponse =
            TenantOnboardingResponse(
                tenantId = metadata.tenantId.value,
                displayName = metadata.displayName,
                status = metadata.status,
                createdAt = metadata.createdAt,
            )
    }
}

/**
 * Shared tenant request header constants.
 */
object TenantHeaders {
    const val TENANT_HEADER: String = "X-TENANT-ID"
    const val ADMIN_TOKEN_HEADER: String = "X-ADMIN-TOKEN"
}

/**
 * Resolves the tenant header for request-time routing.
 */
object TenantIdResolver {
    fun resolve(rawTenantId: String?): TenantId =
        TenantId.parse(rawTenantId)
}
