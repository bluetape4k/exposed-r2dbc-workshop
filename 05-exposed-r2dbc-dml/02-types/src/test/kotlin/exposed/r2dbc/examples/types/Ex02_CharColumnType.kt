package exposed.r2dbc.examples.types

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class Ex02_CharColumnType: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS chartable (
     *      id SERIAL PRIMARY KEY,
     *      "charColumn" CHAR NOT NULL
     * )
     * ```
     */
    object CharTable: IntIdTable("charTable") {
        val charColumn: Column<Char> = char("charColumn")
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `char column read and write`(testDB: TestDB) = runTest {
        withTables(testDB, CharTable) {
            val id: EntityID<Int> = CharTable.insertAndGetId {
                it[charColumn] = 'A'
            }

            val result: ResultRow? = CharTable
                .selectAll()
                .where { CharTable.id eq id }
                .singleOrNull()

            result?.get(CharTable.charColumn) shouldBeEqualTo 'A'
        }
    }

    /**
     * Char Column with `collate` (eg. C, utf8mb4_bin)
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `char column with collate`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in TestDB.ALL_POSTGRES + TestDB.ALL_MYSQL_LIKE + TestDB.ALL_MARIADB_LIKE }
        /**
         * ```sql
         * -- MySQL V8
         * CREATE TABLE IF NOT EXISTS tester (
         *      letter CHAR(1) COLLATE utf8mb4_bin NOT NULL
         * )
         * ```
         * ```sql
         * -- Postgres
         * CREATE TABLE IF NOT EXISTS tester (
         *      letter CHAR(1) COLLATE "C" NOT NULL
         * )
         * ```
         */
        val collateOption = when (testDB) {
            in TestDB.ALL_POSTGRES -> "C"
            else -> "utf8mb4_bin"
        }
        val tester = object: Table("tester") {
            val letter = char("letter", 1, collate = collateOption)
        }

        // H2 only allows collation for the entire database using SET COLLATION
        // Oracle only allows collation if MAX_STRING_SIZE=EXTENDED, which can only be set in upgrade mode
        // Oracle -> https://docs.oracle.com/en/database/oracle/oracle-database/12.2/refrn/MAX_STRING_SIZE.html#
        withTables(testDB, tester) {
            val letters = listOf("a", "A", "b", "B")
            tester.batchInsert(letters) { ch ->
                this[tester.letter] = ch
            }

            // one of the purposes of collation is to determine ordering rules of stored character data types
            val expected = letters.sortedBy { it.first().code } // [A, B, a, b]
            val actual = tester
                .select(tester.letter)
                .orderBy(tester.letter)
                .map { it[tester.letter] }
                .toList()

            actual shouldBeEqualTo expected // [A, B, a, b]
        }
    }
}
