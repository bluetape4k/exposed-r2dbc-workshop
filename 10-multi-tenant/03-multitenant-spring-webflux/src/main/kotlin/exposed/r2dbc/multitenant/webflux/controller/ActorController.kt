package exposed.r2dbc.multitenant.webflux.controller

import exposed.r2dbc.multitenant.webflux.domain.model.ActorRecord
import exposed.r2dbc.multitenant.webflux.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.webflux.tenant.suspendTransactionWithCurrentTenant
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 멀티테넌트 환경에서 배우 정보를 조회하는 API입니다.
 */
@RestController
@RequestMapping("/actors")
class ActorController(
    private val actorRepository: ActorR2dbcRepository,
) {

    companion object: KLoggingChannel()

    /**
     * 현재 테넌트의 배우 목록을 조회합니다.
     */
    @GetMapping
    suspend fun getAllActors(): List<ActorRecord> =
        suspendTransactionWithCurrentTenant {
            actorRepository.findAll().toList()
        }

    /**
     * 현재 테넌트에서 ID로 배우를 조회합니다.
     */
    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ActorRecord? =
        suspendTransactionWithCurrentTenant {
            actorRepository.findByIdOrNull(id)
        }
}
