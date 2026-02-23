package exposed.r2dbc.examples.suspendedcache.controller

import io.bluetape4k.logging.coroutines.KLoggingChannel

/**
 * 기본 저장소 기반 국가 조회 컨트롤러 테스트.
 */
class DefaultCountryControllerTest: AbstractCountryControllerTest() {

    companion object: KLoggingChannel()

    override val basePath: String = "default"

}
