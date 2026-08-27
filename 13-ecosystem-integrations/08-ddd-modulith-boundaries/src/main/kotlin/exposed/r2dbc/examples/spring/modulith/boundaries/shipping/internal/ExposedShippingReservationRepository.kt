package exposed.r2dbc.examples.spring.modulith.boundaries.shipping.internal

import exposed.r2dbc.examples.spring.modulith.boundaries.orders.events.OrderAcceptedEvent
import exposed.r2dbc.examples.spring.modulith.boundaries.shipping.ShippingReservation
import io.bluetape4k.idgenerators.snowflake.Snowflakers
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
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

/** shipping module이 소유하는 R2DBC reservation table입니다. */
internal object ShippingReservations: Table("ddd_modulith_shipping_reservations") {
    val id = long("id")
    val orderKey = varchar("order_key", 80).uniqueIndex()
    val customerId = varchar("customer_id", 80)
    val reservedAt = long("reserved_at_epoch_millis")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

/** 공개 event만 입력으로 받고 orders 내부 table에는 접근하지 않는 repository입니다. */
@Repository
class ExposedShippingReservationRepository(
    private val database: R2dbcDatabase,
) {
    private val schemaReady = AtomicBoolean(false)
    private val schemaMutex = Mutex()

    suspend fun reserve(event: OrderAcceptedEvent): ShippingReservation {
        ensureSchema()
        return suspendTransaction(db = database) {
            val existing = ShippingReservations
                .selectAll()
                .where { ShippingReservations.orderKey eq event.orderKey }
                .singleOrNull()
            if (existing == null) {
                ShippingReservations.insert {
                    it[id] = Snowflakers.Global.nextId()
                    it[ShippingReservations.orderKey] = event.orderKey
                    it[customerId] = event.customerId
                    it[reservedAt] = Instant.now().toEpochMilli()
                }
            }
            ShippingReservations
                .selectAll()
                .where { ShippingReservations.orderKey eq event.orderKey }
                .single()
                .toReservation()
        }
    }

    suspend fun findByOrderKey(orderKey: String): ShippingReservation? {
        ensureSchema()
        return suspendTransaction(db = database) {
            ShippingReservations
                .selectAll()
                .where { ShippingReservations.orderKey eq orderKey }
                .singleOrNull()
                ?.toReservation()
        }
    }

    suspend fun clear() {
        ensureSchema()
        suspendTransaction(db = database) {
            ShippingReservations.deleteAll()
        }
    }

    private suspend fun ensureSchema() {
        if (schemaReady.get()) return
        schemaMutex.withLock {
            if (schemaReady.get()) return
            suspendTransaction(db = database) {
                SchemaUtils.create(ShippingReservations)
            }
            schemaReady.set(true)
        }
    }
}

private fun ResultRow.toReservation(): ShippingReservation =
    ShippingReservation(
        id = this[ShippingReservations.id],
        orderKey = this[ShippingReservations.orderKey],
        customerId = this[ShippingReservations.customerId],
        reservedAt = Instant.ofEpochMilli(this[ShippingReservations.reservedAt]),
    )
