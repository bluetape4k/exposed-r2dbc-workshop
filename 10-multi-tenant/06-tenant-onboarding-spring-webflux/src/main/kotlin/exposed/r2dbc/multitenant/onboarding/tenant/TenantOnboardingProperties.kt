package exposed.r2dbc.multitenant.onboarding.tenant

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for the tenant onboarding workshop module.
 */
@ConfigurationProperties(prefix = "app.onboarding")
class TenantOnboardingProperties {
    var adminToken: String = "workshop-admin"
    var maxTenants: Int = 4
    var registryUrl: String = "r2dbc:h2:mem:///tenant_onboarding_registry;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    var tenantUrlPrefix: String = "r2dbc:h2:mem:///"
    var tenantUrlOptions: String = "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    var overallTimeout: Duration = Duration.ofSeconds(60)
    var reservationTimeout: Duration = Duration.ofSeconds(10)
    var schemaTimeout: Duration = Duration.ofSeconds(30)
    var pool: TenantPoolProperties = TenantPoolProperties()
}

class TenantPoolProperties {
    var maxSize: Int = 4
    var initialSize: Int = 1
    var minIdle: Int = 0
    var maxIdleTime: Duration = Duration.ofSeconds(30)
    var maxLifeTime: Duration = Duration.ofMinutes(30)
    var maxCreateConnectionTime: Duration = Duration.ofSeconds(10)
    var maxAcquireTime: Duration = Duration.ofSeconds(5)
    var acquireRetry: Int = 3
    var backgroundEvictionInterval: Duration = Duration.ofMinutes(1)
}
