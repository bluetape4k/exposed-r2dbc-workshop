package exposed.r2dbc.examples.ddd.aggregate

import io.bluetape4k.exposed.core.ddd.AbstractAggregateRoot
import io.bluetape4k.exposed.core.ddd.DomainEvent
import io.bluetape4k.exposed.core.snowflakeGenerated
import io.bluetape4k.idgenerators.snowflake.Snowflakers
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@JvmInline
internal value class OrderNumber(val value: String): Serializable {
    init {
        value.requireNotBlank("orderNumber")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@JvmInline
internal value class CustomerId(val value: String): Serializable {
    init {
        value.requireNotBlank("customerId")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@JvmInline
internal value class Sku(val value: String): Serializable {
    init {
        value.requireNotBlank("sku")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@JvmInline
internal value class OperatorId(val value: String): Serializable {
    init {
        value.requireNotBlank("operatorId")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class Money(
    val amount: BigDecimal,
): Serializable {
    init {
        require(amount.signum() >= 0) { "amount must be greater than or equal to zero." }
    }

    operator fun plus(other: Money): Money = Money((amount + other.amount).scaled())

    operator fun times(quantity: Int): Money {
        quantity.requirePositiveNumber("quantity")
        return Money(amount.multiply(quantity.toBigDecimal()).scaled())
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        fun dollars(value: String): Money = Money(BigDecimal(value).scaled())
    }
}

internal data class OrderLine(
    val sku: Sku,
    val quantity: Int,
    val unitPrice: Money,
): Serializable {
    init {
        quantity.requirePositiveNumber("quantity")
    }

    val lineTotal: Money get() = unitPrice * quantity

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class PlaceOrderCommand(
    val orderNumber: OrderNumber,
    val customerId: CustomerId,
    val lines: List<OrderLine>,
): Serializable {
    init {
        require(lines.isNotEmpty()) { "lines must not be empty." }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal enum class OrderStatus {
    PLACED,
    APPROVED,
}

internal sealed interface OrderDomainEvent: DomainEvent<Long>, Serializable {
    val sequence: Int
    val orderNumber: OrderNumber
    val eventType: String

    fun payload(): String
}

internal data class OrderPlacedEvent(
    override val aggregateId: Long,
    override val sequence: Int,
    override val orderNumber: OrderNumber,
    val customerId: CustomerId,
    val lineCount: Int,
    val totalAmount: Money,
    override val occurredAt: Instant,
): OrderDomainEvent {
    override val eventType: String = "OrderPlaced"

    override fun payload(): String =
        "customer=${customerId.value};lines=$lineCount;total=${totalAmount.amount}"

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class OrderApprovedEvent(
    override val aggregateId: Long,
    override val sequence: Int,
    override val orderNumber: OrderNumber,
    val approvedBy: OperatorId,
    val totalAmount: Money,
    override val occurredAt: Instant,
): OrderDomainEvent {
    override val eventType: String = "OrderApproved"

    override fun payload(): String =
        "approvedBy=${approvedBy.value};total=${totalAmount.amount}"

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal class PurchaseOrder private constructor(
    override val id: Long,
    val orderNumber: OrderNumber,
    val customerId: CustomerId,
    lines: List<OrderLine>,
    status: OrderStatus,
    version: Long,
    lastEventSequence: Int,
): AbstractAggregateRoot<Long>() {
    private val mutableLines = lines.toList()
    private var mutableStatus = status
    private var mutableVersion = version
    private var lastCommittedEventSequence = lastEventSequence

    val lines: List<OrderLine> get() = mutableLines
    val pendingEvents: List<OrderDomainEvent>
        get() = domainEvents().filterIsInstance<OrderDomainEvent>()
    val status: OrderStatus get() = mutableStatus
    val version: Long get() = mutableVersion
    val total: Money get() = lines.fold(Money.dollars("0.00")) { acc, line -> acc + line.lineTotal }

    fun approve(approvedBy: OperatorId, occurredAtEpochMillis: Long = nowEpochMillis()) {
        check(mutableStatus == OrderStatus.PLACED) {
            "Only placed orders can be approved. currentStatus=$mutableStatus"
        }
        mutableStatus = OrderStatus.APPROVED
        mutableVersion += 1L
        record(
            OrderApprovedEvent(
                aggregateId = id,
                sequence = nextEventSequence(),
                orderNumber = orderNumber,
                approvedBy = approvedBy,
                totalAmount = total,
                occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
            )
        )
    }

    internal fun markEventsCommitted() {
        lastCommittedEventSequence += domainEvents().size
        clearDomainEvents()
    }

    private fun record(event: OrderDomainEvent) {
        recordDomainEvent(event)
    }

    private fun nextEventSequence(): Int =
        lastCommittedEventSequence + domainEvents().size + 1

    companion object {
        fun place(command: PlaceOrderCommand, occurredAtEpochMillis: Long = nowEpochMillis()): PurchaseOrder {
            val order = PurchaseOrder(
                id = Snowflakers.Global.nextId(),
                orderNumber = command.orderNumber,
                customerId = command.customerId,
                lines = command.lines,
                status = OrderStatus.PLACED,
                version = 0L,
                lastEventSequence = 0,
            )
            order.record(
                OrderPlacedEvent(
                    aggregateId = order.id,
                    sequence = 1,
                    orderNumber = command.orderNumber,
                    customerId = command.customerId,
                    lineCount = command.lines.size,
                    totalAmount = order.total,
                    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
                )
            )
            return order
        }

        fun rehydrate(
            id: Long,
            orderNumber: OrderNumber,
            customerId: CustomerId,
            lines: List<OrderLine>,
            status: OrderStatus,
            version: Long,
            lastEventSequence: Int,
        ): PurchaseOrder = PurchaseOrder(
            id = id,
            orderNumber = orderNumber,
            customerId = customerId,
            lines = lines,
            status = status,
            version = version,
            lastEventSequence = lastEventSequence,
        )
    }
}

internal data class PersistedOrder(
    val id: Long,
    val orderNumber: OrderNumber,
    val customerId: CustomerId,
    val status: OrderStatus,
    val version: Long,
    val lineCount: Int,
    val total: Money,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class CapturedOrderEvent(
    val sequence: Int,
    val eventType: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal fun interface RepositoryFailureHook {
    fun afterEventsPersisted(orderId: Long)
}

internal object WorkshopDddOrders: IdTable<Long>("ddd_orders") {
    override val id = long("id").snowflakeGenerated().entityId()
    val orderNumber = varchar("order_number", 64).uniqueIndex()
    val customerId = varchar("customer_id", 64)
    val status = varchar("status", 24)
    val version = long("version")
    val totalAmount = decimal("total_amount", 12, 2)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

internal object WorkshopDddOrderLines: Table("ddd_order_lines") {
    val id = long("id")
    val orderId = reference("order_id", WorkshopDddOrders)
    val sku = varchar("sku", 64)
    val quantity = integer("quantity")
    val unitPrice = decimal("unit_price", 12, 2)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

internal object WorkshopDddOrderEvents: Table("ddd_order_events") {
    val id = long("id")
    val orderId = reference("order_id", WorkshopDddOrders)
    val eventSequence = integer("event_sequence")
    val eventType = varchar("event_type", 64)
    val payload = varchar("payload", 512)
    val occurredAtEpochMillis = long("occurred_at_epoch_millis")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

/** DDD aggregate state, line, and domain event를 하나의 Exposed R2DBC transaction으로 저장합니다. */
internal class OrderRepository(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    suspend fun bootstrapSchema() {
        schemaMutex.withLock {
            suspendTransaction(db = database) {
                try {
                    SchemaUtils.drop(WorkshopDddOrderEvents, WorkshopDddOrderLines, WorkshopDddOrders)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (_: Throwable) {
                    // A fresh in-memory database has nothing to drop.
                }
                SchemaUtils.create(WorkshopDddOrders, WorkshopDddOrderLines, WorkshopDddOrderEvents)
            }
            schemaReady.set(true)
        }
    }

    suspend fun save(
        aggregate: PurchaseOrder,
        failureHook: RepositoryFailureHook = RepositoryFailureHook {},
    ): PersistedOrder {
        ensureSchema()
        val savedId = suspendTransaction(db = database) {
            val orderId = aggregate.id
            if (WorkshopDddOrders.selectAll().where { WorkshopDddOrders.id eq orderId }.singleOrNull() == null) {
                insertOrder(aggregate)
            }
            replaceOrderLines(orderId, aggregate.lines)
            updateOrder(orderId, aggregate)
            appendDomainEvents(orderId, aggregate.pendingEvents)
            failureHook.afterEventsPersisted(orderId)
            orderId
        }
        aggregate.markEventsCommitted()
        return findById(savedId) ?: error("Order $savedId was not found after save.")
    }

    suspend fun findAggregate(number: OrderNumber): PurchaseOrder? {
        ensureSchema()
        return suspendTransaction(db = database) {
            val orderRow = WorkshopDddOrders
                .selectAll()
                .where { WorkshopDddOrders.orderNumber eq number.value }
                .singleOrNull()
                ?: return@suspendTransaction null
            val orderId = orderRow[WorkshopDddOrders.id].value
            PurchaseOrder.rehydrate(
                id = orderId,
                orderNumber = OrderNumber(orderRow[WorkshopDddOrders.orderNumber]),
                customerId = CustomerId(orderRow[WorkshopDddOrders.customerId]),
                lines = linesFor(orderId),
                status = OrderStatus.valueOf(orderRow[WorkshopDddOrders.status]),
                version = orderRow[WorkshopDddOrders.version],
                lastEventSequence = lastEventSequence(orderId),
            )
        }
    }

    suspend fun findById(orderId: Long): PersistedOrder? {
        ensureSchema()
        return suspendTransaction(db = database) {
            WorkshopDddOrders
                .selectAll()
                .where { WorkshopDddOrders.id eq orderId }
                .singleOrNull()
                ?.toPersistedOrder(linesFor(orderId))
        }
    }

    suspend fun capturedEvents(orderId: Long): List<CapturedOrderEvent> {
        ensureSchema()
        return suspendTransaction(db = database) {
            WorkshopDddOrderEvents
                .selectAll()
                .where { WorkshopDddOrderEvents.orderId eq orderId }
                .orderBy(WorkshopDddOrderEvents.eventSequence to SortOrder.ASC)
                .toList()
                .map { row ->
                    CapturedOrderEvent(
                        sequence = row[WorkshopDddOrderEvents.eventSequence],
                        eventType = row[WorkshopDddOrderEvents.eventType],
                        payload = row[WorkshopDddOrderEvents.payload],
                    )
                }
        }
    }

    suspend fun orderCount(): Long {
        ensureSchema()
        return suspendTransaction(db = database) { WorkshopDddOrders.selectAll().count() }
    }

    suspend fun eventCount(): Long {
        ensureSchema()
        return suspendTransaction(db = database) { WorkshopDddOrderEvents.selectAll().count() }
    }

    private suspend fun ensureSchema() {
        if (!schemaReady.get()) bootstrapSchema()
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.insertOrder(aggregate: PurchaseOrder): EntityID<Long> {
        WorkshopDddOrders.insert {
            it[WorkshopDddOrders.id] = EntityID(aggregate.id, WorkshopDddOrders)
            it[orderNumber] = aggregate.orderNumber.value
            it[customerId] = aggregate.customerId.value
            it[status] = aggregate.status.name
            it[version] = aggregate.version
            it[totalAmount] = aggregate.total.amount
        }
        return EntityID(aggregate.id, WorkshopDddOrders)
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.updateOrder(
        orderId: Long,
        aggregate: PurchaseOrder,
    ) {
        WorkshopDddOrders.update({ WorkshopDddOrders.id eq orderId }) {
            it[customerId] = aggregate.customerId.value
            it[status] = aggregate.status.name
            it[version] = aggregate.version
            it[totalAmount] = aggregate.total.amount
        }
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.replaceOrderLines(
        orderId: Long,
        lines: List<OrderLine>,
    ) {
        WorkshopDddOrderLines.deleteWhere { WorkshopDddOrderLines.orderId eq orderId }
        lines.forEach { line ->
            WorkshopDddOrderLines.insert {
                it[WorkshopDddOrderLines.id] = Snowflakers.Global.nextId()
                it[WorkshopDddOrderLines.orderId] = orderId
                it[sku] = line.sku.value
                it[quantity] = line.quantity
                it[unitPrice] = line.unitPrice.amount
            }
        }
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.appendDomainEvents(
        orderId: Long,
        events: List<OrderDomainEvent>,
    ) {
        events.forEach { event ->
            WorkshopDddOrderEvents.insert {
                it[WorkshopDddOrderEvents.id] = Snowflakers.Global.nextId()
                it[WorkshopDddOrderEvents.orderId] = orderId
                it[eventSequence] = event.sequence
                it[eventType] = event.eventType
                it[payload] = event.payload()
                it[occurredAtEpochMillis] = event.occurredAt.toEpochMilli()
            }
        }
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.linesFor(orderId: Long): List<OrderLine> =
        WorkshopDddOrderLines
            .selectAll()
            .where { WorkshopDddOrderLines.orderId eq orderId }
            .orderBy(WorkshopDddOrderLines.id to SortOrder.ASC)
            .toList()
            .map { row ->
                OrderLine(
                    sku = Sku(row[WorkshopDddOrderLines.sku]),
                    quantity = row[WorkshopDddOrderLines.quantity],
                    unitPrice = Money(row[WorkshopDddOrderLines.unitPrice]),
                )
            }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.lastEventSequence(orderId: Long): Int =
        WorkshopDddOrderEvents
            .selectAll()
            .where { WorkshopDddOrderEvents.orderId eq orderId }
            .orderBy(WorkshopDddOrderEvents.eventSequence to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(WorkshopDddOrderEvents.eventSequence)
            ?: 0
}

private fun ResultRow.toPersistedOrder(lines: List<OrderLine>): PersistedOrder =
    PersistedOrder(
        id = this[WorkshopDddOrders.id].value,
        orderNumber = OrderNumber(this[WorkshopDddOrders.orderNumber]),
        customerId = CustomerId(this[WorkshopDddOrders.customerId]),
        status = OrderStatus.valueOf(this[WorkshopDddOrders.status]),
        version = this[WorkshopDddOrders.version],
        lineCount = lines.size,
        total = lines.fold(Money.dollars("0.00")) { acc, line -> acc + line.lineTotal },
    )

private fun BigDecimal.scaled(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

private fun nowEpochMillis(): Long = System.currentTimeMillis()
