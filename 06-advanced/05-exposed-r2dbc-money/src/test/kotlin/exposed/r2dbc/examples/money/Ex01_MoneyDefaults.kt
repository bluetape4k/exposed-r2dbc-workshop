package exposed.r2dbc.examples.money

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.money.moneyOf
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.money.compositeMoney
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class Ex01_MoneyDefaults: R2dbcExposedTestBase() {

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
        internal val cIndex = AtomicInteger(0)  // 컬럼이 아닙니다.

        val field = varchar("field", 100)
        val t1 = compositeMoney(10, 0, "t1").default(defaultValue)
        val t2 = compositeMoney(10, 0, "t2").nullable()
        val clientDefault = integer("clientDefault").clientDefault { cIndex.getAndIncrement() }
    }

}
