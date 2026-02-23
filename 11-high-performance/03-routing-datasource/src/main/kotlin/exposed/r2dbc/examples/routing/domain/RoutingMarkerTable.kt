package exposed.r2dbc.examples.routing.domain

import org.jetbrains.exposed.v1.core.Table

/**
 * 현재 라우팅된 노드를 식별하기 위한 마커 테이블입니다.
 */
object RoutingMarkerTable: Table("routing_marker") {
    val marker = varchar("marker", 100)
}
