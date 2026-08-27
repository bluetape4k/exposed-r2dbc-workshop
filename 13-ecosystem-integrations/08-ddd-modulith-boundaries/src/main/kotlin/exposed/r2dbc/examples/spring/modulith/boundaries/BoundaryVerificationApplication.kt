package exposed.r2dbc.examples.spring.modulith.boundaries

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** DDD bounded context와 Spring Modulith 경계 검증 예제의 진입점입니다. */
@SpringBootApplication
class BoundaryVerificationApplication

fun main(args: Array<String>) {
    runApplication<BoundaryVerificationApplication>(*args)
}
