package exposed.r2dbc.examples.types

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Exposed R2DBC에서 Double(배정밀도 부동소수점) 컬럼 타입을 사용하는 예제.
 *
 * 주요 학습 내용:
 * - `double()` 컬럼 타입 정의 및 사용
 * - Double 값의 삽입, 조회, 비교
 * - `insertAndGetId()` 로 삽입 후 ID 즉시 반환
 * - nullable double 컬럼 처리
 *
 * 모든 쿼리는 `withTables(testDB, ...)` 블록 내에서 실행됩니다.
 */
class Ex04_DoubleColumnType: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS double_table (
     *      id SERIAL PRIMARY KEY,
     *      amount DOUBLE PRECISION NOT NULL
     * )
     * ```
     */
    object TestTable: IntIdTable("double_table") {
        val amount = double("amount")
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert and select from double column`(testDB: TestDB) = runTest {
        withTables(testDB, TestTable) {
            val id = TestTable.insertAndGetId {
                it[amount] = 9.23
            }

            TestTable.selectAll()
                .where { TestTable.id eq id }
                .single()[TestTable.amount] shouldBeEqualTo 9.23
        }
    }

    /**
     * `DOUBLE PRECISION` 타입을 사용하는 컬럼을 `REAL` 타입으로 변경해도 작동한다
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert and select from real column`(testDB: TestDB) = runTest {
        withDb(testDB) {
            val originalColumnDDL = TestTable.amount.descriptionDdl()
            val realColumnDDL = originalColumnDDL.replace(" DOUBLE PRECISION ", " REAL ")

            /**
             * create table with double() column that uses SQL type REAL
             *
             * ```sql
             * -- Postgres
             * CREATE TABLE IF NOT EXISTS double_table (
             *      id SERIAL PRIMARY KEY,
             *      amount REAL NOT NULL
             * )
             * ```
             */
            TestTable.ddl
                .map { it.replace(originalColumnDDL, realColumnDDL) }
                .forEach { exec(it) }

            val id = TestTable.insertAndGetId {
                it[amount] = 9.23
            }

            TestTable.selectAll()
                .where { TestTable.id eq id }
                .single()[TestTable.amount] shouldBeEqualTo 9.23

            SchemaUtils.drop(TestTable)
        }
    }
}
