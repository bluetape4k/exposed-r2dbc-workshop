package exposed.r2dbc.examples.cache.controller

import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 캐시 전략 예제 애플리케이션의 메타 정보를 제공합니다.
 */
@RestController
@RequestMapping
class IndexController(private val buildProps: BuildProperties) {

    companion object: KLoggingChannel()

    /**
     * 빌드 정보를 반환합니다.
     */
    @GetMapping("/")
    suspend fun index(): BuildProperties {
        return buildProps
    }
}
