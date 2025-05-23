package exposed.r2dbc.examples.json


import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.junit.jupiter.api.Assumptions

abstract class R2dbcExposedJsonTest: R2dbcExposedTestBase() {

    companion object: KLogging()


    protected suspend fun withJsonTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: JsonTestData.JsonTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = JsonTestData.JsonTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            /**
             * JSON column 에 JSON 데이터를 저장합니다.
             * ```sql
             * -- Postgres
             * INSERT INTO j_table (j_column)
             * VALUES ({"user":{"name":"Admin","team":null},"logins":10,"active":true,"team":null})
             * ```
             */
            tester.insert { it[jsonColumn] = data1 }

            statement(tester, user1, data1)
        }
    }

    protected suspend fun withJsonBTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: JsonTestData.JsonBTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = JsonTestData.JsonBTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert { it[jsonBColumn] = data1 }

            statement(tester, user1, data1)
        }
    }

    protected suspend fun withJsonArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: JsonTestData.JsonArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        Assumptions.assumeTrue { testDB !in TestDB.ALL_H2 }

        val tester = JsonTestData.JsonArrayTable

        withTables(testDB, tester) {
            /**
             * ```sql
             * INSERT INTO j_arrays ("groups", numbers)
             * VALUES ({"users":[{"name":"Admin","team":"Team A"}]}, [100])
             * ```
             */
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("Admin", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }

            /**
             * ```sql
             * INSERT INTO j_arrays ("groups", numbers)
             * VALUES (
             *      {"users":[{"name":"B","team":"Team B"},{"name":"C","team":"Team C"},{"name":"D","team":"Team D"}]},
             *      [3,4,5]
             * )
             * ```
             */
            val tripleId = tester.insertAndGetId {
                // User name = "B", "C", "D"
                // User team = "Team B", "Team C", "Team D"
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)
        }
    }

    protected suspend fun withJsonBArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: JsonTestData.JsonBArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        Assumptions.assumeTrue { testDB !in TestDB.ALL_H2 }

        val tester = JsonTestData.JsonBArrayTable

        withTables(testDB, tester) {
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("Admin", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)
        }
    }
}
