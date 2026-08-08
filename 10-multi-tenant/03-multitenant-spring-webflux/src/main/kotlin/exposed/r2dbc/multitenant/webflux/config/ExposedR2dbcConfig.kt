package exposed.r2dbc.multitenant.webflux.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.bluetape4k.testcontainers.database.MySQL8Server
import io.bluetape4k.testcontainers.database.PostgreSQLServer
import io.bluetape4k.utils.Runtimex
import exposed.r2dbc.shared.config.R2dbcServerCredentials
import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions as sharedH2ConnectionFactoryOptions
import exposed.r2dbc.shared.config.mysqlConnectionFactoryOptions as sharedMysqlConnectionFactoryOptions
import exposed.r2dbc.shared.config.postgresConnectionFactoryOptions as sharedPostgresConnectionFactoryOptions
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.beans.factory.annotation.Value
import java.time.Duration

internal fun resolvePoolMaxSize(configuredMaxSize: Int): Int =
    if (configuredMaxSize > 0) configuredMaxSize
    else maxOf(Runtimex.availableProcessors * 2, 16)

@Configuration
class ExposedR2dbcConfig {

    companion object: KLoggingChannel()

    /**
     * Java 21 가상 스레드 기반 코루틴 컨텍스트를 제공합니다.
     * 데이터베이스 I/O 작업에 최적화되어 있습니다.
     */
    @Bean(destroyMethod = "")
    fun databaseCoroutineDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO // 기본적으로 IO 디스패처를 사용합니다.
        // return Dispatchers.VT // Java 21 가상 스레드 사용
        // return Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
    }

    /**
     * H2 인메모리 데이터베이스용 ConnectionFactory를 생성합니다.
     */
    @Bean
    @Profile("h2")
    fun h2ConnectionFactoryOptions(): ConnectionFactoryOptions {
        val options = sharedH2ConnectionFactoryOptions()

        log.info { "H2 연결 설정: ${options.toString().replace(Regex("password=.*?,"), "password=****,")}" }
        return options
    }

    /**
     * MySQL 데이터베이스용 ConnectionFactory를 생성합니다.
     */
    @Bean
    @Profile("mysql")
    fun mysqlConnectionFactoryOptions(): ConnectionFactoryOptions {
        val mysql = MySQL8Server.Launcher.mysql

        val options = sharedMysqlConnectionFactoryOptions(
            R2dbcServerCredentials(
                host = mysql.host,
                port = mysql.port,
                user = mysql.username ?: "test",
                password = mysql.password ?: "test",
            ),
        )

        log.info { "MySQL 연결 설정: ${options.toString().replace(Regex("password=.*?,"), "password=****,")}" }
        return options
    }

    /**
     * PostgreSQL 데이터베이스용 ConnectionFactory를 생성합니다.
     */
    @Bean
    @Profile("postgres")
    fun postgresConnectionFactoryOptions(): ConnectionFactoryOptions {
        val postgres = PostgreSQLServer.Launcher.postgres

        val options = sharedPostgresConnectionFactoryOptions(
            R2dbcServerCredentials(
                host = postgres.host,
                port = postgres.port,
                user = postgres.username ?: "test",
                password = postgres.password ?: "test",
            ),
        )

        log.info { "PostgreSQL 연결 설정: ${options.toString().replace(Regex("password=.*?,"), "password=****,")}" }
        return options
    }

    /**
     * ConnectionFactory를 래핑하여 커넥션 풀을 구성합니다.
     * 모든 데이터베이스 유형에 공통으로 적용됩니다.
     */
    @Bean
    @Primary
    fun connectionPool(
        connectionFactoryOptions: ConnectionFactoryOptions,
        @Value("\${app.r2dbc.pool.max-size:0}") configuredMaxSize: Int,
    ): ConnectionPool {
        return connectionPoolOf(connectionFactoryOptions) {
            maxIdleTime = Duration.ofMinutes(10)
            maxLifeTime = Duration.ofMinutes(30)
            maxCreateConnectionTime = Duration.ofSeconds(10)
            maxSize = resolvePoolMaxSize(configuredMaxSize)
            initialSize = 2
            minIdle = 2
            acquireRetry = 3
            backgroundEvictionInterval = Duration.ofMinutes(1)
            maxAcquireTime = Duration.ofSeconds(3)
        }
    }

    /**
     * ConnectionPool을 사용하여 Exposed R2DBC 데이터베이스 객체를 생성합니다.
     */
    @Bean
    fun r2dbcDatabase(
        connectionPool: ConnectionPool,
        connectionFactoryOptions: ConnectionFactoryOptions,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): R2dbcDatabase {
        val config = R2dbcDatabaseConfig {
            this.dispatcher = databaseCoroutineDispatcher
            this.connectionFactoryOptions = connectionFactoryOptions
        }

        log.info { "R2DBC Database 설정 완료 (connectionPool 기반)" }
        return R2dbcDatabase.connect(connectionPool, config)
    }
}
