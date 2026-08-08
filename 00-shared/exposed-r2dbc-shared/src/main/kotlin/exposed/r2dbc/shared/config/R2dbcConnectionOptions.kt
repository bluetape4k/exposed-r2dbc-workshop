package exposed.r2dbc.shared.config

import io.bluetape4k.r2dbc.pool.connectionFactoryOptionsOf
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import java.time.Duration

/**
 * Testcontainers 또는 외부 R2DBC 서버에 연결할 때 사용하는 공통 접속 정보입니다.
 */
data class R2dbcServerCredentials(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

/**
 * H2 인메모리 데이터베이스용 [ConnectionFactoryOptions]를 생성합니다.
 *
 * @param database 데이터베이스 이름
 * @param closeOnExit JVM 종료 시 H2 데이터베이스를 닫을지 여부
 */
fun h2ConnectionFactoryOptions(
    database: String = "test",
    closeOnExit: Boolean = true,
): ConnectionFactoryOptions = connectionFactoryOptionsOf {
    driver = "h2"
    protocol = "mem"
    this.database = database
    option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
    if (closeOnExit) {
        option(Option.valueOf("DB_CLOSE_ON_EXIT"), "FALSE")
    }
}

/**
 * MySQL용 [ConnectionFactoryOptions]를 생성합니다.
 *
 * @param credentials 서버 접속 정보
 * @param database 선택적인 데이터베이스 이름
 */
fun mysqlConnectionFactoryOptions(
    credentials: R2dbcServerCredentials,
    database: String? = null,
): ConnectionFactoryOptions = connectionFactoryOptionsOf {
    driver = "mysql"
    host = credentials.host
    port = credentials.port
    user = credentials.user
    password = credentials.password
    ssl = false
    connectTimeout = Duration.ofSeconds(10)
    database?.let { this.database = it }
    option(Option.valueOf("useServerPrepareStatement"), true)
    option(Option.valueOf("tcpKeepAlive"), true)
}

/**
 * PostgreSQL용 [ConnectionFactoryOptions]를 생성합니다.
 *
 * @param credentials 서버 접속 정보
 * @param database 선택적인 데이터베이스 이름
 */
fun postgresConnectionFactoryOptions(
    credentials: R2dbcServerCredentials,
    database: String? = null,
): ConnectionFactoryOptions = connectionFactoryOptionsOf {
    driver = "postgresql"
    host = credentials.host
    port = credentials.port
    user = credentials.user
    password = credentials.password
    ssl = false
    database?.let { this.database = it }
    option(Option.valueOf("lc_messages"), "ko_KR.UTF-8")
    option(Option.valueOf("application_name"), "exposed-r2dbc-app")
    option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 256)
    statementTimeout = Duration.ofSeconds(30)
}
