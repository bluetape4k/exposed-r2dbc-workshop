package exposed.r2dbc.multitenant.ktor.tenant

import io.ktor.util.AttributeKey

internal val TenantAttributeKey = AttributeKey<Tenants.Tenant>("Tenant")

internal const val TenantHeader = "X-TENANT-ID"

/**
 * Raised when the request tenant header cannot be resolved to a supported tenant.
 */
class InvalidTenantException(message: String): IllegalArgumentException(message)
