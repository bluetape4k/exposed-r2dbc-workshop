package exposed.r2dbc.multitenant.ktor.tenant

import org.jetbrains.exposed.v1.core.Schema

/**
 * Builds an Exposed schema from a validated tenant enum.
 */
internal fun getSchemaDefinition(tenant: Tenants.Tenant): Schema =
    Schema(tenant.id)
