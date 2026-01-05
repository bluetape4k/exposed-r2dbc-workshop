package exposed.r2dbc.multitenant.webflux.tenant

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.reactor.ReactorContext
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * 코루틴의 [CoroutineContext] 에 [TenantId] 를 설정합니다.
 */
data class TenantId(val value: Tenants.Tenant): CoroutineContext.Element {
    companion object Key: CoroutineContext.Key<TenantId> {
        val DEFAULT = TenantId(Tenants.DEFAULT_TENANT)

        const val TENANT_ID_KEY = "TenantId"
    }

    override val key: CoroutineContext.Key<*> = Key

    override fun toString(): String {
        return "TenantId(value='$value')"
    }
}

/**
 * [ReactorContext] 에서 `TenantId` 의 정보를 읽어옵니다. 없으면 [TenantId.DEFAULT] 를 사용합니다.
 *
 * Webflux 에서 사용되는 코루틴의 [CoroutineContext] 에서 [TenantId] 를 읽어옵니다.
 */
suspend fun currentReactorTenant(): Tenants.Tenant =
    coroutineContext[ReactorContext]?.context?.getOrDefault(TenantId.TENANT_ID_KEY, TenantId.DEFAULT)?.value
        ?: Tenants.DEFAULT_TENANT


/**
 * 현재 코루틴의 [TenantId] 를 읽어옵니다. 없으면 [Tenants.DEFAULT_TENANT] 를 사용합니다.
 */
suspend fun currentTenant(): Tenants.Tenant =
    coroutineContext[TenantId]?.value ?: Tenants.DEFAULT_TENANT

/**
 * [newSuspendedTransaction] 함수를 수행할 때, [tenant] 를 전달하도록 합니다.
 */
suspend fun <T> suspendTransactionWithTenant(
    tenant: Tenants.Tenant? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T,
): T {
    // val context = Dispatchers.IO + TenantId(currentTenant)
    val isolationLevel = transactionIsolation ?: db?.transactionManager?.defaultIsolationLevel

    return suspendTransaction(db = db, transactionIsolation = isolationLevel, readOnly = readOnly) {
        val currentTenant = tenant ?: currentTenant()
        SchemaUtils.setSchema(getSchemaDefinition(currentTenant))
        statement()
    }
}

/**
 * [newSuspendedTransaction] 함수를 호출할 때, ReactorContext 에 있는 [TenantId]에 해당하는 Schema 를 사용하도록 합니다.
 */
suspend fun <T> suspendTransactionWithCurrentTenant(
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T,
): T = suspendTransactionWithTenant(
    tenant = currentReactorTenant(),
    db = db,
    transactionIsolation = transactionIsolation,
    readOnly = readOnly,
    statement = statement
)
