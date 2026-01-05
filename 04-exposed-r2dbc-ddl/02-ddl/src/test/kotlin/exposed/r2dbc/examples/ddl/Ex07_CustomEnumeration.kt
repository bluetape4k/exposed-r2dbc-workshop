package exposed.r2dbc.examples.ddl

import exposed.r2dbc.shared.tests.ContainerProvider
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.codec.EnumCodec
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 사용자 정의 Enum 수형을 사용하는 방법인데,
 * JPA의  `@Enumerated(EnumType.STRING)` 과 같은 방식으로 사용하시던 분들은
 * Exposed의 column transformation 기능을 사용하는 것을 추천합니다.
 */
class Ex07_CustomEnumeration: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    private val supportsCustomEnumerationDB =
        TestDB.ALL_POSTGRES + TestDB.ALL_MYSQL_MARIADB

    internal enum class Status {
        ACTIVE,
        INACTIVE;

        override fun toString(): String = "Status: $name"
    }

    @Suppress("UnusedPrivateProperty")
    private val pgOptionsWithEnumCodec by lazy {
        PostgresqlConnectionConfiguration.builder()
            .host("127.0.0.1")
            .port(ContainerProvider.postgres.port)
            .username(TestDB.POSTGRESQL.user)
            .password(TestDB.POSTGRESQL.pass)
            .database("postgres")
            .options(mapOf("lc_messages" to "en_US.UTF-8"))
            .codecRegistrar(EnumCodec.builder().withEnum("StatusEnum", Status::class.java).build())
            .build()
    }

    /**
     * 컬럼 수형으로 Custom Enum 수형을 사용하는 경우
     *
     * ```sql
     * -- PostgreSQL
     * DROP TYPE IF EXISTS StatusEnum;
     * CREATE TYPE StatusEnum AS ENUM ('ACTIVE', 'INACTIVE');
     *
     * CREATE TABLE IF NOT EXISTS enum_table (
     *      id SERIAL PRIMARY KEY,
     *      status StatusEnum NOT NULL
     * );
     * ```
     */
    internal object EnumTable: IntIdTable("enum_table") {
        var status: Column<Status> = enumeration<Status>("status")

        internal fun initEnumColumn(sql: String) {
            (columns as MutableList<Column<*>>).remove(status)
            status = customEnumeration(
                name = "status",
                sql = sql,
                fromDb = { value -> Status.valueOf(value as String) },
                toDb = { value ->
                    when (currentDialect) {
                        is PostgreSQLDialect -> value
                        else -> value.name
                    }
                }
            )
        }
    }

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS tester (
     *      id SERIAL PRIMARY KEY,
     *      status_name VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL
     * )
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `Enum Name 으로 저장하기`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("tester") {
            var statusName = enumerationByName<Status>("status_name", 32).default(Status.ACTIVE)
        }
        withTables(testDB, tester) {
            // INSERT INTO tester (status_name) VALUES ('ACTIVE')
            tester.insert {
                it[statusName] = Status.ACTIVE
            }
            tester.selectAll().single()[tester.statusName] shouldBeEqualTo Status.ACTIVE

            // UPDATE tester SET status_name='INACTIVE' WHERE tester.id = 1
            tester.update({ tester.id eq 1 }) {
                it[statusName] = Status.INACTIVE
            }
            tester.selectAll().single()[tester.statusName] shouldBeEqualTo Status.INACTIVE

            tester.deleteAll()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `사용자 정의 Enum 수형의 컬럼 정의 - 01`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in supportsCustomEnumerationDB && testDB !in TestDB.ALL_POSTGRES }

        withDb(testDB) {
            val sqlType = when (currentDialect) {
                is PostgreSQLDialect -> "StatusEnum"
                is MysqlDialect -> "ENUM('ACTIVE', 'INACTIVE')"
                else -> error("Unsupported dialect: $currentDialect")
            }
            try {
                if (currentDialect is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS StatusEnum")
                    exec("CREATE TYPE StatusEnum AS ENUM ('ACTIVE', 'INACTIVE')")
                }
                EnumTable.initEnumColumn(sqlType)
                SchemaUtils.create(EnumTable)

                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }

                // enumColumn = ACTIVE 로 설정
                // INSERT INTO enum_table (status) VALUES ('ACTIVE')
                EnumTable.insert {
                    it[status] = Status.ACTIVE
                }
                EnumTable.selectAll().single()[EnumTable.status] shouldBeEqualTo Status.ACTIVE

                // enumColumn = INACTIVE 로 설정
                // UPDATE enum_table SET status='INACTIVE'
                EnumTable.update {
                    it[status] = Status.INACTIVE
                }
                EnumTable.selectAll().single()[EnumTable.status] shouldBeEqualTo Status.INACTIVE

                EnumTable.deleteAll()
            } finally {
                SchemaUtils.drop(EnumTable)
                if (currentDialect is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS StatusEnum")
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `custom enumeration with reference`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in supportsCustomEnumerationDB && testDB !in TestDB.ALL_POSTGRES }

        /**
         * ```sql
         * CREATE TABLE IF NOT EXISTS TESTER (
         *      ENUM_COLUMN INT NOT NULL,
         *      ENUM_NAME_COLUMN VARCHAR(32) NOT NULL
         * )
         * ```
         */
        val referenceTable = object: Table("ref_table") {
            var referenceColumn: Column<Status> = enumeration<Status>("ref_column")

            fun initRefColumn() {
                (columns as MutableList<Column<*>>).remove(referenceColumn)
                referenceColumn = reference("ref_column", EnumTable.status)
            }
        }

        withDb(testDB) {
            val sqlType = when (currentDialect) {
                is PostgreSQLDialect -> "StatusEnum"
                is MysqlDialect -> "ENUM('ACTIVE', 'INACTIVE')"
                else -> error("Unsupported case. dialect: $currentDialect")
            }

            try {
                if (currentDialect is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS StatusEnum;")
                    exec("CREATE TYPE StatusEnum AS ENUM ('ACTIVE', 'INACTIVE');")
                }
                EnumTable.initEnumColumn(sqlType)
                with(EnumTable) {
                    if (indices.isEmpty()) status.uniqueIndex()
                }
                SchemaUtils.create(EnumTable)

                referenceTable.initRefColumn()
                SchemaUtils.create(referenceTable)

                val status = Status.ACTIVE
                val id1 = EnumTable.insert {
                    it[EnumTable.status] = status
                } get EnumTable.status

                referenceTable.insert {
                    it[referenceColumn] = id1
                }

                EnumTable.selectAll().single()[EnumTable.status] shouldBeEqualTo status
                referenceTable.selectAll().single()[referenceTable.referenceColumn] shouldBeEqualTo status
            } finally {
                runCatching {
                    SchemaUtils.drop(referenceTable)
                    exec(EnumTable.indices.first().dropStatement().single())
                    SchemaUtils.drop(EnumTable)

                    if (currentDialect is PostgreSQLDialect) {
                        exec("DROP TYPE IF EXISTS StatusEnum")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun testEnumerationColumnsWithReference(testDB: TestDB) = runTest {
        val tester = object: Table("tester") {
            val enumColumn = enumeration<Status>("enum_column").uniqueIndex()
            val enumNameColumn = enumerationByName<Status>("enum_name_column", 32).uniqueIndex()
        }
        val referenceTable = object: Table("ref_table") {
            val referenceColumn = reference("ref_column", tester.enumColumn)
            val referenceNameColumn = reference("ref_name_column", tester.enumNameColumn)
        }

        withTables(testDB, tester, referenceTable) {
            val active = Status.ACTIVE
            val inavtive = Status.INACTIVE
            val entry = tester.insert {
                it[enumColumn] = active
                it[enumNameColumn] = inavtive
            }
            referenceTable.insert {
                it[referenceColumn] = entry[tester.enumColumn]
                it[referenceNameColumn] = entry[tester.enumNameColumn]
            }

            tester.selectAll().single()[tester.enumColumn] shouldBeEqualTo active
            referenceTable.selectAll().single()[referenceTable.referenceColumn] shouldBeEqualTo active

            tester.selectAll().single()[tester.enumNameColumn] shouldBeEqualTo inavtive
            referenceTable.selectAll().single()[referenceTable.referenceNameColumn] shouldBeEqualTo inavtive
        }
    }
}
