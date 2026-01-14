package exposed.r2dbc.examples.money

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.money.compositeMoney
import org.jetbrains.exposed.v1.money.nullable
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

internal const val AMOUNT_SCALE = 5

/**
 * `Money` 를 나타내는 [MonetaryAmount] 를 저장하는 테이블을 정의합니다.
 *
 * [MonetaryAmount] 는 [BigDecimal] 과 [CurrencyUnit] 으로 구성되어 있습니다.
 *
 * ```sql
 * -- Postgres
 * CREATE TABLE IF NOT EXISTS accounts (
 *      id SERIAL PRIMARY KEY,
 *      composite_money DECIMAL(8, 5) NULL,       -- amount
 *      "composite_money_C" VARCHAR(3) NULL       -- currency
 * )
 *
 * CREATE INDEX ix_money_amount ON accounts (composite_money)
 * ```
 *
 * ```sql
 * -- MySQL
 * CREATE TABLE IF NOT EXISTS Accounts (
 *      id INT AUTO_INCREMENT PRIMARY KEY,
 *      composite_money DECIMAL(8, 5) NULL,
 *      composite_money_C VARCHAR(3) NULL
 * )
 *
 * CREATE INDEX ix_amount ON Accounts (composite_money)
 * ```
 */
internal object AccountTable: IntIdTable("Accounts") {
    val composite_money =
        compositeMoney(8, AMOUNT_SCALE, "composite_money").nullable()

    init {
        index("ix_money_amount", false, composite_money.amount)
    }
}
