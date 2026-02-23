package exposed.r2dbc.examples.routing.config

import exposed.r2dbc.examples.routing.domain.RoutingMarkerRepository
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 애플리케이션 시작 시 tenant/rw-ro 노드별 마커 데이터를 초기화합니다.
 */
@Component
class RoutingMarkerInitializer(
    private val routingNodeDatabases: Map<String, R2dbcDatabase>,
    private val markerRepository: RoutingMarkerRepository,
): ApplicationRunner {

    override fun run(args: ApplicationArguments) = runBlocking {
        routingNodeDatabases.forEach { (key, db) ->
            markerRepository.resetAndInsert(
                marker = key.replace(':', '-'),
                db = db,
            )
        }
    }
}
