package exposed.r2dbc.examples.dml

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.exposed.core.CteTable
import io.bluetape4k.exposed.r2dbc.withCte
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.crossJoin
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * `CteTable` and `withCte` example for Exposed R2DBC Query Builder based CTEs.
 *
 * The CTE body remains an Exposed DSL query, and the temporary CTE table exposes fields through
 * `cte[originalColumn]` references.
 */
class Ex51_CteQueryBuilder: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel() {
        private val cteSupportedDb = TestDB.ALL_H2 + TestDB.ALL_POSTGRES_LIKE + TestDB.MYSQL_V8
    }

    object CteUsers: Table("r2dbc_cte_query_builder_users") {
        val id = integer("id")
        val name = varchar("name", 64)
        val active = bool("active")
        val managerId = integer("manager_id").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `query builder cte filters active users`(testDB: TestDB) = runSuspendIO {
        Assumptions.assumeTrue { testDB in cteSupportedDb }

        withTables(testDB, CteUsers) {
            seedUsers()

            val activeUsers = CteTable(
                name = "active_users",
                query = CteUsers
                    .select(CteUsers.id, CteUsers.name)
                    .where { CteUsers.active eq true }
            )
            val activeName = activeUsers[CteUsers.name]
            val query = activeUsers
                .select(activeName)
                .withCte(activeUsers)
                .orderBy(activeUsers[CteUsers.id])

            val builder = QueryBuilder(prepared = true)
            val sql = query.prepareSQL(builder)

            sql shouldContain "WITH"
            sql.lowercase() shouldContain "active_users"
            builder.args.map { it.second } shouldBeEqualTo listOf(true)

            query.map { it[activeName] }.toList() shouldBeEqualTo listOf("root", "child")
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `recursive query builder cte references temporary table columns`(testDB: TestDB) = runSuspendIO {
        Assumptions.assumeTrue { testDB in cteSupportedDb }

        withTables(testDB, CteUsers) {
            seedUsers()

            val hierarchy = CteTable(
                name = "user_hierarchy",
                query = CteUsers
                    .select(CteUsers.id, CteUsers.name, CteUsers.managerId)
                    .where { CteUsers.managerId.isNull() and (CteUsers.active eq true) },
                recursiveQuery = { cte ->
                    CteUsers
                        .crossJoin(cte)
                        .select(CteUsers.id, CteUsers.name, CteUsers.managerId)
                        .where { CteUsers.managerId eq cte[CteUsers.id] }
                }
            )
            val hierarchyName = hierarchy[CteUsers.name]
            val query = hierarchy
                .select(hierarchyName)
                .withCte(hierarchy)
                .orderBy(hierarchy[CteUsers.id])

            val sql = query.prepareSQL(QueryBuilder(prepared = true))

            sql shouldContain "WITH RECURSIVE"
            query.map { it[hierarchyName] }.toList() shouldBeEqualTo listOf("root", "child", "inactive-child")
        }
    }

    private suspend fun seedUsers() {
        CteUsers.insert {
            it[id] = 1
            it[name] = "root"
            it[active] = true
            it[managerId] = null
        }
        CteUsers.insert {
            it[id] = 2
            it[name] = "child"
            it[active] = true
            it[managerId] = 1
        }
        CteUsers.insert {
            it[id] = 3
            it[name] = "inactive-child"
            it[active] = false
            it[managerId] = 2
        }
        CteUsers.insert {
            it[id] = 4
            it[name] = "other-root"
            it[active] = false
            it[managerId] = null
        }
    }
}
