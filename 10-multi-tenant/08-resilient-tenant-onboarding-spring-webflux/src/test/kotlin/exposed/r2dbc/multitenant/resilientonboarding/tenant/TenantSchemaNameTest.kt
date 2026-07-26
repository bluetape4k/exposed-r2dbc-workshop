package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class TenantSchemaNameTest {

    @Test
    fun `tenant id is converted to a prefixed PostgreSQL schema name`() {
        TenantSchemaName.from(TenantId("clinic-seoul")).value shouldBeEqualTo "tenant_clinic_seoul"
    }

    @Test
    fun `the longest accepted tenant id remains within the PostgreSQL identifier limit`() {
        TenantSchemaName.from(TenantId("a".repeat(31))).value.length shouldBeEqualTo 38
    }
}
