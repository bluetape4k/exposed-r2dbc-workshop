package exposed.r2dbc.examples.money

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.money.moneyOf
import kotlinx.atomicfu.atomic
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.money.compositeMoney
import java.math.BigDecimal

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
