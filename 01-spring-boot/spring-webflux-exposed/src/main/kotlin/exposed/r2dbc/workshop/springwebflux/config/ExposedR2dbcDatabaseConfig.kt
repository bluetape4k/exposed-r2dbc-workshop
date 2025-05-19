package exposed.r2dbc.workshop.springwebflux.config

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.testcontainers.database.MySQL8Server
import io.bluetape4k.testcontainers.database.PostgreSQLServer
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class ExposedR2dbcDatabaseConfig {

    companion object: KLogging()

    @Bean
    @Profile("h2")
    fun h2ConnectionFactory(): ConnectionFactory {
        log.info { "H2 Database Configuration" }

        val config = H2ConnectionConfiguration.builder()
            .url("r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL")
            .username("sa")
            .password("")
            .build()

        return H2ConnectionFactory(config)
    }

    @Bean
    @Profile("mysql")
    fun mySqlConnectionFactory(): ConnectionFactory {
        log.info { "MySQL Database Configuration" }

        val mysql = MySQL8Server.Launcher.mysql

        val config = MySqlConnectionConfiguration.builder()
            .host(mysql.host)
            .port(mysql.port)
            .database(mysql.databaseName)
            .username(mysql.username)
            .password(mysql.password)
            .build()

        return MySqlConnectionFactory.from(config)
    }

    @Bean
    @Profile("postgres")
    fun postgresConnectionFactory(): ConnectionFactory {
        log.info { "PostgreSQL Database Configuration" }

        val postgres = PostgreSQLServer.Launcher.postgres

        val config = PostgresqlConnectionConfiguration.builder()
            .host(postgres.host)
            .port(postgres.port)
            .database(postgres.databaseName)
            .username(postgres.username ?: "test")
            .password(postgres.password)
            .build()

        return PostgresqlConnectionFactory(config)
    }

    @Bean
    fun r2dbcDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase {
//        log.info { "Create R2dbcDatabase. connectionFactory=$connectionFactory" }
//        return R2dbcDatabase.connect(connectionFactory)

        val config = R2dbcDatabaseConfig {
            setUrl("r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL")
        }
        return R2dbcDatabase.connect(config)
    }
}
