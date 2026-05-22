package exposed.r2dbc.multitenant.onboarding

import exposed.r2dbc.multitenant.onboarding.tenant.ProvisioningFailurePoint
import exposed.r2dbc.multitenant.onboarding.tenant.ProvisioningFailureSimulator
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.atomic.AtomicReference

@ActiveProfiles("h2")
@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [OnboardingTenantApp::class, TenantOnboardingTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractTenantOnboardingTest {

    companion object: KLoggingChannel()
}

@TestConfiguration
class TenantOnboardingTestConfig {

    @Bean
    fun provisioningFailureSwitch(): ProvisioningFailureSwitch =
        ProvisioningFailureSwitch()

    @Bean
    @Primary
    fun testProvisioningFailureSimulator(
        failureSwitch: ProvisioningFailureSwitch,
    ): ProvisioningFailureSimulator =
        ProvisioningFailureSimulator { point ->
            failureSwitch.failAt.get()?.let { expected ->
                if (expected == point) {
                    throw IllegalStateException("Simulated provisioning failure at $point")
                }
            }
        }
}

class ProvisioningFailureSwitch {
    val failAt: AtomicReference<ProvisioningFailurePoint?> = AtomicReference(null)
}
