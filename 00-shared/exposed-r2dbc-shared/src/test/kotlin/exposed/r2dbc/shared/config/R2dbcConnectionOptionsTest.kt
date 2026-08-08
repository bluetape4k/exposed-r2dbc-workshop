package exposed.r2dbc.shared.config

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration

class R2dbcConnectionOptionsTest {

    @Test
    fun `H2 옵션은 인메모리 데이터베이스와 종료 옵션을 포함한다`() {
        val options = h2ConnectionFactoryOptions(database = "options-test")

        assertEquals("h2", options.getRequiredValue(ConnectionFactoryOptions.DRIVER))
        assertEquals("mem", options.getRequiredValue(ConnectionFactoryOptions.PROTOCOL))
        assertEquals("options-test", options.getRequiredValue(ConnectionFactoryOptions.DATABASE))
        assertEquals("-1", options.getRequiredValue(Option.valueOf<String>("DB_CLOSE_DELAY")))
        assertEquals("FALSE", options.getRequiredValue(Option.valueOf<String>("DB_CLOSE_ON_EXIT")))
    }

    @Test
    fun `MySQL 옵션은 자격 증명과 Bluetape 연결 옵션을 포함한다`() {
        val options = mysqlConnectionFactoryOptions(
            R2dbcServerCredentials("mysql.example", 3306, "workshop", "secret"),
        )

        assertEquals("mysql", options.getRequiredValue(ConnectionFactoryOptions.DRIVER))
        assertEquals("mysql.example", options.getRequiredValue(ConnectionFactoryOptions.HOST))
        assertEquals(3306, options.getRequiredValue(ConnectionFactoryOptions.PORT))
        assertEquals("workshop", options.getRequiredValue(ConnectionFactoryOptions.USER))
        assertEquals("secret", options.getRequiredValue(ConnectionFactoryOptions.PASSWORD))
        assertFalse(options.getRequiredValue(ConnectionFactoryOptions.SSL) as Boolean)
        assertEquals(
            true,
            options.getRequiredValue(Option.valueOf<Boolean>("useServerPrepareStatement")),
        )
        assertEquals(
            Duration.ofSeconds(10),
            options.getRequiredValue(ConnectionFactoryOptions.CONNECT_TIMEOUT),
        )
    }

    @Test
    fun `PostgreSQL 옵션은 prepared statement와 statement timeout을 포함한다`() {
        val options = postgresConnectionFactoryOptions(
            R2dbcServerCredentials("postgres.example", 5432, "workshop", "secret"),
        )

        assertEquals("postgresql", options.getRequiredValue(ConnectionFactoryOptions.DRIVER))
        assertEquals("postgres.example", options.getRequiredValue(ConnectionFactoryOptions.HOST))
        assertEquals(5432, options.getRequiredValue(ConnectionFactoryOptions.PORT))
        assertFalse(options.getRequiredValue(ConnectionFactoryOptions.SSL) as Boolean)
        assertEquals("ko_KR.UTF-8", options.getRequiredValue(Option.valueOf<String>("lc_messages")))
        assertEquals("exposed-r2dbc-app", options.getRequiredValue(Option.valueOf<String>("application_name")))
        assertEquals(
            256,
            options.getRequiredValue(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES),
        )
        assertEquals(
            Duration.ofSeconds(30),
            options.getRequiredValue(ConnectionFactoryOptions.STATEMENT_TIMEOUT),
        )
    }
}
