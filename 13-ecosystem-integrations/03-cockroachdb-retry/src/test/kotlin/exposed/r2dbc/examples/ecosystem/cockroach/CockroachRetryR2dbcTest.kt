package exposed.r2dbc.examples.ecosystem.cockroach

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CockroachRetryR2dbcTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `retryable SQLSTATE reruns the whole reservation transaction`(testDB: TestDB) = runTest {
        withDb(testDB) {
            SchemaUtils.drop(CockroachInventoryItems, CockroachInventoryLedger)
            SchemaUtils.create(CockroachInventoryItems, CockroachInventoryLedger)
            CockroachInventoryItems.insert {
                it[sku] = "retry"
                it[quantityOnHand] = 10
                it[version] = 0
            }
            commit()
        }

        try {
            var attempts = 0
            val snapshot = reserveWithRetry(
                testDB = testDB,
                sku = "retry",
                quantity = 4,
                reason = "checkout",
                policy = CockroachR2dbcRetryPolicy(maxAttempts = 3),
            ) { attempt ->
                attempts = attempt
                if (attempt == 1) {
                    throw RetryableSqlStateFailure("restart transaction", "40001")
                }
            }

            attempts shouldBeEqualTo 2
            snapshot shouldBeEqualTo InventorySnapshot("retry", quantityOnHand = 6, version = 1)

            withDb(testDB) {
                CockroachInventoryLedger.selectAll()
                    .where { CockroachInventoryLedger.sku eq "retry" }
                    .single()[CockroachInventoryLedger.quantity] shouldBeEqualTo 4
            }
        } finally {
            withDb(testDB) {
                SchemaUtils.drop(CockroachInventoryLedger, CockroachInventoryItems)
            }
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `non retryable SQLSTATE is not retried`(testDB: TestDB) = runTest {
        val policy = CockroachR2dbcRetryPolicy(maxAttempts = 3)
        var attempts = 0
        val failure = assertFailsWith<RetryableSqlStateFailure> {
            policy.execute {
                attempts++
                throw RetryableSqlStateFailure("duplicate key", "23505")
            }
        }

        attempts shouldBeEqualTo 1
        failure.sqlState shouldBeEqualTo "23505"
        RetryableSqlStateFailure("restart transaction", "40001").isCockroachRetryable().shouldBeTrue()
    }
}
