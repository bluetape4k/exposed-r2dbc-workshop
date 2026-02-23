package exposed.r2dbc.examples.routing.context

/**
 * Reactor Context에 저장하는 라우팅 키 상수 모음입니다.
 */
object RoutingContextKeys {
    const val TENANT = "routing.tenant"
    const val READ_ONLY = "routing.readOnly"
}
