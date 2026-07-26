package exposed.r2dbc.multitenant.resilientonboarding.tenant

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.resilient-onboarding")
data class TenantLifecycleProperties(
    val leaseDuration: Duration,
    val overallProvisionTimeout: Duration,
) {
    init {
        require(leaseDuration > overallProvisionTimeout) {
            "leaseDuration must be longer than overallProvisionTimeout"
        }
    }
}
