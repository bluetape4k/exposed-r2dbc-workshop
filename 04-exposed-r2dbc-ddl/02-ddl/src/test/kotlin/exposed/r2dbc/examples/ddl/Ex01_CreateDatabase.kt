package exposed.r2dbc.examples.ddl

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withAutoCommit
import exposed.r2dbc.shared.tests.withDb
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class Ex01_CreateDatabase: R2dbcExposedTestBase() {

    companion object: KLoggingChannel() {
        private const val DB_NAME = "bluetape4k"
    }

    /**
     * 데이터베이스 생성 및 삭제
     *
     * ```sql
     * CREATE DATABASE bluetape4k;
     * DROP DATABASE bluetape4k;
     * ```
     *
     * @see [SchemaUtils.createDatabase]
     * @see [SchemaUtils.dropDatabase]
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `데이터베이스 생성과 삭제 작업`(testDB: TestDB) = runTest {
        // MySQL은 계정 권한이 있어야 해서, 테스트가 불가능합니다.
        Assumptions.assumeTrue { testDB in TestDB.ALL_H2 + TestDB.ALL_POSTGRES }

        withDb(testDB) {
            withAutoCommit(true) {
                val dbName = DB_NAME
                // 데이터베이스가 존재하면 삭제 (예외가 발생해도 무시)
                runCatching { SchemaUtils.dropDatabase(dbName) }

                // 데이터베이스 생성
                SchemaUtils.createDatabase(dbName)

                // 데이터베이스 삭제
                SchemaUtils.dropDatabase(dbName)
            }
        }
    }

    /**
     * 데이터베이스 목록 조회하기 (Postgres)
     *
     * ```sql
     * -- Postgres
     * SELECT datname FROM pg_database;
     *
     * CREATE DATABASE bluetape4k;
     * DROP DATABASE bluetape4k;
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `데이터베이스 목록 조회하기`(testDB: TestDB) = runTest {
        // MySQL은 계정 권한이 있어야 해서, 테스트가 불가능합니다.
        Assumptions.assumeTrue { testDB in TestDB.ALL_H2 + TestDB.ALL_POSTGRES }

        withDb(testDB) {
            val dbName = DB_NAME
            val originalDatabases = SchemaUtils.listDatabases()
            originalDatabases.forEach {
                log.debug { "database name: $it" }
            }

            if (dbName in originalDatabases) {
                SchemaUtils.dropDatabase(dbName)
            }

            // Postgres 는 autoCommit 이 true 여야 DB 생성, 삭제가 가능합니다.
            withAutoCommit(true) {
                SchemaUtils.createDatabase(dbName)
                SchemaUtils.listDatabases() shouldContain dbName

                SchemaUtils.dropDatabase(dbName)
                SchemaUtils.listDatabases() shouldNotContain dbName
            }
        }
    }
}
