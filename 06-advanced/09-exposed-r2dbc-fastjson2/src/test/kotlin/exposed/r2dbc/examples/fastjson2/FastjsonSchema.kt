package exposed.r2dbc.examples.fastjson2

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.fastjson2.fastjson
import io.bluetape4k.exposed.core.fastjson2.fastjsonb
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId

@Suppress("UnusedReceiverParameter")
object FastjsonSchema {

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS fastjson_table (
     *      id SERIAL PRIMARY KEY,
     *      fastjson_column JSON NOT NULL
     * )
     * ```
     */
    object FastjsonTable: IntIdTable("fastjson_table") {
        val fastjsonColumn = fastjson<DataHolder>("fastjson_column")
    }

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS fastjson_b_table (
     *      id SERIAL PRIMARY KEY,
     *      fastjson_b_column JSONB NOT NULL
     * );
     * ```
     */
    object FastjsonBTable: IntIdTable("fastjson_b_table") {
        val fastjsonBColumn = fastjsonb<DataHolder>("fastjson_b_column")
    }

    class FastjsonEntity(id: EntityID<Int>): IntEntity(id) {
        companion object: IntEntityClass<FastjsonEntity>(FastjsonTable)

        var fastjsonColumn by FastjsonTable.fastjsonColumn
    }

    class FastjsonBEntity(id: EntityID<Int>): IntEntity(id) {
        companion object: IntEntityClass<FastjsonBEntity>(FastjsonBTable)

        var fastjsonBColumn by FastjsonBTable.fastjsonBColumn
    }

    /**
     * ```sql
     * CREATE TABLE IF NOT EXISTS fastjson_arrays (
     *      id SERIAL PRIMARY KEY,
     *      "groups" JSON NOT NULL,
     *      numbers JSON NOT NULL
     * );
     * ```
     */
    object FastjsonArrayTable: IntIdTable("fastjson_arrays") {
        val groups = fastjson<UserGroup>("groups")
        val numbers = fastjson<IntArray>("numbers")
    }

    object FastjsonBArrayTable: IntIdTable("fastjson_b_arrays") {
        val groups = fastjsonb<UserGroup>("groups")
        val numbers = fastjsonb<IntArray>("numbers")
    }


    data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

    data class User(val name: String, val team: String?)

    data class UserGroup(val users: List<User>)

    suspend fun R2dbcExposedTestBase.withFastjsonTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: FastjsonTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = FastjsonTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.fastjsonColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun R2dbcExposedTestBase.withFastjsonBTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: FastjsonBTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = FastjsonBTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.fastjsonBColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun R2dbcExposedTestBase.withFastjsonArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: FastjsonArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = FastjsonArrayTable

        withTables(testDB, tester) {
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)
        }
    }

    suspend fun R2dbcExposedTestBase.withFastjsonBArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: FastjsonBArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = FastjsonBArrayTable

        withTables(testDB, tester) {
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
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
