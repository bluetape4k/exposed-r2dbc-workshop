package exposed.r2dbc.multitenant.resilientonboarding.tenant

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.Duration
import java.time.Instant
import java.util.UUID

class TenantLifecycleRepository(
    private val database: R2dbcDatabase,
    private val leaseDuration: Duration,
) {

    suspend fun initializeSchema() {
        suspendTransaction(db = database) {
            SchemaUtils.create(TenantLifecycleTable)
        }
    }

    internal suspend fun deleteAll() {
        suspendTransaction(db = database) {
            TenantLifecycleTable.deleteAll()
        }
    }

    suspend fun claim(
        tenantId: TenantId,
        displayName: String,
        now: Instant,
    ): TenantClaim =
        suspendTransaction(db = database) {
            val existing = findRow(tenantId)
            when {
                existing == null -> {
                    val metadata = newProvisioningMetadata(tenantId, displayName, 1, now)
                    insert(metadata)
                    TenantClaim.Owner(metadata)
                }

                existing.displayName != displayName -> TenantClaim.Conflict(existing)
                existing.status == TenantLifecycleStatus.ACTIVE -> TenantClaim.Active(existing)
                existing.status == TenantLifecycleStatus.PROVISIONING && existing.leaseExpiresAt > now -> TenantClaim.Pending(existing)
                else -> retry(existing, now)
            }
        }

    suspend fun markFailed(
        owner: TenantClaim.Owner,
        code: TenantFailureCode,
        now: Instant,
    ): Boolean =
        suspendTransaction(db = database) {
            TenantLifecycleTable.update({ ownedCondition(owner.metadata) }) {
                it[status] = TenantLifecycleStatus.FAILED.name
                it[lastFailureCode] = code.name
                it[leaseExpiresAt] = now
                it[version] = owner.metadata.version + 1
                it[updatedAt] = now
            } == 1
        }

    suspend fun markActive(
        owner: TenantClaim.Owner,
        now: Instant,
    ): TenantMetadata? =
        suspendTransaction(db = database) {
            if (TenantLifecycleTable.update({ ownedCondition(owner.metadata) }) {
                    it[status] = TenantLifecycleStatus.ACTIVE.name
                    it[lastFailureCode] = null
                    it[version] = owner.metadata.version + 1
                    it[updatedAt] = now
                } == 1
            ) {
                findRow(owner.metadata.tenantId)
            } else {
                null
            }
        }

    suspend fun renewLease(
        owner: TenantClaim.Owner,
        now: Instant,
    ): TenantClaim.Owner? =
        suspendTransaction(db = database) {
            if (TenantLifecycleTable.update({ ownedCondition(owner.metadata) }) {
                    it[leaseExpiresAt] = now.plus(leaseDuration)
                    it[version] = owner.metadata.version + 1
                    it[updatedAt] = now
                } == 1
            ) {
                findRow(owner.metadata.tenantId)?.let(TenantClaim::Owner)
            } else {
                null
            }
        }

    suspend fun find(tenantId: TenantId): TenantMetadata? =
        suspendTransaction(db = database) { findRow(tenantId) }

    suspend fun findExpiredProvisioning(now: Instant): List<TenantMetadata> =
        suspendTransaction(db = database) {
            TenantLifecycleTable.selectAll()
                .where {
                    (TenantLifecycleTable.status eq TenantLifecycleStatus.PROVISIONING.name) and
                        (TenantLifecycleTable.leaseExpiresAt lessEq now)
                }
                .map { it.toMetadata() }
                .toList()
        }

    suspend fun findActive(): List<TenantMetadata> =
        suspendTransaction(db = database) {
            TenantLifecycleTable.selectAll()
                .where { TenantLifecycleTable.status eq TenantLifecycleStatus.ACTIVE.name }
                .map { it.toMetadata() }
                .toList()
        }

    private suspend fun retry(existing: TenantMetadata, now: Instant): TenantClaim {
        val next = newProvisioningMetadata(
            tenantId = existing.tenantId,
            displayName = existing.displayName,
            attempt = existing.attempt + 1,
            now = now,
        )
        val updated = TenantLifecycleTable.update({
            (TenantLifecycleTable.tenantId eq existing.tenantId.value) and
                (TenantLifecycleTable.reservationToken eq existing.reservationToken.toString()) and
                (TenantLifecycleTable.version eq existing.version)
        }) {
            it[status] = TenantLifecycleStatus.PROVISIONING.name
            it[attempt] = next.attempt
            it[reservationToken] = next.reservationToken.toString()
            it[version] = existing.version + 1
            it[leaseExpiresAt] = next.leaseExpiresAt
            it[lastFailureCode] = null
            it[updatedAt] = now
        }
        return if (updated == 1) {
            TenantClaim.Owner(next.copy(version = existing.version + 1))
        } else {
            classifyCurrent(existing.tenantId, existing.displayName, now)
        }
    }

    private suspend fun classifyCurrent(
        tenantId: TenantId,
        displayName: String,
        now: Instant,
    ): TenantClaim {
        val metadata = requireNotNull(findRow(tenantId)) { "Tenant disappeared during lifecycle claim" }
        return when {
            metadata.displayName != displayName -> TenantClaim.Conflict(metadata)
            metadata.status == TenantLifecycleStatus.ACTIVE -> TenantClaim.Active(metadata)
            metadata.status == TenantLifecycleStatus.PROVISIONING && metadata.leaseExpiresAt > now -> TenantClaim.Pending(metadata)
            else -> TenantClaim.Pending(metadata)
        }
    }

    private fun ownedCondition(metadata: TenantMetadata): Op<Boolean> =
        (TenantLifecycleTable.tenantId eq metadata.tenantId.value) and
            (TenantLifecycleTable.reservationToken eq metadata.reservationToken.toString()) and
            (TenantLifecycleTable.version eq metadata.version)

    private suspend fun findRow(tenantId: TenantId): TenantMetadata? =
        TenantLifecycleTable.selectAll()
            .where { TenantLifecycleTable.tenantId eq tenantId.value }
            .firstOrNull()
            ?.toMetadata()

    private suspend fun insert(metadata: TenantMetadata) {
        TenantLifecycleTable.insert {
            it[tenantId] = metadata.tenantId.value
            it[displayName] = metadata.displayName
            it[status] = metadata.status.name
            it[attempt] = metadata.attempt
            it[reservationToken] = metadata.reservationToken.toString()
            it[version] = metadata.version
            it[leaseExpiresAt] = metadata.leaseExpiresAt
            it[lastFailureCode] = metadata.lastFailureCode?.name
            it[createdAt] = metadata.createdAt
            it[updatedAt] = metadata.updatedAt
        }
    }

    private fun newProvisioningMetadata(
        tenantId: TenantId,
        displayName: String,
        attempt: Int,
        now: Instant,
    ): TenantMetadata =
        TenantMetadata(
            tenantId = tenantId,
            displayName = displayName,
            status = TenantLifecycleStatus.PROVISIONING,
            attempt = attempt,
            reservationToken = UUID.randomUUID(),
            version = 0,
            leaseExpiresAt = now.plus(leaseDuration),
            lastFailureCode = null,
            createdAt = now,
            updatedAt = now,
        )

    private fun ResultRow.toMetadata(): TenantMetadata =
        TenantMetadata(
            tenantId = TenantId(this[TenantLifecycleTable.tenantId]),
            displayName = this[TenantLifecycleTable.displayName],
            status = TenantLifecycleStatus.valueOf(this[TenantLifecycleTable.status]),
            attempt = this[TenantLifecycleTable.attempt],
            reservationToken = UUID.fromString(this[TenantLifecycleTable.reservationToken]),
            version = this[TenantLifecycleTable.version],
            leaseExpiresAt = this[TenantLifecycleTable.leaseExpiresAt],
            lastFailureCode = this[TenantLifecycleTable.lastFailureCode]?.let(TenantFailureCode::valueOf),
            createdAt = this[TenantLifecycleTable.createdAt],
            updatedAt = this[TenantLifecycleTable.updatedAt],
        )
}

private object TenantLifecycleTable: Table("tenant_lifecycle") {
    val tenantId = varchar("tenant_id", 64).uniqueIndex()
    val displayName = varchar("display_name", 128)
    val status = varchar("status", 32)
    val attempt = integer("attempt")
    val reservationToken = varchar("reservation_token", 36)
    val version = long("version")
    val leaseExpiresAt = timestamp("lease_expires_at")
    val lastFailureCode = varchar("last_failure_code", 32).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
