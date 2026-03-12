package exposed.r2dbc.shared.tests

import io.bluetape4k.logging.KLogging
import io.r2dbc.spi.IsolationLevel
import org.h2.engine.Mode
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

/**
 * Postgres, MySQL 등의 서버를 Testconstainers 를 이용할 것인가? 직접 설치한 서버를 사용할 것인가?
 */
const val USE_TESTCONTAINERS = true

/**
 * true 이면, InMemory DB만을 대상으로 테스트 합니다.
 * false 이면 Postgres, MySQL V8 도 포함해서 테스트 합니다.
 *
 * Gradle 실행 시 `-PuseFastDB=true` 로 지정하면 H2 계열만 테스트합니다.
 * 예: `./gradlew test -PuseFastDB=true`
 */
val useFastDB: Boolean = System.getProperty("exposed.test.useFastDB", "false").toBoolean()

/**
 * Exposed R2DBC 기능을 테스트하기 위한 대상 데이터베이스 목록 및 연결 정보를 제공합니다.
 *
 * 각 항목은 R2DBC URL 생성 람다, 드라이버 클래스명, 인증 정보, 연결 전/후 훅,
 * [org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig] 커스터마이징을 포함합니다.
 *
 * 지원하는 데이터베이스:
 * - [H2], [H2_MYSQL], [H2_PSQL], [H2_MARIADB], [H2_ORACLE], [H2_SQLSERVER]: H2 인메모리 (각 호환 모드)
 * - [MARIADB]: MariaDB (Testcontainers 또는 외부 서버)
 * - [MYSQL_V5], [MYSQL_V8]: MySQL 5/8 (Testcontainers 또는 외부 서버)
 * - [POSTGRESQL]: PostgreSQL (Testcontainers 또는 외부 서버)
 *
 * @see enabledDialects
 * @see connect
 */
