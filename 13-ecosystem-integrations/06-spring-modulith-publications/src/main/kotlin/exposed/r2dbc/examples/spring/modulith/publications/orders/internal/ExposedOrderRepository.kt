package exposed.r2dbc.examples.spring.modulith.publications.orders.internal

import exposed.r2dbc.examples.spring.modulith.publications.orders.ApproveOrderCommand
import exposed.r2dbc.examples.spring.modulith.publications.orders.OrderSummary
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.OrderApprovedEvent
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationLogPort
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationRecord
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationStatus
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** orders table과 custom publication log를 한 transaction으로 기록하는 R2DBC repository입니다. */
@Repository
class ExposedOrderRepository(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    suspend fun approve(command: ApproveOrderCommand): OrderSummary {
        ensureSchema()
        val orderKey = command.orderKey.trim().requireNotBlank("orderKey")
        val customerId = command.customerId.trim().requireNotBlank("customerId")
        val approvedAt = Instant.now()
        return suspendTransaction(db = database) {
            val id = WorkshopOrders.insertAndGetId {
                it[WorkshopOrders.orderKey] = orderKey
                it[WorkshopOrders.customerId] = customerId
                it[WorkshopOrders.status] = APPROVED
                it[WorkshopOrders.approvedAt] = approvedAt
            }
            WorkshopPublicationLog.insert {
                it[listenerId] = FULFILLMENT_LISTENER_ID
                it[eventType] = OrderApprovedEvent::class.qualifiedName.orEmpty()
                it[WorkshopPublicationLog.orderKey] = orderKey
                it[WorkshopPublicationLog.customerId] = customerId
                it[WorkshopPublicationLog.approvedAt] = approvedAt
                it[status] = PENDING
                it[attempts] = 0
            }
            WorkshopOrders
                .selectAll()
                .where { WorkshopOrders.id eq id }
                .single()
                .toOrderSummary()
        }
    }

    suspend fun clear() {
        ensureSchema()
        suspendTransaction(db = database) {
            WorkshopPublicationLog.deleteAll()
            WorkshopOrders.deleteAll()
        }
    }

    private suspend fun ensureSchema() {
        if (schemaReady.get()) return
        schemaMutex.withLock {
            if (schemaReady.get()) return
            suspendTransaction(db = database) {
                SchemaUtils.create(WorkshopOrders, WorkshopPublicationLog)
            }
            schemaReady.set(true)
        }
    }

    private fun String.requireNotBlank(field: String): String {
        require(isNotBlank()) { "$field must not be blank" }
        return this
    }

    private companion object {
        const val APPROVED = "APPROVED"
        const val PENDING = "PENDING"
        const val FULFILLMENT_LISTENER_ID = "fulfillment.reserve-stock"
    }
}

/** orders module의 publication log를 fulfillment module에 공개하는 adapter입니다. */
@Repository
class R2dbcPublicationLog(
    private val database: R2dbcDatabase,
) : PublicationLogPort {

    override suspend fun findOutstanding(): List<PublicationRecord> {
        return suspendTransaction(db = database) {
            WorkshopPublicationLog
                .selectAll()
                .where { WorkshopPublicationLog.status neq COMPLETED }
                .orderBy(WorkshopPublicationLog.id)
                .toList()
                .map { it.toPublicationRecord() }
        }
    }

    override suspend fun markAttempt(id: Long) {
        suspendTransaction(db = database) {
            val current = WorkshopPublicationLog
                .selectAll()
                .where { WorkshopPublicationLog.id eq id }
                .single()
            WorkshopPublicationLog.update({ WorkshopPublicationLog.id eq id }) {
                it[attempts] = current[WorkshopPublicationLog.attempts] + 1
            }
        }
    }

    override suspend fun markCompleted(id: Long) {
        suspendTransaction(db = database) {
            WorkshopPublicationLog.update({ WorkshopPublicationLog.id eq id }) {
                it[status] = COMPLETED
                it[lastError] = null
            }
        }
    }

    override suspend fun markFailed(id: Long, message: String) {
        suspendTransaction(db = database) {
            WorkshopPublicationLog.update({ WorkshopPublicationLog.id eq id }) {
                it[status] = FAILED
                it[lastError] = message.take(MAX_ERROR_LENGTH)
            }
        }
    }

    suspend fun findByStatus(status: String): List<PublicationRecord> =
        suspendTransaction(db = database) {
            WorkshopPublicationLog
                .selectAll()
                .where { WorkshopPublicationLog.status eq status }
                .orderBy(WorkshopPublicationLog.id)
                .toList()
                .map { it.toPublicationRecord() }
        }

    private companion object {
        const val COMPLETED = "COMPLETED"
        const val FAILED = "FAILED"
        const val MAX_ERROR_LENGTH = 240
    }
}

private fun ResultRow.toOrderSummary(): OrderSummary =
    OrderSummary(
        id = this[WorkshopOrders.id].value,
        orderKey = this[WorkshopOrders.orderKey],
        customerId = this[WorkshopOrders.customerId],
        status = this[WorkshopOrders.status],
    )

private fun ResultRow.toPublicationRecord(): PublicationRecord =
    PublicationRecord(
        id = this[WorkshopPublicationLog.id].value,
        listenerId = this[WorkshopPublicationLog.listenerId],
        event = OrderApprovedEvent(
            orderKey = this[WorkshopPublicationLog.orderKey],
            customerId = this[WorkshopPublicationLog.customerId],
            approvedAt = this[WorkshopPublicationLog.approvedAt],
        ),
        status = PublicationStatus.valueOf(this[WorkshopPublicationLog.status]),
        attempts = this[WorkshopPublicationLog.attempts],
        lastError = this[WorkshopPublicationLog.lastError],
    )
