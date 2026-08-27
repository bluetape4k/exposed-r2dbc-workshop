package exposed.r2dbc.examples.spring.modulith.boundaries.orders.internal

import exposed.r2dbc.examples.spring.modulith.boundaries.orders.AcceptOrderCommand
import exposed.r2dbc.examples.spring.modulith.boundaries.orders.OrderSummary
import io.bluetape4k.idgenerators.snowflake.Snowflakers
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** orders module이 소유하는 R2DBC 전용 order table입니다. */
internal object WorkshopOrders: Table("ddd_modulith_orders") {
    val id = long("id")
    val orderKey = varchar("order_key", 80).uniqueIndex()
    val customerId = varchar("customer_id", 80)
    val status = varchar("status", 30)
    val acceptedAt = long("accepted_at_epoch_millis")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

/** orders module 내부 repository이며 다른 bounded context에는 공개하지 않습니다. */
@Repository
class ExposedOrderRepository(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    suspend fun accept(command: AcceptOrderCommand): OrderSummary {
        ensureSchema()
        val orderKey = command.orderKey.trim().requireNotBlank("orderKey")
        val customerId = command.customerId.trim().requireNotBlank("customerId")
        val acceptedAt = Instant.now()
        return suspendTransaction(db = database) {
            val orderId = Snowflakers.Global.nextId()
            WorkshopOrders.insert {
                it[id] = orderId
                it[WorkshopOrders.orderKey] = orderKey
                it[WorkshopOrders.customerId] = customerId
                it[status] = ACCEPTED
                it[WorkshopOrders.acceptedAt] = acceptedAt.toEpochMilli()
            }
            WorkshopOrders
                .selectAll()
                .where { WorkshopOrders.id eq orderId }
                .single()
                .toOrderSummary()
        }
    }

    suspend fun clear() {
        ensureSchema()
        suspendTransaction(db = database) {
            WorkshopOrders.deleteAll()
        }
    }

    private suspend fun ensureSchema() {
        if (schemaReady.get()) return
        schemaMutex.withLock {
            if (schemaReady.get()) return
            suspendTransaction(db = database) {
                SchemaUtils.create(WorkshopOrders)
            }
            schemaReady.set(true)
        }
    }

    private companion object {
        const val ACCEPTED = "ACCEPTED"
    }
}

private fun String.requireNotBlank(field: String): String {
    require(isNotBlank()) { "$field must not be blank" }
    return this
}

private fun ResultRow.toOrderSummary(): OrderSummary =
    OrderSummary(
        id = this[WorkshopOrders.id],
        orderKey = this[WorkshopOrders.orderKey],
        customerId = this[WorkshopOrders.customerId],
        status = this[WorkshopOrders.status],
        acceptedAt = Instant.ofEpochMilli(this[WorkshopOrders.acceptedAt]),
    )
