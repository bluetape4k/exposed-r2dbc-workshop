package exposed.r2dbc.examples

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ExposedR2dbcRepositoryApp {
    companion object: KLoggingChannel()
}

fun main(vararg args: String) {
    runApplication<ExposedR2dbcRepositoryApp>(*args) {
        webApplicationType = WebApplicationType.REACTIVE
    }
}
