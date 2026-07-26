package exposed.r2dbc.multitenant.resilientonboarding

import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleProperties
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.ZoneOffset

class ResilientTenantOnboardingAppTest {

    @Test
    fun `uses UTC clock for lifecycle leases`() {
        ResilientTenantOnboardingApp.lifecycleClock().zone shouldBeEqualTo ZoneOffset.UTC
    }

    @Test
    fun `rejects a lease no longer than the total provision timeout`() {
        assertThrows<IllegalArgumentException> {
            TenantLifecycleProperties(
                leaseDuration = Duration.ofSeconds(30),
                overallProvisionTimeout = Duration.ofSeconds(30),
            )
        }
    }
}
