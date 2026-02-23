package exposed.r2dbc.examples.routing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Exposed R2DBC 라우팅 예제를 실행하는 Spring Boot 애플리케이션입니다.
 */
@SpringBootApplication
class RoutingR2dbcApplication

fun main(args: Array<String>) {
    runApplication<RoutingR2dbcApplication>(*args)
}
