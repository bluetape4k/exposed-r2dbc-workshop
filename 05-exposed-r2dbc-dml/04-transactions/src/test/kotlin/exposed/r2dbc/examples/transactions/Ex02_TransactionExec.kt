package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.inProperCase
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.collections.eclipse.toFastList
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.exposed.r2dbc.getInt
import io.bluetape4k.exposed.r2dbc.getStringOrNull
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.autoIncColumnType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.utility.Base58
import java.sql.ResultSet

class Ex02_TransactionExec: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE SEQUENCE IF NOT EXISTS exec_id_seq
     *      START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
     *
     * CREATE TABLE IF NOT EXISTS exec_table (
     *      id INT PRIMARY KEY,
     *      amount INT NOT NULL
     * );
     * ```
     */
    object ExecTable: Table("exec_table") {
        val id = integer("id").autoIncrement("exec_id_seq")
        val amount = integer("amount")

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Exposed [R2dbcTransaction.exec] method에 대한 한 줄의 SQL을 전달하여 실행하는 방법에 대한 테스트 코드입니다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `exec with single statement query`(testDB: TestDB) = runTest {
        withTables(testDB, ExecTable) {
            val amounts = (90..99).toFastList()

            ExecTable.batchInsert(amounts, shouldReturnGeneratedValues = false) { amount ->
                this[ExecTable.id] = (amount % 10 + 1)  // autoIncrement 이지만, custom 으로 설정 
                this[ExecTable.amount] = amount
            }

            /**
             * ```sql
             * SELECT * FROM exec_table;
             * ```
             */
            val results = exec(
                stmt = """SELECT * FROM ${ExecTable.tableName.inProperCase()};""",
                explicitStatementType = StatementType.SELECT
            ) { row ->
                val id = row.getInt("id")
                val loadedAmount = row.getInt("amount")
                log.debug { "Loaded id=$id, amount: $loadedAmount" }
                loadedAmount
            }!!.toFastList()

            results shouldBeEqualTo amounts
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `exec with multi statements query`(testDB: TestDB) = runTest {
        // PGjdbc-NG 드라이버는 단일 PreparedStatement에서 여러 명령을 허용하지 않습니다.
        // SQLite 및 H2 드라이버는 여러 개를 허용하지만 첫 번째 문장의 결과만 반환합니다.
        // SQLite issue tracker: https://github.com/xerial/sqlite-jdbc/issues/277
        // H2 issue tracker: https://github.com/h2database/h2database/issues/3704
        // val toExclude = TestDB.ALL_H2 + TestDB.ALL_MYSQL_LIKE + listOf(TestDB.POSTGRESQLNG)
        val supportMultipleStatements = setOf(TestDB.POSTGRESQL)

        Assumptions.assumeTrue { testDB in supportMultipleStatements }
        withTables(testDB, ExecTable) {
            testInsertAndSelectInSingleExec(testDB)
        }
    }

    /**
     * MySQL/MariaDB에서 `allowMultiQueries=true` 설정을 추가하여 여러 개의 SQL 문을 실행하는 방법에 대한 테스트 코드입니다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `exec with multi statement query using MySQL`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in TestDB.ALL_MYSQL_MARIADB }

        val extra = if (testDB in TestDB.ALL_MARIADB) "?" else ""
        val db = R2dbcDatabase.connect(
            url = testDB.connection().plus("$extra&allowMultiQueries=true"),
        )

        suspendTransaction(db = db) {
            try {
                SchemaUtils.create(ExecTable)
                commit()
                testInsertAndSelectInSingleExec(testDB)
                commit()
            } finally {
                SchemaUtils.drop(ExecTable)
            }
        }

        TransactionManager.closeAndUnregister(db)
    }

    private suspend fun R2dbcTransaction.testInsertAndSelectInSingleExec(testDB: TestDB) {
        ExecTable.insert {
            it[amount] = 99
        }

        val insertStatement = "INSERT INTO ${ExecTable.tableName.inProperCase()} " +
                "(${ExecTable.amount.name.inProperCase()}, ${ExecTable.id.name.inProperCase()}) " +
                "VALUES (100, ${ExecTable.id.autoIncColumnType?.nextValExpression})"

        val columnAlias = "last_inserted_id"
        val selectLastIdStatement = when (testDB) {
            TestDB.POSTGRESQL -> "SELECT lastval() AS $columnAlias;"
            TestDB.MARIADB -> "SELECT LASTVAL(${ExecTable.id.autoIncColumnType?.autoincSeq}) AS $columnAlias"
            else -> "SELECT LAST_INSERT_ID() AS $columnAlias"
        }

        val insertAndSelectStatements =
            """
            $insertStatement;
            $selectLastIdStatement;
            """.trimIndent()

        val result = exec(
            insertAndSelectStatements,
            explicitStatementType = StatementType.MULTI
        ) { row -> row.getInt(columnAlias) }?.singleOrNull()

        result.shouldNotBeNull() shouldBeEqualTo 2
    }

    /**
     * [R2dbcTransaction.exec] 메서드 실행의 결과인 [ResultSet]에서 결과물을 가져오는 방법에 대한 테스트 코드입니다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `exec with nullable and empty resultSets`(testDB: TestDB) = runTest {
        val tester = object: Table("tester_${Base58.randomString(4)}") {
            val id = integer("id")
            val title = varchar("title", 32)
        }

        withTables(testDB, tester) {
            tester.insert {
                it[id] = 1
                it[title] = "Exposed"
            }

            val (table, id, title) = listOf(
                tester.tableName,
                tester.id.name,
                tester.title.name
            )

            val stringResult = exec(
                """SELECT $title FROM $table WHERE $id = 1;"""
            ) { row ->
                row.getStringOrNull(title)
            }?.singleOrNull()
            stringResult shouldBeEqualTo "Exposed"

            // no record exists for id = 999, but result set returns single nullable value due to subquery alias
            val nullColumnResult = exec(
                """SELECT (SELECT $title FROM $table WHERE $id = 999) AS sub;"""
            ) { row ->
                row.getStringOrNull("sub")
            }?.singleOrNull()
            nullColumnResult.shouldBeNull()

            // no record exists for id = 999, so result set is empty and rs.next() is false
            val nullTransformResult = exec(
                """SELECT $title FROM $table WHERE $id = 999;"""
            ) { row ->
                row.getStringOrNull(title)
            }?.singleOrNull()
            nullTransformResult.shouldBeNull()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `run execInBatch`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in TestDB.ALL_POSTGRES }

        val users = object: IntIdTable("users_batch") {
            val name = varchar("name", 50)
            val age = integer("age")
        }

        withTables(testDB, users) {
            val statments = List(3) {
                val name = faker.name().firstName()
                val age = faker.number().numberBetween(18, 80)

                "INSERT INTO ${users.tableName.inProperCase()} " +
                        "(${users.name.name.inProperCase()}, ${users.age.name.inProperCase()}) " +
                        "VALUES ('$name', $age)"
            }

            execInBatch(statments)

            users.selectAll().count() shouldBeEqualTo 3
        }
    }
}
