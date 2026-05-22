package exposed.r2dbc.multitenant.security.tenant

/**
 * Reactor context keys used by tenant routing.
 */
object TenantContextKeys {

    /** Context key containing the normalized tenant ID. */
    const val TENANT_ID: String = "tenantId"
}
