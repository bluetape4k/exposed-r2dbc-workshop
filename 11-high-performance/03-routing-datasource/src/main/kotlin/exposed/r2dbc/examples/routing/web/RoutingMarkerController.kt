package exposed.r2dbc.examples.routing.web

import exposed.r2dbc.examples.routing.config.RoutingTransactionalExecutor
import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import exposed.r2dbc.examples.routing.domain.RoutingMarkerRepository
import kotlinx.coroutines.reactor.ReactorContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.coroutines.coroutineContext

/**
 * 현재 요청이 어떤 라우팅 키로 해석되는지 확인하는 API입니다.
 */
@RestController
@RequestMapping("/routing")
class RoutingMarkerController(
    private val markerRepository: RoutingMarkerRepository,
    private val txExecutor: RoutingTransactionalExecutor,
) {

    /**
     * read-write 경로에서 마커를 조회합니다.
     */
    @GetMapping("/marker")
    suspend fun getReadWriteMarker(): RoutingMarkerResponse =
        txExecutor.readWrite {
            RoutingMarkerResponse(
                tenant = currentTenant(),
                readOnly = false,
                marker = markerRepository.findCurrentMarker(),
            )
        }

    /**
     * read-only 경로에서 마커를 조회합니다.
     */
    @GetMapping("/marker/readonly")
    suspend fun getReadOnlyMarker(): RoutingMarkerResponse =
        txExecutor.readOnly {
            RoutingMarkerResponse(
                tenant = currentTenant(),
                readOnly = true,
                marker = markerRepository.findCurrentMarker(),
            )
        }

    /**
     * read-write 경로의 마커 값을 갱신합니다.
     */
    @PatchMapping("/marker")
    suspend fun updateMarker(@RequestBody request: UpdateMarkerRequest): RoutingMarkerResponse {
        return txExecutor.readWrite {
            markerRepository.resetAndInsert(request.marker)
            RoutingMarkerResponse(
                tenant = currentTenant(),
                readOnly = false,
                marker = markerRepository.findCurrentMarker(),
            )
        }
    }

    private suspend fun currentTenant(): String =
        coroutineContext[ReactorContext]
            ?.context
            ?.getOrDefault(RoutingContextKeys.TENANT, "default")
            ?.toString()
            ?: "default"
}

/**
 * 라우팅 조회 응답 모델입니다.
 */
data class RoutingMarkerResponse(
    val tenant: String,
    val readOnly: Boolean,
    val marker: String?,
)

/**
 * 마커 갱신 요청 모델입니다.
 */
data class UpdateMarkerRequest(
    val marker: String,
)
