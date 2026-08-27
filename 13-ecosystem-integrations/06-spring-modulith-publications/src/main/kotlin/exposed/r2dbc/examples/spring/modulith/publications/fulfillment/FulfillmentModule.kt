package exposed.r2dbc.examples.spring.modulith.publications.fulfillment

import exposed.r2dbc.examples.spring.modulith.publications.orders.events.OrderApprovedEvent
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationLogPort
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** fulfillment module이 소유하는 reservation table입니다. */
internal object FulfillmentReservations: LongIdTable("modulith_fulfillment_reservations") {
    val orderKey = varchar("order_key", 80).uniqueIndex()
    val customerId = varchar("customer_id", 80)
    val reservedAt = timestamp("reserved_at")
}

data class FulfillmentReservation(
    val id: Long,
    val orderKey: String,
    val customerId: String,
    val reservedAt: Instant,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** publication log를 읽고 fulfillment handler에 전달하는 custom dispatcher입니다. */
@Service
class R2dbcPublicationDispatcher(
    private val publicationLog: PublicationLogPort,
    private val handler: FulfillmentReservationHandler,
) {
    suspend fun dispatchOutstanding(): DispatchSummary {
        val outstanding = publicationLog.findOutstanding()
        var completed = 0
        var failed = 0
        for (record in outstanding) {
            publicationLog.markAttempt(record.id)
            try {
                handler.reserve(record.event)
                publicationLog.markCompleted(record.id)
                completed++
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Throwable) {
                publicationLog.markFailed(record.id, ex.message ?: "fulfillment failed")
                failed++
            }
        }
        return DispatchSummary(attempted = outstanding.size, completed = completed, failed = failed)
    }
}

data class DispatchSummary(
    val attempted: Int,
    val completed: Int,
    val failed: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** 공개 주문 event를 fulfillment table에 기록하는 R2DBC handler입니다. */
@Service
class FulfillmentReservationHandler(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()
    private val failNext = AtomicBoolean(false)

    fun failNextReservation() {
        failNext.set(true)
    }

    suspend fun reserve(event: OrderApprovedEvent): FulfillmentReservation {
        ensureSchema()
        if (failNext.compareAndSet(true, false)) {
            error("Simulated downstream reservation failure for ${event.orderKey}")
        }
        return suspendTransaction(db = database) {
            FulfillmentReservations.insert {
                it[orderKey] = event.orderKey
                it[customerId] = event.customerId
                it[reservedAt] = event.approvedAt
            }
            FulfillmentReservations
                .selectAll()
                .where { FulfillmentReservations.orderKey eq event.orderKey }
                .single()
                .toReservation()
        }
    }

    suspend fun findReservation(orderKey: String): FulfillmentReservation? {
        ensureSchema()
        return suspendTransaction(db = database) {
            FulfillmentReservations
                .selectAll()
                .where { FulfillmentReservations.orderKey eq orderKey }
                .singleOrNull()
                ?.toReservation()
        }
    }

    suspend fun clear() {
        ensureSchema()
        suspendTransaction(db = database) {
            FulfillmentReservations.deleteAll()
        }
    }

    private suspend fun ensureSchema() {
        if (schemaReady.get()) return
        schemaMutex.withLock {
            if (schemaReady.get()) return
            suspendTransaction(db = database) {
                SchemaUtils.create(FulfillmentReservations)
            }
            schemaReady.set(true)
        }
    }
}

private fun ResultRow.toReservation(): FulfillmentReservation =
    FulfillmentReservation(
        id = this[FulfillmentReservations.id].value,
        orderKey = this[FulfillmentReservations.orderKey],
        customerId = this[FulfillmentReservations.customerId],
        reservedAt = this[FulfillmentReservations.reservedAt],
    )
