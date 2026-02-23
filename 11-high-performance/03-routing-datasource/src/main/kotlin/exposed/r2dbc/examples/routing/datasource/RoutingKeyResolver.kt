package exposed.r2dbc.examples.routing.datasource

import reactor.util.context.ContextView

/**
 * 현재 요청 컨텍스트로부터 라우팅 키를 계산합니다.
 */
fun interface RoutingKeyResolver {

    /**
     * [context]를 기반으로 라우팅 키를 반환합니다.
     */
    fun currentLookupKey(context: ContextView): String
}
