package exposed.r2dbc.examples.ecosystem.cockroach

import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.io.Serializable

object CockroachInventoryItems: Table("cockroach_inventory_items") {
    val sku = varchar("sku", 64)
    val quantityOnHand = integer("quantity_on_hand")
    val version = long("version")

    override val primaryKey: PrimaryKey = PrimaryKey(sku)
}

object CockroachInventoryLedger: Table("cockroach_inventory_ledger") {
    val sku = varchar("sku", 64)
    val quantity = integer("quantity")
    val reason = varchar("reason", 64)
}

data class InventorySnapshot(
    val sku: String,
    val quantityOnHand: Int,
    val version: Long,
): Serializable

class RetryableSqlStateFailure(
    message: String,
    val sqlState: String,
): RuntimeException(message)

fun RetryableSqlStateFailure.isCockroachRetryable(): Boolean =
    sqlState == COCKROACH_RETRYABLE_SQLSTATE

class CockroachR2dbcRetryPolicy(
    val maxAttempts: Int = 3,
) {

    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    suspend fun <T> execute(block: suspend (attempt: Int) -> T): T {
        var attempt = 0
        var lastFailure: RetryableSqlStateFailure? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                return block(attempt)
            } catch (ex: RetryableSqlStateFailure) {
                if (!ex.isCockroachRetryable()) {
                    throw ex
                }
                lastFailure = ex
            }
        }
        throw lastFailure ?: IllegalStateException("retry policy exhausted without a failure")
    }
}

suspend fun reserveWithRetry(
    testDB: TestDB,
    sku: String,
    quantity: Int,
    reason: String,
    policy: CockroachR2dbcRetryPolicy = CockroachR2dbcRetryPolicy(),
    beforeAttempt: suspend (attempt: Int) -> Unit = {},
): InventorySnapshot =
    policy.execute { attempt ->
        beforeAttempt(attempt)
        var snapshot: InventorySnapshot? = null
        withDb(testDB) {
            val current = CockroachInventoryItems
                .selectAll()
                .where { CockroachInventoryItems.sku eq sku.requireNotBlank("sku") }
                .single()
            val currentQuantity = current[CockroachInventoryItems.quantityOnHand]
            require(currentQuantity >= quantity) { "insufficient quantity for $sku" }

            val nextQuantity = currentQuantity - quantity
            val nextVersion = current[CockroachInventoryItems.version] + 1
            CockroachInventoryItems.update({ CockroachInventoryItems.sku eq sku }) {
                it[quantityOnHand] = nextQuantity
                it[version] = nextVersion
            }
            CockroachInventoryLedger.insert {
                it[CockroachInventoryLedger.sku] = sku
                it[CockroachInventoryLedger.quantity] = quantity
                it[CockroachInventoryLedger.reason] = reason.requireNotBlank("reason")
            }
            snapshot = InventorySnapshot(sku = sku, quantityOnHand = nextQuantity, version = nextVersion)
        }
        checkNotNull(snapshot)
    }

private const val COCKROACH_RETRYABLE_SQLSTATE = "40001"

private fun String.requireNotBlank(name: String): String {
    require(isNotBlank()) { "$name must not be blank" }
    return this
}
