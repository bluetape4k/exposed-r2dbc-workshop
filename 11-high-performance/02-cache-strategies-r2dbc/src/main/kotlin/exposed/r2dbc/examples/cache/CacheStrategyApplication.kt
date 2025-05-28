package exposed.r2dbc.examples.cache

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CacheStrategyApplication {

    companion object: KLoggingChannel()
}

fun main(args: Array<String>) {
    runApplication<CacheStrategyApplication>(*args) {
        webApplicationType = WebApplicationType.REACTIVE
    }
}
