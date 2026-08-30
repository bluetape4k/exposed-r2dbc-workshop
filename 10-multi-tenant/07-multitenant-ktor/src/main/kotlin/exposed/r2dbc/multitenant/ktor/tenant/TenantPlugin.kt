package exposed.r2dbc.multitenant.ktor.tenant

import io.bluetape4k.ktor.tenant.KtorTenantContext
import io.bluetape4k.tenant.TenantId
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin

/**
 * `X-TENANT-ID`를 해석하고 검증된 tenant를 Ktor call에 binding합니다.
 */
val TenantPlugin = createApplicationPlugin(name = "TenantPlugin") {
    onCall { call ->
        val rawValues = call.request.headers.getAll(TenantHeader).orEmpty()
        val tenantId = normalizeTenantHeader(rawValues)
        val tenant = Tenants.findById(tenantId)
            ?: throw InvalidTenantException("Unknown tenant id: $tenantId")
        KtorTenantContext.bindTenant(call, TenantId(tenant.id))
    }
}

/**
 * 현재 Ktor call의 provider tenant를 local registry 값으로 반환합니다.
 */
fun ApplicationCall.currentTenant(): Tenants.Tenant =
    KtorTenantContext.requireCurrent(this).value.let { tenantId ->
        Tenants.findById(tenantId)
            ?: error("TenantPlugin resolved an unregistered tenant: $tenantId")
    }

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
