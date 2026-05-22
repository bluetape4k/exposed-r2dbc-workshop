package exposed.r2dbc.multitenant.onboarding.tenant

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class TenantIdTest {

    @Test
    fun `accepts valid lowercase slug`() {
        TenantId("korean-1").value shouldBeEqualTo "korean-1"
    }

    @Test
    fun `rejects invalid tenant id values`() {
        listOf("", "KOREAN", "a/b", "a.b", "a:b", "a".repeat(32)).forEach { value ->
            assertFailsWith<Exception> {
                TenantId(value)
            }
        }
    }
}
