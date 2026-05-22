package exposed.r2dbc.multitenant.connectionfactory

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring WebFlux example application for connection-factory-per-tenant routing.
 */
@SpringBootApplication
class ConnectionFactoryTenantApp {

    companion object: KLoggingChannel()
}

fun main(vararg args: String) {
    runApplication<ConnectionFactoryTenantApp>(*args) {
        setWebApplicationType(WebApplicationType.REACTIVE)
    }
}
