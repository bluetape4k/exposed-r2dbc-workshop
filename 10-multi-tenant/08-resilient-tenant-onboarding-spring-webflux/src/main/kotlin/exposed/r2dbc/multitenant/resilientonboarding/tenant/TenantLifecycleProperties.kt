package exposed.r2dbc.multitenant.resilientonboarding.tenant

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.resilient-onboarding")
data class TenantLifecycleProperties(
    val leaseDuration: Duration = Duration.ofMinutes(2),
    val overallProvisionTimeout: Duration = Duration.ofMinutes(1),
    val adminToken: String = "workshop-admin",
) {
    init {
        require(leaseDuration > overallProvisionTimeout) {
            "leaseDuration must be longer than overallProvisionTimeout"
        }
    }
}
