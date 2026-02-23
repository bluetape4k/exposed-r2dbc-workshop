package exposed.r2dbc.examples.routing.domain

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

/**
 * Exposed R2DBC를 사용해 라우팅 대상 노드의 마커를 조회/갱신합니다.
 */
@Repository
class RoutingMarkerRepository(
    private val routingDatabase: R2dbcDatabase,
) {

    /**
     * 현재 라우팅된 노드의 마커 값을 조회합니다.
     */
    suspend fun findCurrentMarker(): String? = suspendTransaction(db = routingDatabase) {
        RoutingMarkerTable
            .selectAll()
            .singleOrNull()
            ?.getOrNull(RoutingMarkerTable.marker)
    }

    /**
     * 마커 테이블을 초기화하고 단일 마커 값을 저장합니다.
     */
    suspend fun resetAndInsert(marker: String, db: R2dbcDatabase = routingDatabase) {
        suspendTransaction(db = db) {
            SchemaUtils.create(RoutingMarkerTable)
            RoutingMarkerTable.deleteAll()
            RoutingMarkerTable.insert {
                it[RoutingMarkerTable.marker] = marker
            }
        }
    }
}
