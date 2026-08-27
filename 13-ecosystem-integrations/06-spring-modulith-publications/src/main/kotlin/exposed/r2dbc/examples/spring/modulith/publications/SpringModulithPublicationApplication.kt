package exposed.r2dbc.examples.spring.modulith.publications

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Spring Modulith module metadata와 custom R2DBC publication log 예제의 진입점입니다. */
@SpringBootApplication
class SpringModulithPublicationApplication

fun main(args: Array<String>) {
    runApplication<SpringModulithPublicationApplication>(*args)
}
