package exposed.r2dbc.examples.functions

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.first
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.select
import java.math.BigDecimal
import java.math.RoundingMode

typealias SqlFunction<T> = org.jetbrains.exposed.v1.core.Function<T>

/**
 * SQL 함수 테스트를 위한 공통 베이스 클래스.
 *
 * 역할:
 * - `DUAL` 테이블 기반의 함수 평가 헬퍼 제공
 * - `evalFunction()` 으로 단일 함수 결과를 직접 평가
 * - `SqlFunction<T>` 타입 별칭을 통해 Exposed 함수를 간결하게 참조
 * - 하위 클래스(Ex01~Ex05)에서 상속하여 각 함수 카테고리별 테스트 작성
 *
 * 모든 서브클래스는 `withDb(testDB)` 블록 내에서 실행됩니다.
 */
abstract class Ex00_FunctionBase: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS faketable (
     *      id SERIAL PRIMARY KEY
     * )
     * ```
     */
    private object FakeTestTable: IntIdTable("fakeTable")

    protected suspend fun withTable(testDB: TestDB, body: suspend R2dbcTransaction.(TestDB) -> Unit) {
        withDb(testDB) {
            body(it)
        }
    }

    protected suspend infix fun <T> SqlFunction<T>.shouldExpressionEqualTo(expected: T) {
        val result = Table.Dual.select(this).first()[this]

        if (expected is BigDecimal && result is BigDecimal) {
            result.setScale(expected.scale(), RoundingMode.HALF_UP) shouldBeEqualTo expected
        } else {
            result shouldBeEqualTo expected
        }
    }
}
