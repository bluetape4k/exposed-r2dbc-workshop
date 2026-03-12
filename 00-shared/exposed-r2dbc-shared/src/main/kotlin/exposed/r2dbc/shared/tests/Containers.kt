package exposed.r2dbc.shared.tests

import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.database.MariaDBServer
import io.bluetape4k.testcontainers.database.MySQL5Server
import io.bluetape4k.testcontainers.database.MySQL8Server
import io.bluetape4k.testcontainers.database.PostgreSQLServer
import io.bluetape4k.utils.ShutdownQueue

/**
 * Testcontainers 기반 데이터베이스 컨테이너 싱글턴 객체.
 *
 * 각 데이터베이스(MariaDB, MySQL 5/8, PostgreSQL)에 대한 Docker 컨테이너를 지연 초기화로 관리합니다.
 * 첫 접근 시 컨테이너를 시작하고, JVM 종료 시 [ShutdownQueue]를 통해 자동 정리됩니다.
 *
 * 모든 컨테이너는 `utf8mb4` 문자 인코딩 및 `utf8mb4_bin` 콜레이션으로 구성되어
 * 한글 등 멀티바이트 문자를 올바르게 처리합니다.
 *
 * 사용 예:
 * ```kotlin
 * // TestDB enum에서 자동으로 참조됩니다.
 * val port = Containers.MariaDB.port
 * val dbName = Containers.MariaDB.databaseName
 * ```
 *
 * @see TestDB
 * @see ShutdownQueue
 */
object Containers: KLogging() {

    /** MariaDB Testcontainer 인스턴스. utf8mb4 인코딩으로 구성됩니다. */
    val MariaDB: MariaDBServer by lazy {
        MariaDBServer()
            .apply {
                withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_bin"
                )
                start()
                ShutdownQueue.register(this)
            }
    }

    /** MySQL 5 Testcontainer 인스턴스. utf8mb4 인코딩으로 구성됩니다. */
    val MySql5: MySQL5Server by lazy {
        MySQL5Server()
            .apply {
                withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_bin"
                )
                start()
                ShutdownQueue.register(this)
            }
    }

    /** MySQL 8 Testcontainer 인스턴스. utf8mb4 인코딩으로 구성됩니다. */
    val MySql8: MySQL8Server by lazy {
        MySQL8Server()
            .apply {
                withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_bin"
                )
                start()
                ShutdownQueue.register(this)
            }

    }

    /** PostgreSQL Testcontainer 인스턴스. [PostgreSQLServer.Launcher]의 공유 인스턴스를 사용합니다. */
    val Postgres: PostgreSQLServer by lazy { PostgreSQLServer.Launcher.postgres }
}
