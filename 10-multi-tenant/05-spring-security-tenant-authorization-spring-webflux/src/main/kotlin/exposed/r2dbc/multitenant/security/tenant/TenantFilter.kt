package exposed.r2dbc.multitenant.security.tenant

/**
 * Shared tenant request header constants.
 *
 * Security-aware routing is handled by `AuthorizedTenantContextWebFilter`, not
 * this class. Keeping this class unregistered prevents header-only tenant trust.
 */
class TenantFilter private constructor() {

    companion object {
        /** HTTP header used to select the tenant. */
        const val TENANT_HEADER = "X-TENANT-ID"
    }
}
