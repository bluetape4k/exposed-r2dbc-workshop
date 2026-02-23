package exposed.r2dbc.workshop.springwebflux.controller

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 애플리케이션 기본 메타 정보를 제공하는 엔드포인트입니다.
 */
@RestController
@RequestMapping("/")
class IndexController {

    companion object: KLoggingChannel()

    @Autowired
    private val buildProperties: BuildProperties = uninitialized()


    /**
     * 빌드 시점의 애플리케이션 정보를 반환합니다.
     */
    @GetMapping
    suspend fun index(): BuildProperties {
        return buildProperties
    }
}
