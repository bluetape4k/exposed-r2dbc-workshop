package exposed.r2dbc.multitenant.security.tenant

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Normalizes and validates user-supplied tenant IDs.
 */
object TenantIdResolver {

    private val TenantIdRegex = Regex("[a-zA-Z0-9_-]{1,64}")

    /**
     * Returns the normalized tenant ID or throws `400 Bad Request`.
     */
    fun resolve(rawTenantId: String?): String {
        val normalized = rawTenantId?.trim()
            ?: throw badRequest("Missing tenant id header")

        if (normalized.isEmpty()) {
            throw badRequest("Missing tenant id header")
        }
        if (!TenantIdRegex.matches(normalized)) {
            throw badRequest("Invalid tenant id header")
        }
        Tenants.findById(normalized) ?: throw badRequest("Unknown tenant id header")
        return normalized
    }

    /**
     * Resolves an authenticated tenant claim without turning claim problems into
     * request-header `400 Bad Request` failures.
     */
    fun resolveAuthenticatedTenant(rawTenantId: String?): Tenants.Tenant? {
        val normalized = rawTenantId?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return null
        }
        if (!TenantIdRegex.matches(normalized)) {
            return null
        }
        return Tenants.findById(normalized)
    }

    private fun badRequest(reason: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
}
