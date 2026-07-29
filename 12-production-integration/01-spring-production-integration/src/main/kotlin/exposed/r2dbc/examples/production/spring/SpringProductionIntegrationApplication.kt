package exposed.r2dbc.examples.production.spring

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring Boot 4 production integration 예제를 시작합니다.
 */
@SpringBootApplication
class SpringProductionIntegrationApplication

fun main(args: Array<String>) {
    runApplication<SpringProductionIntegrationApplication>(*args)
}

/**
 * Spring app-boundary 예제에서 사용하는 H2 R2DBC 데이터베이스를 제공합니다.
 */
@Configuration
class SpringProductionDatabaseConfig {
    @Bean
    fun productionDatabase(): R2dbcDatabase =
        R2dbcDatabase.connect("r2dbc:h2:mem:///spring-production-integration;DB_CLOSE_DELAY=-1;USER=sa;")

    @Bean
    fun productionWebClient(): WebClient =
        WebClient.builder().build()
}
