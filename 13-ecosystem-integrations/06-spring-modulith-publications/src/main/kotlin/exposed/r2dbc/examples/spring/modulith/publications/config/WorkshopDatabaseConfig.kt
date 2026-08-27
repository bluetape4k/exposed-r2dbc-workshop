package exposed.r2dbc.examples.spring.modulith.publications.config

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Spring context가 소유하는 H2 R2DBC pool과 Exposed Database를 구성합니다. */
@Configuration(proxyBeanMethods = false)
class WorkshopDatabaseConfig {

    @Bean
    fun connectionFactoryOptions(
        @Value("\${workshop.r2dbc.database-name:spring-modulith-publications}") databaseName: String,
    ): ConnectionFactoryOptions = h2ConnectionFactoryOptions(
        database = databaseName,
        closeOnExit = false,
    )

    @Bean(destroyMethod = "dispose")
    fun r2dbcPool(options: ConnectionFactoryOptions): ConnectionPool =
        connectionPoolOf(options) {
            maxSize = 4
            initialSize = 1
            minIdle = 0
            maxCreateConnectionTime = Duration.ofSeconds(5)
            maxAcquireTime = Duration.ofSeconds(3)
        }

    @Bean
    fun r2dbcDatabase(
        pool: ConnectionPool,
        options: ConnectionFactoryOptions,
    ): R2dbcDatabase {
        val config = R2dbcDatabaseConfig {
            dispatcher = kotlinx.coroutines.Dispatchers.IO
            connectionFactoryOptions = options
        }
        return R2dbcDatabase.connect(pool, config)
    }
}
