package exposed.r2dbc.workshop.springwebflux.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.testcontainers.database.MySQL8Server
import io.bluetape4k.testcontainers.database.PostgreSQLServer
import io.bluetape4k.utils.Runtimex
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.math.max

@Configuration
class ExposedR2dbcConfig {

    companion object: KLoggingChannel()

    /**
     * Java 21 가상 스레드 기반 코루틴 컨텍스트를 제공합니다.
     * 데이터베이스 I/O 작업에 최적화되어 있습니다.
     */
    @Bean
    fun databaseCoroutineDispatcher(): CoroutineDispatcher {
        return Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
    }

    /**
     * H2 인메모리 데이터베이스용 ConnectionFactory를 생성합니다.
     */
    @Bean
    @Profile("h2")
    fun h2ConnectionFactoryOptions(): ConnectionFactoryOptions {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PROTOCOL, "mem")
            .option(ConnectionFactoryOptions.DATABASE, "test")
            .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
            .option(Option.valueOf("DB_CLOSE_ON_EXIT"), "FALSE")
            .option(Option.valueOf("MODE"), "PostgreSQL")
            .build()

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

        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "mysql")
            .option(ConnectionFactoryOptions.HOST, mysql.host)
            .option(ConnectionFactoryOptions.PORT, mysql.port)
            .option(ConnectionFactoryOptions.USER, mysql.username ?: "test")
            .option(ConnectionFactoryOptions.PASSWORD, mysql.password ?: "test")
            .option(ConnectionFactoryOptions.SSL, false)
            // MySQL 전용 옵션 추가
            .option(Option.valueOf("useServerPrepareStatement"), true)
            .option(Option.valueOf("tcpKeepAlive"), true)
            .option(Option.valueOf("connectTimeout"), Duration.ofSeconds(10))
            .build()

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

        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, postgres.host)
            .option(ConnectionFactoryOptions.PORT, postgres.port)
            .option(ConnectionFactoryOptions.USER, postgres.username ?: "test")
            .option(ConnectionFactoryOptions.PASSWORD, postgres.password ?: "test")
            .option(ConnectionFactoryOptions.SSL, false)
            // PostgreSQL 전용 옵션 추가
            .option(Option.valueOf("lc_messages"), "ko_KR.UTF-8")
            .option(Option.valueOf("application_name"), "exposed-r2dbc-app")
            .option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 256)
            .option(PostgresqlConnectionFactoryProvider.STATEMENT_TIMEOUT, Duration.ofSeconds(30))
            .build()

        log.info { "PostgreSQL 연결 설정: ${options.toString().replace(Regex("password=.*?,"), "password=****,")}" }
        return options
    }

    /**
     * ConnectionFactory를 래핑하여 커넥션 풀을 구성합니다.
     * 모든 데이터베이스 유형에 공통으로 적용됩니다.
     */
    @Bean
    @Primary
    fun connectionPool(connectionFactoryOptions: ConnectionFactoryOptions): ConnectionPool {
        val connectionFactory = ConnectionFactories.get(connectionFactoryOptions)
        val poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
            .maxIdleTime(Duration.ofMinutes(30))
            .initialSize(5)
            .maxSize(max(Runtimex.availableProcessors * 2, 10))
            .acquireRetry(3)
            .maxAcquireTime(Duration.ofSeconds(5))
            .validationQuery("SELECT 1")
            .build()

        return ConnectionPool(poolConfig)
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
