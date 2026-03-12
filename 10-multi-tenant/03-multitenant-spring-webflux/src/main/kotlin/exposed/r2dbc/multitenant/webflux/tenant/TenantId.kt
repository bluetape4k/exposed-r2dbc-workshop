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
 * 코루틴의 [CoroutineContext] 에 테넌트 정보를 전달하는 컨텍스트 엘리먼트입니다.
 *
 * Spring WebFlux 환경에서는 [TenantFilter]가 HTTP 요청 헤더를 읽어
 * `ReactorContext`에 이 객체를 저장하면, 코루틴 안에서 [currentReactorTenant]로 꺼낼 수 있습니다.
 *
 * ## 테넌트 격리 수준
 * - **Schema-based**: 테넌트마다 별도 DB 스키마를 생성하여 데이터를 완전히 격리합니다.
 * - 단일 DB 인스턴스 공유: 연결 풀, DB 서버 자원은 공유하지만, 스키마 네임스페이스로 테이블을 분리합니다.
 * - `SchemaUtils.setSchema(tenant.id)`가 각 트랜잭션 시작 시 호출되어 스키마를 전환합니다.
 *
 * @param value 현재 요청에 해당하는 테넌트 ([Tenants.Tenant])
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
 * 지정한 [tenant]의 DB 스키마로 전환한 후 트랜잭션을 실행합니다.
 *
 * 트랜잭션 시작 시 `SchemaUtils.setSchema(tenant.id)`를 호출하여 해당 테넌트 스키마로 전환합니다.
 * 이후 [statement] 블록에서 실행되는 모든 쿼리는 해당 스키마 안에서 동작합니다.
 *
 * ## Schema-based 멀티테넌시 동작 원리
 * ```
 * suspendTransactionWithTenant(tenant = KOREAN) {
 *     // 내부적으로: SET SEARCH_PATH = 'korean'  (PostgreSQL)
 *     //            또는: SET SCHEMA 'korean'      (H2, MySQL)
 *     actorRepository.findAll()  // korean 스키마의 actors 테이블 조회
 * }
 * ```
 *
 * @param tenant 사용할 테넌트. `null`이면 [currentTenant]에서 읽습니다.
 * @param db 사용할 R2DBC 데이터베이스. `null`이면 기본 데이터베이스를 사용합니다.
 * @param transactionIsolation 트랜잭션 격리 수준
 * @param readOnly 읽기 전용 트랜잭션 여부
 * @param statement 트랜잭션 내에서 실행할 suspend 블록
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
 * `ReactorContext`에 저장된 현재 테넌트의 DB 스키마로 전환한 후 트랜잭션을 실행합니다.
 *
 * Spring WebFlux 환경에서 [TenantFilter]가 `ReactorContext`에 저장한 [TenantId]를
 * [currentReactorTenant]로 읽어 [suspendTransactionWithTenant]에 전달합니다.
 *
 * ## 사용 예시 (Controller)
 * ```kotlin
 * @GetMapping
 * suspend fun getActors(): List<ActorRecord> =
 *     suspendTransactionWithCurrentTenant {
 *         // HTTP 헤더의 X-TENANT-ID에 해당하는 스키마에서 데이터 조회
 *         actorRepository.findAll().toList()
 *     }
 * ```
 *
 * @see suspendTransactionWithTenant
 * @see currentReactorTenant
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
