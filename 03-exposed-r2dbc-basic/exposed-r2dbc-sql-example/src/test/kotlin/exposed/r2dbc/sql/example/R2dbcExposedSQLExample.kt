package exposed.r2dbc.sql.example

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.sql.example.Schema.CityTable
import exposed.r2dbc.sql.example.Schema.UserTable
import exposed.r2dbc.sql.example.Schema.withCityUsers
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import kotlinx.coroutines.flow.single
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.like
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.orWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class R2dbcExposedSQLExample: R2dbcExposedTestBase() {

    companion object: KLogging()

    /**
     * 특정 조건에 맞는 행을 UPDATE 합니다.
     *
     * ```sql
     * -- Postgres
     * UPDATE "users"
     *    SET "name"='Alexey'
     *  WHERE "users".ID = 'alex'
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `Raw SQL을 이용하여 Update 수행합니다`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            UserTable.update({ UserTable.id eq "alex" }) {
                it[name] = "Alexey"   // Alex -> Alexey
            }

            // 갱신된 정보를 조회합니다.
            UserTable
                .selectAll()
                .where { UserTable.id eq "alex" }
                .single()[UserTable.name] shouldBeEqualTo "Alexey"

            // 이렇게 수행해도 된다.
            UserTable
                .selectAll()
                .where { UserTable.name eq "Alexey" }
                .count() shouldBeEqualTo 1L
        }
    }


    /**
     * 조건절에 따라 행을 삭제합니다.
     *
     * ```sql
     * -- Postgres
     * DELETE
     *   FROM "users"
     *  WHERE "users"."name" LIKE '%thing'
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `Raw SQL을 이용하여 DELETE를 수행합니다`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            val affectedCount = UserTable.deleteWhere { UserTable.name like "%thing" }
            affectedCount shouldBeEqualTo 1

            // 삭제된 데이터가 없어야 한다.
            UserTable
                .selectAll()
                .where { UserTable.name like "%thing" }
                .count() shouldBeEqualTo 0L
        }
    }

    /**
     * Manual Join 조건을 추가로 지정합니다.
     *
     * ```sql
     * -- Postgres
     * SELECT users."name",
     *        cities."name"
     *   FROM users INNER JOIN cities ON cities.id = users.city_id
     *  WHERE ((users.id = 'debop') OR (users."name" = 'Jane.Doe'))
     *    AND (users.id = 'jane')
     *    AND (users.city_id = cities.id)
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `manual inner join`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            UserTable
                .innerJoin(CityTable)
                .select(UserTable.name, CityTable.name)
                .where { (UserTable.id eq "debop") or (UserTable.name eq "Jane.Doe") }
                .andWhere { UserTable.id eq "jane" }
                .andWhere { UserTable.cityId eq CityTable.id }  // manual join (굳이 할 필요 없음)
                .collect {
                    log.info { "${it[UserTable.name]} lives in ${it[CityTable.name]}" }
                }
        }
    }

    /**
     * Foreign Key를 이용하여 Join을 수행합니다.
     *
     * ```sql
     * -- Postgres
     * SELECT users."name",
     *        users.city_id,
     *        cities."name"
     *   FROM users INNER JOIN cities ON cities.id = users.city_id
     *  WHERE (cities."name" = 'Busan')
     *     OR (users.city_id IS NULL)
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `join with foreign key`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            UserTable
                .innerJoin(CityTable)
                .select(UserTable.name, UserTable.cityId, CityTable.name)
                .where { CityTable.name eq "Busan" }
                .orWhere { UserTable.cityId.isNull() }
                .collect {
                    if (it[UserTable.cityId] != null) {
                        log.info { "${it[UserTable.name]} lives in ${it[CityTable.name]}" }
                    } else {
                        log.info { "${it[UserTable.name]} lives nowhere" }
                    }
                }
        }
    }

    /**
     * `GROUP BY` 와 집계 함수를 사용합니다.
     *
     * ```sql
     * -- Postgres
     * SELECT cities."name",
     *        COUNT(users.id)
     *   FROM cities INNER JOIN users ON cities.id = users.city_id
     *  GROUP BY cities."name"
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `use functions and group by`(testDB: TestDB) = runSuspendIO {
        withCityUsers(testDB) {
            val query = CityTable.innerJoin(UserTable)
                .select(CityTable.name, UserTable.id.count())
                .groupBy(CityTable.name)

            query.collect {
                val cityName = it[CityTable.name]
                val userCount = it[UserTable.id.count()]

                if (userCount > 0) {
                    log.info { "$cityName has $userCount users" }
                } else {
                    log.info { "$cityName has no users" }
                }
            }
        }
    }
}
