package exposed.r2dbc.examples.springbootrepository.config

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

/**
 * H2 데모의 ConnectionFactoryOptions, bounded pool, Exposed database를 애플리케이션이 소유하도록 구성합니다.
 */
@Configuration(proxyBeanMethods = false)
class ExposedR2dbcConfig {

    companion object : KLoggingChannel()

    /**
     * R2DBC I/O에 사용할 dispatcher를 제공합니다.
     */
    @Bean(destroyMethod = "")
    fun databaseCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * 공유 테스트 fixture와 동일한 H2 `regular` 인메모리 데이터베이스 옵션을 만듭니다.
     */
    @Bean
    @Profile("h2")
    fun h2ConnectionFactoryOptions(): ConnectionFactoryOptions =
        h2ConnectionFactoryOptions(database = "regular", closeOnExit = false).also {
            log.info { "r2dbc_options_configured ${it.sanitizedSummary()}" }
        }

    /**
     * 애플리케이션 수명 동안 사용할 bounded ConnectionPool을 만듭니다.
     *
     * Spring의 암시적 `destroyMethod` 대신 [ConnectionPoolLifecycle]이 단일 close 경계를 담당합니다.
     */
    @Bean(destroyMethod = "")
    fun connectionPool(connectionFactoryOptions: ConnectionFactoryOptions): ConnectionPool =
        connectionPoolOf(connectionFactoryOptions) {
            maxSize = 8
            maxCreateConnectionTime = Duration.ofSeconds(10)
            maxAcquireTime = Duration.ofSeconds(3)
            maxIdleTime = Duration.ofMinutes(10)
            maxLifeTime = Duration.ofMinutes(30)
            acquireRetry = 0
        }

    /**
     * 애플리케이션 소유 pool을 Exposed R2DBC database로 연결합니다.
     */
    @Bean
    fun r2dbcDatabase(
        connectionPool: ConnectionPool,
        connectionFactoryOptions: ConnectionFactoryOptions,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): R2dbcDatabase {
        val config = R2dbcDatabaseConfig {
            dispatcher = databaseCoroutineDispatcher
            this.connectionFactoryOptions = connectionFactoryOptions
        }

        return R2dbcDatabase.connect(connectionPool, config)
    }

    /**
     * Spring context close 시 Exposed manager를 unregister한 다음 애플리케이션 소유
     * pool을 한 번만 닫는 lifecycle 경계를 등록합니다.
     */
    @Bean
    fun connectionPoolLifecycle(
        connectionPool: ConnectionPool,
        database: R2dbcDatabase,
    ): ConnectionPoolLifecycle = ConnectionPoolLifecycle(database, connectionPool)
}
