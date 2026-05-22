package exposed.r2dbc.multitenant.security

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring WebFlux example application for Spring Security tenant authorization routing.
 */
@SpringBootApplication
class SecurityTenantApp {

    companion object: KLoggingChannel()
}

fun main(vararg args: String) {
    runApplication<SecurityTenantApp>(*args) {
        setWebApplicationType(WebApplicationType.REACTIVE)
    }
}
