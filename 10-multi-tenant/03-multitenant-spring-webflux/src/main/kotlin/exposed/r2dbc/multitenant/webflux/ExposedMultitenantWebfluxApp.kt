package exposed.r2dbc.multitenant.webflux


import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// NOTE: R2dbc 에서는 mysql, h2 가 schema 를 지원하지 않는다. (JDBC 에서는 지원함)
@SpringBootApplication
class ExposedMultitenantWebfluxApp {

    companion object: KLoggingChannel()

}

fun main(vararg args: String) {
    runApplication<ExposedMultitenantWebfluxApp>(*args) {
        webApplicationType = WebApplicationType.REACTIVE
    }
}
