package exposed.r2dbc.multitenant.webflux.tenant

import org.jetbrains.exposed.v1.core.Schema

/**
 * 테넌트 ID를 기반으로 Exposed [Schema] 정의를 생성합니다.
 *
 * Schema-based 멀티테넌시 전략에서 각 테넌트는 독립된 DB 스키마를 가집니다.
 * 반환된 [Schema]는 `SchemaUtils.createSchema` 및 `SchemaUtils.setSchema`에 사용됩니다.
 */
internal fun getSchemaDefinition(tenant: Tenants.Tenant): Schema =
    Schema(
        tenant.id,
        defaultTablespace = "USERS",
        temporaryTablespace = "TEMP ",
        quota = "20M",
        on = "USERS"
    )
