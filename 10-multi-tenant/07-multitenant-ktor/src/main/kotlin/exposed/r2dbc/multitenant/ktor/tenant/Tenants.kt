package exposed.r2dbc.multitenant.ktor.tenant

/**
 * Tenant identifiers supported by the Ktor schema-per-tenant example.
 */
object Tenants {

    enum class Tenant(val id: String) {
        KOREAN("korean"),
        ENGLISH("english");

        companion object {
            fun fromId(id: String): Tenant? = entries.find { it.id == id }
        }
    }

    fun findById(tenantId: String): Tenant? = Tenant.fromId(tenantId)
}
