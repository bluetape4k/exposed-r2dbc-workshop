package exposed.r2dbc.multitenant.ktor.tenant

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager

/**
 * Runs an Exposed R2DBC transaction after switching to the validated tenant schema.
 */
suspend fun <T> suspendTransactionWithTenant(
    tenant: Tenants.Tenant,
    db: R2dbcDatabase,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T,
): T {
    val isolationLevel = transactionIsolation ?: db.transactionManager.defaultIsolationLevel
    return suspendTransaction(db = db, transactionIsolation = isolationLevel, readOnly = readOnly) {
        SchemaUtils.setSchema(getSchemaDefinition(tenant))
        statement()
    }
}