enum class TestDB(
    val connection: () -> String,
    val driver: String,
    val user: String = "test",
    val pass: String = "test",
    val beforeConnection: suspend () -> Unit = {},
    // val afterConnection: (connection: Connection) -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: R2dbcDatabaseConfig.Builder.() -> Unit = {},
) {
    /**
     * H2 v2.x 인메모리 DB (기본 모드).
     *
     * Docker 불필요. 격리 수준: READ_COMMITTED.
     * R2DBC URL: `r2dbc:h2:mem:///regular;DB_CLOSE_DELAY=-1;`
     */
    H2(
        connection = { "r2dbc:h2:mem:///regular;DB_CLOSE_DELAY=-1;" },
        driver = "org.h2.Driver",
        dbConfig = {
            defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
        }
    ),

    /**
     * H2 인메모리 DB — MySQL 호환 모드.
     *
     * Docker 불필요. MySQL 문법 및 동작을 에뮬레이션합니다.
     * `convertInsertNullToZero` 플래그를 `false`로 설정하여 NULL 처리를 MySQL과 동일하게 맞춥니다.
     * R2DBC URL: `r2dbc:h2:mem:///mysql;DB_CLOSE_DELAY=-1;MODE=MySQL;`
     */
    H2_MYSQL(
        connection = { "r2dbc:h2:mem:///mysql;DB_CLOSE_DELAY=-1;MODE=MySQL;" },
        driver = "org.h2.Driver",
        beforeConnection = {
            Mode::class.declaredMemberProperties
                .firstOrNull { it.name == "convertInsertNullToZero" }
                ?.let { field ->
                    val mode = Mode.getInstance("MySQL")
                    @Suppress("UNCHECKED_CAST")
                    (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
                }
        }
    ),

    /**
     * H2 인메모리 DB — MariaDB 호환 모드.
     *
     * Docker 불필요. `DATABASE_TO_LOWER=TRUE`로 식별자 소문자 처리.
     * R2DBC URL: `r2dbc:h2:mem:///mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;`
     */
    H2_MARIADB(
        connection = {
            "r2dbc:h2:mem:///mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;"
        },
        driver = "org.h2.Driver",
    ),

    /**
     * H2 인메모리 DB — PostgreSQL 호환 모드.
     *
     * Docker 불필요. `DEFAULT_NULL_ORDERING=HIGH`로 NULL 정렬을 PostgreSQL 기본값과 맞춥니다.
     * R2DBC URL: `r2dbc:h2:mem:///psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;`
     */
    H2_PSQL(
        connection = {
            "r2dbc:h2:mem:///psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;"
        },
        driver = "org.h2.Driver"
    ),

    /**
     * H2 인메모리 DB — Oracle 호환 모드.
     *
     * Docker 불필요. Oracle 문법 에뮬레이션. 실제 Oracle Driver 없이 Oracle 문법 테스트 가능.
     * R2DBC URL: `r2dbc:h2:mem:///oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;`
     */
    H2_ORACLE(
        connection = {
            "r2dbc:h2:mem:///oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;"
        },
        driver = "org.h2.Driver"
    ),

    /**
     * H2 인메모리 DB — MS SQL Server 호환 모드.
     *
     * Docker 불필요. MSSQL 문법 에뮬레이션.
     * R2DBC URL: `r2dbc:h2:mem:///sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;`
     */
    H2_SQLSERVER(
        connection = { "r2dbc:h2:mem:///sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;" },
        driver = "org.h2.Driver"
    ),

    /**
     * MariaDB 서버 연결 (Testcontainers 또는 외부 서버).
     *
     * `USE_TESTCONTAINERS=true`이면 Docker로 MariaDB 컨테이너를 자동 기동합니다.
     * UTF-8, UTC, NULL zeroDateTime 처리, 배치 처리 옵션이 포함됩니다.
     * R2DBC URL (Testcontainers): `r2dbc:mariadb://test:test@127.0.0.1:<port>/<db>?...`
     */
    MARIADB(
        connection = {
            val options = "?useSSL=false" +
                    "&characterEncoding=UTF-8" +
                    "&useLegacyDatetimeCode=false&serverTimezone=UTC" +  // TimeZone 을 UTC 로 설정
                    "&zeroDateTimeBehavior=convertToNull" +
                    "&rewriteBatchedStatements=true"   // Batch 처리를 위한 설정

            if (USE_TESTCONTAINERS) {
                val port = Containers.MariaDB.port
                val databaseName = Containers.MariaDB.databaseName
                "r2dbc:mariadb://${MARIADB.user}:${MARIADB.pass}@127.0.0.1:$port/$databaseName$options"
            } else {
                "r2dbc:mariadb://localhost:3306/exposed$options"
            }
        },
        driver = "org.mariadb.jdbc.Driver",
    ),

    /**
     * MySQL 5.x 서버 연결 (Testcontainers 또는 외부 서버).
     *
     * `USE_TESTCONTAINERS=true`이면 Docker로 MySQL 5 컨테이너를 자동 기동합니다.
     * R2DBC URL (Testcontainers): `r2dbc:mysql://test:test@127.0.0.1:<port>/<db>?...`
     */
    MYSQL_V5(
        connection = {
            val options = "?useSSL=false" +
                    "&characterEncoding=UTF-8" +
                    "&useLegacyDatetimeCode=false" +
                    "&serverTimezone=UTC" +  // TimeZone 을 UTC 로 설정
                    "&zeroDateTimeBehavior=convertToNull" +
                    "&rewriteBatchedStatements=true"   // Batch 처리를 위한 설정

            if (USE_TESTCONTAINERS) {
                val port = Containers.MySql5.port
                val databaseName = Containers.MariaDB.databaseName
                "r2dbc:mysql://${MYSQL_V5.user}:${MYSQL_V5.pass}@127.0.0.1:$port/$databaseName$options"
            } else {
                "r2dbc:mysql://localhost:3306/exposed$options"
            }
        },
        driver = "com.mysql.cj.jdbc.Driver",
    ),

    /**
     * MySQL 8.x 서버 연결 (Testcontainers 또는 외부 서버).
     *
     * `USE_TESTCONTAINERS=true`이면 Docker로 MySQL 8 컨테이너를 자동 기동합니다.
     * `allowPublicKeyRetrieval=true` 옵션이 포함됩니다.
     * 기본 활성 DB 중 하나로 포함됩니다 (`enabledDialects()` 기본값).
     * R2DBC URL (Testcontainers): `r2dbc:mysql://test:test@127.0.0.1:<port>/<db>?...`
     */
    MYSQL_V8(
        connection = {
            val options = "?useSSL=false" +
                    "&characterEncoding=UTF-8" +
                    "&zeroDateTimeBehavior=convertToNull" +
                    "&useLegacyDatetimeCode=false" +
                    "&serverTimezone=UTC" +  // TimeZone 을 UTC 로 설정
                    "&allowPublicKeyRetrieval=true" +
                    "&rewriteBatchedStatements=true"   // Batch 처리를 위한 설정

            if (USE_TESTCONTAINERS) {
                val port = Containers.MySql8.port
                val databaseName = Containers.MariaDB.databaseName
                "r2dbc:mysql://${MYSQL_V8.user}:${MYSQL_V8.pass}@127.0.0.1:$port/$databaseName$options"
            } else {
                "r2dbc:mysql://localhost:3306/exposed$options"
            }
        },
        driver = "com.mysql.cj.jdbc.Driver",
        user = if (USE_TESTCONTAINERS) "test" else "exposed",
        pass = if (USE_TESTCONTAINERS) "test" else "@exposed2025",
    ),

    /**
     * PostgreSQL 서버 연결 (Testcontainers 또는 외부 서버).
     *
     * `USE_TESTCONTAINERS=true`이면 Docker로 PostgreSQL 컨테이너를 자동 기동합니다.
     * 기본 활성 DB 중 하나로 포함됩니다 (`enabledDialects()` 기본값).
     * R2DBC URL (Testcontainers): `r2dbc:postgresql://test:test@127.0.0.1:<port>/postgres?lc_messages=en_US.UTF-8`
     */
    POSTGRESQL(
        connection = {
            val options = "?lc_messages=en_US.UTF-8"
            if (USE_TESTCONTAINERS) {
                val port = Containers.Postgres.port
                "r2dbc:postgresql://${POSTGRESQL.user}:${POSTGRESQL.pass}@127.0.0.1:$port/postgres$options"
            } else {
                "r2dbc:postgresql://localhost:5432/exposed$options"
            }
        },
        driver = "org.postgresql.Driver",
        user = if (USE_TESTCONTAINERS) "test" else "exposed",
//        afterConnection = { connection ->
//            connection.createStatement().use { stmt ->
//                stmt.execute("SET TIMEZONE='UTC';")
//            }
//        }
    );

    /** 이 [TestDB]에 해당하는 [R2dbcDatabase] 인스턴스. 최초 연결 시 설정됩니다. */
    var db: R2dbcDatabase? = null

    /**
     * 이 [TestDB]에 대한 [R2dbcDatabase] 연결을 생성하고 반환합니다.
     *
     * @param configure 추가 데이터베이스 구성 커스터마이징 람다
     * @return 생성된 [R2dbcDatabase] 인스턴스
     */
    fun connect(configure: R2dbcDatabaseConfig.Builder.() -> Unit = {}): R2dbcDatabase {
        val config = R2dbcDatabaseConfig {
            dbConfig()
            configure()

            setUrl(connection())
        }
        return R2dbcDatabase.connect(databaseConfig = config)
    }

    companion object: KLogging() {
        val ALL_H2 = setOf(H2, H2_MYSQL, H2_PSQL, H2_MARIADB /*H2_ORACLE, H2_SQLSERVER*/)
        val ALL_MARIADB = setOf(MARIADB)
        val ALL_MARIADB_LIKE = setOf(MARIADB, H2_MARIADB)
        val ALL_MYSQL = setOf(MYSQL_V5, MYSQL_V8)
        val ALL_MYSQL_MARIADB = ALL_MYSQL + ALL_MARIADB
        val ALL_MYSQL_LIKE = ALL_MYSQL + H2_MYSQL
        val ALL_MYSQL_MARIADB_LIKE = ALL_MYSQL_LIKE + ALL_MARIADB_LIKE
        val ALL_POSTGRES = setOf(POSTGRESQL)
        val ALL_POSTGRES_LIKE = setOf(POSTGRESQL, H2_PSQL)
        val ALL_ORACLE_LIKE = setOf(H2_ORACLE)
        val ALL_SQLSERVER_LIKE = setOf(H2_SQLSERVER)

        val ALL = TestDB.entries.toSet()

        /**
         * 테스트 대상 DB 목록을 반환합니다.
         *
         * 우선순위:
         * 1. `-PuseDB=H2,POSTGRESQL,...` 로 명시적 지정 시 해당 DB만 테스트
         * 2. `-PuseFastDB=true` 이면 H2 계열만 테스트
         * 3. 기본값: H2, POSTGRESQL, MYSQL_V8
         *
         * 예:
         * - `./gradlew test -PuseDB=H2,POSTGRESQL`
         * - `./gradlew test -PuseFastDB=true`
         */
        fun enabledDialects(): Set<TestDB> {
            val useDB = System.getProperty("exposed.test.useDB")
            if (!useDB.isNullOrBlank()) {
                return useDB.split(",")
                    .map { it.trim() }
                    .mapNotNull { name -> entries.find { it.name.equals(name, true) } }
                    .toSet()
                    .ifEmpty { setOf(H2) }
            }

            return if (useFastDB) setOf(H2)
            else setOf(H2, POSTGRESQL, MYSQL_V8)
        }
    }
}
