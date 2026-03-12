package exposed.r2dbc.multitenant.webflux.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 요청 헤더에서 `X-TENANT-ID` 를 읽어서 ReactorContext 에 TenantId 를 설정합니다.
 * 이를 사용하여, CoroutineScope 에서 TenantId 를 사용할 수 있습니다.
 *
 * ## 테넌트 전파 흐름
 * ```
 * HTTP 요청 (X-TENANT-ID: korean)
 *     │
 *     ▼
 * TenantFilter.filter()
 *     │  chain.filter(exchange).contextWrite { it.put("TenantId", TenantId(tenant)) }
 *     ▼
 * ReactorContext ["TenantId" → TenantId(KOREAN)]
 *     │
 *     ▼
 * Controller suspend fun  ← currentReactorTenant() 로 테넌트 읽기
 *     │
 *     ▼
 * suspendTransactionWithCurrentTenant { SchemaUtils.setSchema("korean") }
 * ```
 *
 * ## 기본값 처리
 * - `X-TENANT-ID` 헤더가 없거나 빈 문자열이면 [Tenants.DEFAULT_TENANT] (KOREAN)을 사용합니다.
 * - 알 수 없는 테넌트 ID는 `400 Bad Request`로 응답합니다.
 *
 * ```kotlin
 * val tenantId = currentReactorTenant()
 * ```
 *
 * @see [currentReactorTenant]
 * @see [TenantId]
 * @see [Tenants]
 */
@Component
class TenantFilter: WebFilter {

    companion object: KLoggingChannel() {
        const val TENANT_HEADER = "X-TENANT-ID"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> = mono {
        val tenantId = exchange.request.headers.getFirst(TENANT_HEADER)
        log.debug { "Request tenantId: $tenantId" }
        val resolvedTenantId = tenantId?.takeIf { it.isNotBlank() } ?: Tenants.DEFAULT_TENANT.id
        val tenant = Tenants.findById(resolvedTenantId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tenant id: $resolvedTenantId")

        chain
            .filter(exchange)
            .contextWrite {
                it.put(TenantId.TENANT_ID_KEY, TenantId(tenant))
            }
            .awaitSingleOrNull()     // awaitSingle() 을 사용하면, 전송 후에도 뭔가 처리하느라 예외가 발생함.
    }
}
