package exposed.r2dbc.examples.springbootrepository

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.spring.data.exposed.r2dbc.repository.config.EnableExposedR2dbcRepositories
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 애플리케이션 소유 R2DBC pool과 provider repository scan을 사용하는 예제 애플리케이션입니다.
 */
@SpringBootApplication(proxyBeanMethods = false)
@EnableExposedR2dbcRepositories(
    basePackages = ["exposed.r2dbc.examples.springbootrepository.repository"],
)
class ExposedSpringBootR2dbcRepositoryApp {

    companion object : KLoggingChannel()
}

/**
 * REACTIVE WebApplicationType으로 예제 애플리케이션을 시작합니다.
 */
fun main(vararg args: String) {
    runApplication<ExposedSpringBootR2dbcRepositoryApp>(*args) {
        setWebApplicationType(WebApplicationType.REACTIVE)
    }
}
