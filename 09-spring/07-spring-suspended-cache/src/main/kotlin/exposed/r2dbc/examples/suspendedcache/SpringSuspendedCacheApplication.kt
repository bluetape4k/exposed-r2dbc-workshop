package exposed.r2dbc.examples.suspendedcache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringSuspendedCacheApplication {
    companion object: KLoggingChannel()
}

fun main(vararg args: String) {
    runApplication<SpringSuspendedCacheApplication>(*args) {
        webApplicationType = WebApplicationType.REACTIVE
    }
}
