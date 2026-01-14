package exposed.r2dbc.examples.money

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.expectException
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.money.currencyUnitOf
import io.bluetape4k.money.moneyOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.javamoney.moneta.Money
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.money.compositeMoney
import org.jetbrains.exposed.v1.money.currency
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

class Ex02_Money: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * Money 를 사용하는 테스트를 실행합니다.
     *
     * ```sql
     * -- Postgres
     * INSERT INTO accounts (composite_money, "composite_money_C")
     * VALUES (10.00000, 'USD');
     *
     * SELECT accounts.composite_money,
     *        accounts."composite_money_C"
     *   FROM accounts WHERE accounts.id = 1;
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert and select money`(testDB: TestDB) = runTest {
        withTables(testDB, AccountTable) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(Money.of(BigDecimal.TEN, "USD"))
            AccountTable.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(Money.of(BigDecimal.TEN, "USD"))
        }
    }

    /**
     * Floating 값을 가지는 Money 를 사용하는 테스트를 실행합니다.
     *
     * ```sql
     * -- Postgres
     * INSERT INTO accounts (composite_money, "composite_money_C")
     * VALUES (0.12345, 'USD');
     *
     * SELECT accounts.composite_money,
     *        accounts."composite_money_C"
     *   FROM accounts WHERE accounts.id = 1;
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert and select floating money`(testDB: TestDB) = runTest {
        withTables(testDB, AccountTable) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(Money.of(0.12345.toBigDecimal(), "USD"))
            AccountTable.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(Money.of(0.54321.toBigDecimal(), "USD"))
        }
    }

    /**
     * Null 값을 가지는 Money 를 사용하는 테스트를 실행합니다.
     *
     * ```sql
     * -- Postgres
     * INSERT INTO accounts (composite_money, "composite_money_C")
     * VALUES (NULL, NULL);
     *
     * SELECT accounts.composite_money,
     *        accounts."composite_money_C"
     *   FROM accounts
     *  WHERE accounts.id = 1
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert select null`(testDB: TestDB) = runTest {
        withTables(testDB, AccountTable) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(null)
            AccountTable.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(null)
        }
    }

    /**
     * INSERT 시에 통화량(`amount`)의 값이 DB 컬럼의 자릿수 (8,5) 를 초과하는 경우에는 예외가 발생한다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert select out of length`(testDB: TestDB) = runTest {
        val amount = 12345.toBigDecimal()
        val toInsert = moneyOf(amount, "CZK")

        // AccountTable 의 composite_money 의 자릿수가 (8, 5) 이므로, 소숫점 상위 부분은 3자리만 가능한다.
        withTables(testDB, AccountTable) {
            expectException<ExposedR2dbcException> {
                AccountTable.insertAndGetId {
                    it[composite_money] = toInsert
                }
            }
            expectException<ExposedR2dbcException> {
                AccountTable.insertAndGetId {
                    it[composite_money.amount] = amount
                    it[composite_money.currency] = toInsert.currency
                }
            }
        }
    }

    /**
     * [compositeMoney] 함수를 이용하여 직접 `Money` 컬럼을 정의하고 사용합니다.
     *
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `using manual composite money columns`(testDB: TestDB) = runTest {
        /**
         * ```sql
         * CREATE TABLE IF NOT EXISTS tester (
         *      amount DECIMAL(8, 5) NOT NULL,
         *      currency VARCHAR(3) NOT NULL,
         *      nullable_amount DECIMAL(8, 5) NULL,
         *      nullable_currency VARCHAR(3) NULL
         * )
         * ```
         */
        val tester = object: Table("tester") {
            val money = compositeMoney(
                decimal("amount", 8, AMOUNT_SCALE),
                currency("currency")
            )

            val nullableMoney = compositeMoney(
                decimal("nullable_amount", 8, AMOUNT_SCALE).nullable(),
                currency("nullable_currency").nullable()
            )
        }

        withTables(testDB, tester) {
            /**
             * ```sql
             * INSERT INTO tester (amount, currency, nullable_amount, nullable_currency)
             * VALUES (99.00000, 'EUR', NULL, NULL)
             * ```
             */
            val amount = 99.toBigDecimal().setScale(AMOUNT_SCALE)
            val currencyUnit = currencyUnitOf("EUR")
            tester.insert {
                it[money.amount] = amount
                it[money.currency] = currencyUnit
                it[nullableMoney.amount] = null
                it[nullableMoney.currency] = null
            }

            /**
             * ```sql
             * SELECT tester.amount,
             *        tester.currency,
             *        tester.nullable_amount,
             *        tester.nullable_currency
             *   FROM tester
             *  WHERE (tester.nullable_amount IS NULL)
             *    AND (tester.nullable_currency IS NULL)
             * ```
             */
            val result1 = tester.selectAll()
                .where { tester.nullableMoney.amount.isNull() }
                .andWhere { tester.nullableMoney.currency.isNull() }
                .single()
            result1[tester.money.amount] shouldBeEqualTo amount

            /**
             * ```sql
             * UPDATE tester
             *    SET nullable_amount=99.00000,
             *        nullable_currency='EUR'
             * ```
             */
            tester.update {
                it[tester.nullableMoney.amount] = amount
                it[tester.nullableMoney.currency] = currencyUnit
            }

            /**
             * ```sql
             * -- Postgres
             * SELECT tester.currency,
             *        tester.nullable_currency
             *   FROM tester
             *  WHERE (tester.amount IS NOT NULL)
             *    AND (tester.nullable_amount IS NOT NULL)
             * ```
             */
            val result2 = tester
                .select(tester.money.currency, tester.nullableMoney.currency)
                .where { tester.money.amount.isNotNull() }
                .andWhere { tester.nullableMoney.amount.isNotNull() }
                .single()

            result2[tester.money.currency] shouldBeEqualTo currencyUnit
            result2[tester.nullableMoney.currency] shouldBeEqualTo currencyUnit

            /**
             * manual composite columns should still accept composite values
             *
             * ```sql
             * INSERT INTO tester (amount, currency, nullable_amount, nullable_currency)
             * VALUES (10, 'CAD', NULL, NULL)
             * ```
             * ```
             * INSERT INTO tester (amount, currency) VALUES (10, 'CAD')
             * ```
             */
            val compositeMoney = moneyOf(10, "CAD")
            tester.insert {
                it[money] = compositeMoney
                it[nullableMoney] = null
            }
            tester.insert {
                it[money] = compositeMoney
            }

            /**
             * Search by composite column
             *
             * ```sql
             * SELECT COUNT(*) FROM tester
             *  WHERE (tester.nullable_amount IS NULL)
             *    AND (tester.nullable_currency IS NULL)
             * ```
             */
            tester.selectAll()
                .where { tester.nullableMoney eq null }
                .count() shouldBeEqualTo 2L
        }
    }

    @Suppress("UnusedReceiverParameter")
    private suspend fun R2dbcTransaction.assertInsertOfCompositeValueReturnsEquivalentOnSelect(toInsert: Money?) {
        val accountId = AccountTable.insertAndGetId {
            it[composite_money] = toInsert
        }

        val single = AccountTable.select(AccountTable.composite_money).where { AccountTable.id eq accountId }.single()
        val inserted: MonetaryAmount? = single[AccountTable.composite_money]

        inserted shouldBeEqualTo toInsert
    }

    @Suppress("UnusedReceiverParameter")
    private suspend fun R2dbcTransaction.assertInsertOfComponentValuesReturnsEquivalentOnSelect(toInsert: Money?) {
        val amount: BigDecimal? = toInsert?.numberStripped?.setScale(AMOUNT_SCALE)
        val currencyUnit: CurrencyUnit? = toInsert?.currency
        val accountId = AccountTable.insertAndGetId {
            it[AccountTable.composite_money.amount] = amount
            it[AccountTable.composite_money.currency] = currencyUnit
        }

        val single = AccountTable
            .select(AccountTable.composite_money)
            .where { AccountTable.id eq accountId }
            .single()

        single[AccountTable.composite_money.amount] shouldBeEqualTo amount
        single[AccountTable.composite_money.currency] shouldBeEqualTo currencyUnit
    }
}
