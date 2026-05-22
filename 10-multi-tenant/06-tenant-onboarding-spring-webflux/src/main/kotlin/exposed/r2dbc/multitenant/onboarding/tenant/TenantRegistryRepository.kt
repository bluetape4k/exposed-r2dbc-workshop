package exposed.r2dbc.multitenant.onboarding.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Registry metadata repository for dynamically onboarded tenants.
 */
@Repository
class TenantRegistryRepository(
    @param:Qualifier("registryDatabase")
    private val registryDatabase: R2dbcDatabase,
    private val registry: TenantConnectionFactoryRegistry,
) {

    companion object: KLoggingChannel()

    suspend fun initializeSchema() {
        suspendTransaction(db = registryDatabase) {
            SchemaUtils.create(TenantRegistryTable)
        }
    }

    suspend fun recoverStaleRows() {
        suspendTransaction(db = registryDatabase) {
            // H2 in-memory tenant pools disappear on restart, so stale rows must be re-onboarded.
            TenantRegistryTable.update({
                TenantRegistryTable.status eq TenantStatus.PROVISIONING.name
            }) {
                it[status] = TenantStatus.FAILED.name
            }
            TenantRegistryTable.update({
                TenantRegistryTable.status eq TenantStatus.ACTIVE.name
            }) {
                it[status] = TenantStatus.FAILED.name
            }
        }
    }

    suspend fun reserve(
        tenantId: TenantId,
        displayName: String,
        databaseName: String,
        r2dbcUrl: String,
    ): TenantMetadata =
        registry.mutexFor(tenantId).withLock {
            try {
                suspendTransaction(db = registryDatabase) {
                    val now = Instant.now()
                    val updated = TenantRegistryTable.update({
                        (TenantRegistryTable.tenantId eq tenantId.value) and
                            (TenantRegistryTable.status eq TenantStatus.FAILED.name)
                    }) {
                        it[TenantRegistryTable.displayName] = displayName
                        it[TenantRegistryTable.databaseName] = databaseName
                        it[TenantRegistryTable.r2dbcUrl] = r2dbcUrl
                        it[status] = TenantStatus.PROVISIONING.name
                        it[createdAt] = now
                    }

                    if (updated == 1) {
                        return@suspendTransaction TenantMetadata(
                            tenantId = tenantId,
                            displayName = displayName,
                            databaseName = databaseName,
                            r2dbcUrl = r2dbcUrl,
                            status = TenantStatus.PROVISIONING,
                            createdAt = now,
                        )
                    }

                    val existing = TenantRegistryTable
                        .selectAll()
                        .where { TenantRegistryTable.tenantId eq tenantId.value }
                        .firstOrNull()
                    if (existing != null) {
                        throw DuplicateTenantException(tenantId)
                    }

                    try {
                        TenantRegistryTable.insert {
                            it[TenantRegistryTable.tenantId] = tenantId.value
                            it[TenantRegistryTable.displayName] = displayName
                            it[TenantRegistryTable.databaseName] = databaseName
                            it[TenantRegistryTable.r2dbcUrl] = r2dbcUrl
                            it[status] = TenantStatus.PROVISIONING.name
                            it[createdAt] = now
                        }
                    } catch (e: Exception) {
                        val duplicate = TenantRegistryTable
                            .selectAll()
                            .where { TenantRegistryTable.tenantId eq tenantId.value }
                            .firstOrNull()
                        if (duplicate != null) {
                            throw DuplicateTenantException(tenantId)
                        }
                        throw e
                    }

                    TenantMetadata(
                        tenantId = tenantId,
                        displayName = displayName,
                        databaseName = databaseName,
                        r2dbcUrl = r2dbcUrl,
                        status = TenantStatus.PROVISIONING,
                        createdAt = now,
                    )
                }
            } catch (e: DuplicateTenantException) {
                throw e
            } catch (e: Exception) {
                throw TenantProvisioningException("Failed to reserve tenant '${tenantId.value}'", e)
            }
        }

    suspend fun markActive(tenantId: TenantId): TenantMetadata =
        updateStatus(tenantId, TenantStatus.ACTIVE)

    suspend fun delete(tenantId: TenantId) {
        suspendTransaction(db = registryDatabase) {
            TenantRegistryTable.deleteWhere { TenantRegistryTable.tenantId eq tenantId.value }
        }
    }

    suspend fun countReservedTenants(): Long =
        suspendTransaction(db = registryDatabase) {
            TenantRegistryTable
                .selectAll()
                .where {
                    (TenantRegistryTable.status eq TenantStatus.ACTIVE.name) or
                        (TenantRegistryTable.status eq TenantStatus.PROVISIONING.name)
                }
                .count()
        }

    suspend fun find(tenantId: TenantId): TenantMetadata? =
        suspendTransaction(db = registryDatabase) {
            TenantRegistryTable
                .selectAll()
                .where { TenantRegistryTable.tenantId eq tenantId.value }
                .firstOrNull()
                ?.toMetadata()
        }

    private suspend fun updateStatus(
        tenantId: TenantId,
        nextStatus: TenantStatus,
    ): TenantMetadata =
        suspendTransaction(db = registryDatabase) {
            TenantRegistryTable.update({ TenantRegistryTable.tenantId eq tenantId.value }) {
                it[status] = nextStatus.name
            }
            TenantRegistryTable
                .selectAll()
                .where { TenantRegistryTable.tenantId eq tenantId.value }
                .first()
                .toMetadata()
        }

    private fun ResultRow.toMetadata(): TenantMetadata =
        TenantMetadata(
            tenantId = TenantId(this[TenantRegistryTable.tenantId]),
            displayName = this[TenantRegistryTable.displayName],
            databaseName = this[TenantRegistryTable.databaseName],
            r2dbcUrl = this[TenantRegistryTable.r2dbcUrl],
            status = TenantStatus.valueOf(this[TenantRegistryTable.status]),
            createdAt = this[TenantRegistryTable.createdAt],
        )
}

object TenantRegistryTable: Table("tenant_registry") {
    val tenantId = varchar("tenant_id", 64).uniqueIndex()
    val displayName = varchar("display_name", 128)
    val databaseName = varchar("database_name", 128)
    val r2dbcUrl = varchar("r2dbc_url", 512)
    val status = varchar("status", 32)
    val createdAt = timestamp("created_at")
}
