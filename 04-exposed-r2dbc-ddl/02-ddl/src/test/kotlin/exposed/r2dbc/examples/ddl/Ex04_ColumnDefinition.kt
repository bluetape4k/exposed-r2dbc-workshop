package exposed.r2dbc.examples.ddl

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.r2dbc.getBoolean
import io.bluetape4k.exposed.r2dbc.getInt
import io.bluetape4k.exposed.r2dbc.selectImplicitAll
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.origin
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/**
 * Exposed R2DBC에서 다양한 컬럼 정의 패턴을 보여주는 예제.
 *
 * 컬럼 기본값, 서버 기본값, CHECK 제약, generated 컬럼, nullable 처리 등
 * 실제 DDL 생성 결과와 함께 학습할 수 있습니다.
 *
 * 주요 예제:
 * - `clientDefault` / `defaultExpression` 으로 기본값 설정
 * - `check(...)` 제약 조건 추가
 * - `generatedAs(...)` 로 계산 컬럼(generated column) 정의
 * - nullable 컬럼 및 nullability 검증
 *
 * ```sql
 * -- CHECK 제약 예시 (H2)
 * CREATE TABLE IF NOT EXISTS tester (
 *     id INT NOT NULL,
 *     "name" VARCHAR(255) NOT NULL,
 *     CONSTRAINT chk_tester_name_valid CHECK ("name" > '')
 * );
 *
 * -- Generated column 예시 (MySQL V8)
 * CREATE TABLE IF NOT EXISTS tester (
 *     first_name VARCHAR(30) NOT NULL,
 *     last_name VARCHAR(30) NOT NULL,
 *     full_name VARCHAR(61) GENERATED ALWAYS AS (CONCAT(first_name, ' ', last_name)) STORED NOT NULL
 * );
 * ```
 *
 * @see org.jetbrains.exposed.v1.core.Table.check
 * @see org.jetbrains.exposed.v1.core.Column.generatedAs
 * @see org.jetbrains.exposed.v1.core.Column.clientDefault
 */

@Suppress("DEPRECATION")
class Ex04_ColumnDefinition: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    // 컬럼 주석을 지원하는 DB - H2, MySQL 8, NOT Postgres
    val columnCommentSupportedDB = TestDB.ALL_H2 + TestDB.MYSQL_V8

    /**
     * 컬럼에 주석을 달 수 있다
     * ```sql
     * -- H2
     * CREATE TABLE IF NOT EXISTS TESTER (
     *      AMOUNT INT COMMENT 'Amount of testers' NOT NULL
     * )
     * ```
     *
     * ```sql
     * -- MySQL 8
     * CREATE TABLE IF NOT EXISTS tester (
     *      amount INT COMMENT 'Amount of testers' NOT NULL
     * )
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `컬럼에 주석 달기`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in columnCommentSupportedDB }

        val comment = "Amount of testers"
        val tester = object: Table("tester") {
            val amount = integer("amount")
                .withDefinition("COMMENT", stringLiteral(comment))  // 컬럼에 주석 추가
        }

        withTables(testDB, tester) {
            val ddl = tester.ddl.single()
            log.info { "tester ddl: $ddl" }

            tester.insert {
                it[tester.amount] = 9
            }
            tester.selectAll().single()[tester.amount] shouldBeEqualTo 9
        }
    }

    /**
     * 묵시적으로 컬럼 전체를 뜻하는 `*` 를 사용하면, INVISIBLE 컬럼은 반환되지 않습니다.
     *
     * ```sql
     * -- MySQL 8
     * CREATE TABLE IF NOT EXISTS tester (
     *      amount INT NOT NULL,
     *      active BOOLEAN INVISIBLE NULL   -- INVISIBLE 컬럼
     * )
     * ```
     *
     * ```sql
     * -- MySQL 8
     * SELECT *                    -- active 는 INVISIBLE 컬럼이므로 반환되지 않음
     *   FROM TESTER
     *  WHERE TESTER.AMOUNT > 100
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `컬럼의 visibility를 변경할 수 있다`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in columnCommentSupportedDB }

        val tester = object: Table("tester") {
            val amount = integer("amount")
            val active = bool("active")
                .nullable()
                .withDefinition("INVISIBLE")  // Implicit 조회 시 (select * from tester), 컬럼을 숨김
        }

        withTables(testDB, tester) {
            if (testDB == TestDB.MYSQL_V8) {
                SchemaUtils.statementsRequiredToActualizeScheme(tester).shouldBeEmpty()
            }

            val ddl = tester.ddl.single()
            log.info { "tester ddl: $ddl" }

            tester.insert {
                it[amount] = 999
                it[active] = true
            }

            /**
             * 묵시적으로 컬럼 전체를 뜻하는 `*` 를 사용하면, INVISIBLE 컬럼은 반환되지 않습니다.
             *
             * ```sql
             * SELECT *         -- active 는 INVISIBLE 컬럼이므로 반환되지 않음
             *   FROM TESTER
             *  WHERE TESTER.AMOUNT > 100
             * ```
             */
            // HINT: 이렇게 Statement.execute(R2dbcTransaction) 을 수행하면 org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult 를 반환합니다.
            val result1 = tester.selectImplicitAll()
                .where { tester.amount greater 100 }
                .execute(this) as R2dbcResult

            // 전체 컬럼 수와 INVISIBLE 컬럼을 제외한 컬럼 수가 달라서 [ResultRow] 로 변환하는데 문제가 생깁니다.
            // 그래서 위와 같이 execute() 를 사용하여 [R2dbcResult] 를 반환받아야 합니다.
//            tester.selectImplicitAll()
//                .where { tester.amount greater 100 }
//                .collect {
//                    it[tester.amount] shouldBeEqualTo 999
//                }

            result1.mapRows { row ->
                row.origin.getInt(tester.amount.name) shouldBeEqualTo 999

                assertFailsWith<NoSuchElementException> {
                    row.origin.getBoolean(tester.active.name)
                }
            }.single()

            assertFails {
                val row = tester.selectImplicitAll()
                    .where { tester.amount greater 100 }
                    .single()

                row[tester.amount] shouldBeEqualTo 999
                row.getOrNull(tester.active).shouldBeNull()   // INVISIBLE 컬럼은 반환되지 않음
            }

            /**
             * 명시적으로 INVISIBLE 컬럼을 지정해야만 반환됩니다.
             *
             * ```sql
             * SELECT TESTER.AMOUNT,
             *        TESTER.ACTIVE
             *   FROM TESTER
             *  WHERE TESTER.AMOUNT > 100
             * ```
             */
            // HINT: 이렇게 Statement.execute(R2dbcTransaction) 을 수행하면 org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult 를 반환합니다.
            val result2 = tester
                .selectAll()
                .where { tester.amount greater 100 }
                .execute(this) as R2dbcResult

            result2
                .mapRows { row ->
                    row.origin.getInt(tester.amount.name) shouldBeEqualTo 999
                    row.origin.getBoolean(tester.active.name).shouldBeTrue()
                }
                .single()
        }
    }
}
