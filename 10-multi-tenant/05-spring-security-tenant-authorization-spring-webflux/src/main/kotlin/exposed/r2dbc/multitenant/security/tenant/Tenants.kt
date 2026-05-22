package exposed.r2dbc.multitenant.security.tenant

/**
 * Fixed tenant registry for the Spring Security tenant authorization example.
 */
object Tenants {

    /** Default tenant used only outside the mandatory HTTP tenant-header path. */
    val DEFAULT_TENANT: Tenant = Tenant.KOREAN

    /**
     * Supported example tenants.
     */
    enum class Tenant(val id: String) {
        KOREAN("korean"),
        ENGLISH("english");

        companion object {
            /** Finds a tenant by its normalized ID. */
            fun fromId(id: String): Tenant? = entries.find { it.id == id }
        }
    }

    /** Finds a tenant by its normalized ID. */
    fun findById(tenantId: String): Tenant? = Tenant.fromId(tenantId)

    /** Returns a tenant or throws when the ID is not registered. */
    fun getById(tenantId: String): Tenant =
        findById(tenantId) ?: throw IllegalArgumentException("No registered tenant for the supplied ID")
}
