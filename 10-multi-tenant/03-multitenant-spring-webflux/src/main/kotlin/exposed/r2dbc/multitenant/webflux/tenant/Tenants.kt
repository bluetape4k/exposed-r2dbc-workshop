package exposed.r2dbc.multitenant.webflux.tenant

import org.jetbrains.exposed.v1.core.Schema

/**
 * 테넌트에 대한 정보를 관리합니다.
 */
object Tenants {

    /** 헤더가 없거나 비어 있을 때 사용할 기본 테넌트입니다. */
    val DEFAULT_TENANT = Tenant.KOREAN

    /**
     * 애플리케이션이 지원하는 테넌트 목록입니다.
     */
    enum class Tenant(val id: String) {
        KOREAN("korean"),
        ENGLISH("english");

        companion object {
            /**
             * 문자열 식별자에 대응하는 [Tenant]를 찾습니다.
             */
            fun fromId(id: String): Tenant? = entries.find { it.id == id }
        }
    }

    /**
     * 문자열 식별자에 대응하는 [Tenant]를 찾고, 없으면 `null`을 반환합니다.
     */
    fun findById(tenantId: String): Tenant? = Tenant.fromId(tenantId)

    /**
     * 문자열 식별자에 대응하는 [Tenant]를 반환합니다.
     *
     * 존재하지 않는 식별자를 전달하면 [IllegalArgumentException]을 던집니다.
     */
    fun getById(tenantId: String): Tenant =
        findById(tenantId) ?: throw IllegalArgumentException("No tenant found for id: $tenantId")

    private val tenantSchemas = mapOf(
        Tenant.KOREAN to getSchemaDefinition(Tenant.KOREAN),
        Tenant.ENGLISH to getSchemaDefinition(Tenant.ENGLISH),
    )

    /**
     * [tenant]에 매핑된 DB 스키마 정의를 반환합니다.
     */
    fun getTenantSchema(tenant: Tenant): Schema =
        tenantSchemas[tenant] ?: throw IllegalArgumentException("No schema found for tenant: ${tenant.id}")
}
