package exposed.r2dbc.examples.money

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.money.moneyOf
import kotlinx.atomicfu.atomic
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.money.compositeMoney
import java.math.BigDecimal

/**
 * `compositeMoney` 컬럼의 기본값 설정 예제.
 *
 * `exposed-money` 모듈은 JSR-354 JavaMoney API(`javax.money.MonetaryAmount`)를 Exposed와 통합합니다.
 * `compositeMoney(precision, scale, columnName)` 함수는 하나의 `MonetaryAmount` 값을 두 개의 DB 컬럼으로 분리 저장합니다:
 * - `columnName`: 금액 — `DECIMAL(precision, scale)` 타입
 * - `columnName_C`: 통화 코드 — `VARCHAR(3)` 타입 (ISO 4217)
 *
 * ## 기본값 설정 방법
 *
 * | 방법                                    | 설명                                       |
 * |---------------------------------------|--------------------------------------------|
 * | `.default(MonetaryAmount)`            | INSERT 시 사용할 상수 기본값 (금액 + 통화 동시 설정)     |
 * | `.nullable()`                         | NULL 허용 (기본값 없음)                         |
 * | `.clientDefault { ... }`              | INSERT 시 클라이언트에서 람다로 생성하는 기본값            |
 *
 * ## 주의사항
 *
 * - `compositeMoney.default(value)` 은 금액과 통화 컬럼 모두에 기본값을 설정합니다.
 * - `compositeMoney.nullable()` 은 금액과 통화 컬럼 모두를 NULL 허용으로 만듭니다.
 * - 금액과 통화는 항상 함께 설정되어야 합니다. 한쪽만 NULL이면 논리적 불일치가 발생합니다.
 * - `clientDefault`로 생성된 값은 DB 서버의 `DEFAULT` 표현식이 아닌 애플리케이션에서 계산됩니다.
 */
class Ex01_MoneyDefaults: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS tablewithdbdefault (
     *      id SERIAL PRIMARY KEY,
     *      field VARCHAR(100) NOT NULL,
     *      t1 DECIMAL(10, 0) DEFAULT 1 NOT NULL,
     *      "t1_C" VARCHAR(3) DEFAULT 'USD' NOT NULL,
     *      t2 DECIMAL(10, 0) NULL,
     *      "t2_C" VARCHAR(3) NULL,
     *      "clientDefault" INT NOT NULL
     * )
     * ```
     */
    object TableWithDBDefault: IntIdTable("TableWithDBDefault") {
        internal val defaultValue = moneyOf(BigDecimal.ONE, "USD") // 컬럼이 아닙니다.
        private val cIndex = atomic(0)  // 컬럼이 아닙니다.
        internal var index by cIndex


        val field = varchar("field", 100)
        val t1 = compositeMoney(10, 0, "t1").default(defaultValue)
        val t2 = compositeMoney(10, 0, "t2").nullable()
        val clientDefault = integer("clientDefault").clientDefault { cIndex.getAndIncrement() }

        internal fun resetIndex(value: Int = 0) {
            cIndex.lazySet(value)
        }
    }
}
