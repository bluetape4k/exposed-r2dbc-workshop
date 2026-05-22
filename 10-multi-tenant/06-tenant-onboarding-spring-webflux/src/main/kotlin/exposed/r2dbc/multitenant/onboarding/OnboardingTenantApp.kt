package exposed.r2dbc.multitenant.onboarding

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring WebFlux example application for Tenant onboarding routing.
 */
@SpringBootApplication
class OnboardingTenantApp {

    companion object: KLoggingChannel()
}

fun main(vararg args: String) {
    runApplication<OnboardingTenantApp>(*args) {
        setWebApplicationType(WebApplicationType.REACTIVE)
    }
}
