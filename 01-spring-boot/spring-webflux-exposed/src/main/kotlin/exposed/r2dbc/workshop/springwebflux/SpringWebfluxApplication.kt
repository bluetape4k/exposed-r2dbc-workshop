package exposed.r2dbc.workshop.springwebflux

import io.bluetape4k.logging.KLogging
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class SpringWebfluxApplication {
    companion object: KLogging()
}

fun main(vararg args: String) {
    runApplication<SpringWebfluxApplication>(*args) {
        setWebApplicationType(WebApplicationType.REACTIVE)
    }
}
