package exposed.r2dbc.multitenant.ktor.tenant

import io.ktor.util.AttributeKey

internal val TenantAttributeKey = AttributeKey<Tenants.Tenant>("Tenant")

internal const val TenantHeader = "X-TENANT-ID"

/**
 * request tenant header가 지원 tenant로 해석되지 않을 때 발생한다.
 */
class InvalidTenantException(message: String): IllegalArgumentException(message)
