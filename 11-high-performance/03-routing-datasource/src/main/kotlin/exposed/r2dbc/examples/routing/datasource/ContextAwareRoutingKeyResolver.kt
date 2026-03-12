package exposed.r2dbc.examples.routing.datasource

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import reactor.util.context.ContextView

/**
 * Reactor Context에서 tenant와 readOnly 정보를 읽어 `<tenant>:<rw|ro>` 형태의 라우팅 키를 계산합니다.
 *
 * [TenantRoutingWebFilter]가 Reactor Context에 저장한 [RoutingContextKeys.TENANT]와
 * [RoutingContextKeys.READ_ONLY] 값을 조합하여 [DynamicRoutingConnectionFactory]가
 * 사용할 라우팅 키를 반환합니다.
 *
 * ## 키 계산 규칙
 * | TENANT         | READ_ONLY | 반환 키        |
 * |----------------|-----------|----------------|
 * | `"default"` (기본) | `false`   | `"default:rw"` |
 * | `"acme"`       | `false`   | `"acme:rw"`    |
 * | `"acme"`       | `true`    | `"acme:ro"`    |
 * | 빈 문자열       | `false`   | `"default:rw"` |
 *
 * @param defaultTenant TENANT 컨텍스트 키가 없거나 빈 값일 때 사용할 기본 테넌트 이름
 * @see RoutingKeyResolver
 * @see DynamicRoutingConnectionFactory
 * @see RoutingContextKeys
 */
class ContextAwareRoutingKeyResolver(
    private val defaultTenant: String = "default",
): RoutingKeyResolver {

    /**
     * Reactor [ContextView]에서 테넌트 ID와 읽기 전용 여부를 읽어 라우팅 키를 반환합니다.
     *
     * @param context 현재 요청의 Reactor Context
     * @return `<tenant>:rw` 또는 `<tenant>:ro` 형태의 라우팅 키
     */
    override fun currentLookupKey(context: ContextView): String {
        val tenant = context.getOrDefault(RoutingContextKeys.TENANT, defaultTenant)
            .toString()
            .ifBlank { defaultTenant }

        val readOnly = context.getOrDefault(RoutingContextKeys.READ_ONLY, false)
            .toString()
            .toBooleanStrictOrNull()
            ?: false

        val mode = if (readOnly) "ro" else "rw"
        return "$tenant:$mode"
    }
}
