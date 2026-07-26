package exposed.r2dbc.multitenant.resilientonboarding.tenant

@JvmInline
value class TenantSchemaName private constructor(val value: String) {

    companion object {
        private const val PREFIX = "tenant_"
        private const val POSTGRES_IDENTIFIER_LIMIT = 63
        private val SchemaPattern = Regex("[a-z][a-z0-9_]*")

        fun from(tenantId: TenantId): TenantSchemaName {
            val value = PREFIX + tenantId.value.replace('-', '_')
            require(value.length <= POSTGRES_IDENTIFIER_LIMIT) {
                "tenant schema name exceeds PostgreSQL's 63-byte identifier limit"
            }
            require(SchemaPattern.matches(value)) {
                "tenant schema name must contain lowercase letters, digits, or underscores"
            }
            return TenantSchemaName(value)
        }
    }
}
