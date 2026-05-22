package exposed.r2dbc.multitenant.ktor.tenant

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin

/**
 * Resolves `X-TENANT-ID` and stores only a validated tenant enum in the Ktor call.
 */
val TenantPlugin = createApplicationPlugin(name = "TenantPlugin") {
    onCall { call ->
        val rawValues = call.request.headers.getAll(TenantHeader).orEmpty()
        val tenantId = normalizeTenantHeader(rawValues)
        val tenant = Tenants.findById(tenantId)
            ?: throw InvalidTenantException("Unknown tenant id: $tenantId")
        call.attributes.put(TenantAttributeKey, tenant)
    }
}

/**
 * Returns the resolved tenant for the current Ktor call.
 */
fun ApplicationCall.currentTenant(): Tenants.Tenant =
    attributes.getOrNull(TenantAttributeKey)
        ?: error("TenantPlugin did not resolve a tenant for this call")

private fun normalizeTenantHeader(values: List<String>): String {
    if (values.isEmpty()) {
        throw InvalidTenantException("Missing tenant id header: $TenantHeader")
    }
    val normalized = values
        .flatMap { it.split(',') }
        .map { it.trim() }
    if (normalized.any { it.isEmpty() }) {
        throw InvalidTenantException("Blank tenant id header: $TenantHeader")
    }
    val distinct = normalized.toSet()
    if (distinct.size != 1) {
        throw InvalidTenantException("Conflicting tenant id headers: $TenantHeader")
    }
    return distinct.single()
}
