package exposed.r2dbc.examples.routing.datasource

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import reactor.util.context.ContextView

/**
 * tenant + readOnly 정보를 조합하여 `<tenant>:<rw|ro>` 라우팅 키를 계산합니다.
 */
class ContextAwareRoutingKeyResolver(
    private val defaultTenant: String = "default",
): RoutingKeyResolver {

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
